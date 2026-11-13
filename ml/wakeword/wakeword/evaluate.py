"""Streaming eval gate. The WakeGate rules here MUST equal the Android WakeGate (Task 12).

Calibration / reporting: the threshold is chosen on the VAL negative stream (lowest value with
<= 0.1 FA/hour there); that fixed threshold is then applied to the TEST positives and the TEST
negative stream. passed = test detection >= 0.95 AND test FA/hour <= 0.1 AND the test negative
stream is >= gate.min_negative_hours (default 10 h) long. Owner recordings (--eval) are added to
the test evaluation; the long negative stream always comes from the configured roots.

Gate semantics (hop = 80 ms = 1280 samples, window = 1 s):
  * a trigger fires on a hop whose prob >= threshold when the previous hop also met the threshold
    (2 consecutive hops); a hop below the threshold resets the consecutive counter to 0;
  * after a trigger there is a 2000 ms refractory period (25 hops): those hops are ignored and the
    counter stays 0, so a new trigger needs 2 fresh consecutive hops after the refractory ends.

    python -m wakeword.evaluate --ckpt best.pt [--eval data/eval ...]
"""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from typing import Iterable, Iterator

import numpy as np
import torch

from wakeword.data.manifest import ManifestError, validate_rows
from wakeword.data.mix import MixConfig, make_example
from wakeword.model import SR, WINDOW, WakeNet

HOP_SAMPLES = 1280
HOP_MS = 80
CONSECUTIVE = 2
REFRACTORY_MS = 2000
REFRACTORY_HOPS = REFRACTORY_MS // HOP_MS
MIN_NEGATIVE_HOURS = 10.0
FA_BUDGET_PER_HOUR = 0.1
MIN_DETECTION = 0.95
HOPS_PER_HOUR = 3600 * 1000 / HOP_MS


class WakeGate:
    """Pure streaming trigger logic: step(prob) -> True when a trigger fires on this hop."""

    def __init__(self, threshold: float, consecutive: int = CONSECUTIVE, refractory_hops: int = REFRACTORY_HOPS):
        self.threshold, self.consecutive, self.refractory_hops = threshold, consecutive, refractory_hops
        self._run = 0
        self._cool = 0

    def step(self, prob: float) -> bool:
        if self._cool > 0:
            self._cool -= 1
            self._run = 0
            return False
        if prob >= self.threshold:
            self._run += 1
            if self._run >= self.consecutive:
                self._run = 0
                self._cool = self.refractory_hops
                return True
        else:
            self._run = 0
        return False


def stream_triggers(probs, threshold: float) -> list[int]:
    g = WakeGate(threshold)
    return [i for i, p in enumerate(probs) if g.step(float(p))]


def count_triggers(probs: np.ndarray, threshold: float) -> int:
    """Same trigger count as WakeGate, but O(N) numpy plus one step per trigger.

    A trigger needs a pair of adjacent above-threshold hops (p-1, p) lying entirely after the
    previous refractory period; the next pair is found with a binary search."""
    above = np.asarray(probs) >= threshold
    pairs = np.flatnonzero(above[1:] & above[:-1]) + 1  # hop index of the 2nd hop of each pair
    n, pos = 0, 0
    while True:
        k = int(np.searchsorted(pairs, pos + CONSECUTIVE - 1))
        if k >= len(pairs):
            return n
        n += 1
        pos = int(pairs[k]) + 1 + REFRACTORY_HOPS


def fa_per_hour(probs, threshold: float) -> float:
    return count_triggers(np.asarray(probs), threshold) * HOPS_PER_HOUR / max(len(probs), 1)


def lowest_threshold(neg_probs: np.ndarray, budget: float = FA_BUDGET_PER_HOUR) -> float:
    """Lowest threshold with fa_per_hour <= budget. FA is non-increasing in the threshold, so
    binary-search the sorted unique probabilities (plus one value above the maximum)."""
    cands = np.append(np.unique(neg_probs), np.nextafter(np.float32(1.0), np.float32(2.0)))
    lo, hi = 0, len(cands) - 1  # cands[hi] is never reached, so it always satisfies the budget
    while lo < hi:
        mid = (lo + hi) // 2
        if fa_per_hour(neg_probs, float(cands[mid])) <= budget:
            hi = mid
        else:
            lo = mid + 1
    return float(cands[lo])



@dataclass(frozen=True)
class GateResult:
    detection_rate: float
    fa_per_hour: float
    threshold: float
    passed: bool
    negative_hours: float = 0.0
    reason: str = ""


@torch.no_grad()
def _probs_of_buffer(model: WakeNet, x: torch.Tensor, batch: int = 256) -> np.ndarray:
    n = (len(x) - WINDOW) // HOP_SAMPLES + 1
    out = []
    for s in range(0, n, batch):
        idx = torch.arange(s, min(s + batch, n))
        out.append(model(x[idx[:, None] * HOP_SAMPLES + torch.arange(WINDOW)[None, :]]).cpu().numpy())
    return np.concatenate(out)


def stream_probs(model: WakeNet, audio: np.ndarray, batch: int = 256) -> np.ndarray:
    """Model prob for each full 1 s window at an 80 ms hop (one value per hop)."""
    model.eval()
    x = torch.from_numpy(np.ascontiguousarray(audio, dtype=np.float32))
    if len(x) < WINDOW:
        x = torch.nn.functional.pad(x, (0, WINDOW - len(x)))
    return _probs_of_buffer(model, x, batch)


def stream_probs_chunks(model: WakeNet, chunks: Iterable[np.ndarray]) -> tuple[np.ndarray, int]:
    """Probs over the concatenation of `chunks` without holding it all in memory; identical to
    stream_probs on the concatenated audio. Returns (probs, total_samples)."""
    model.eval()
    buf = np.zeros(0, np.float32)
    out, total = [], 0
    for c in chunks:
        c = np.asarray(c, dtype=np.float32)
        total += len(c)
        buf = np.concatenate([buf, c])
        if len(buf) >= WINDOW:
            n = (len(buf) - WINDOW) // HOP_SAMPLES + 1
            out.append(_probs_of_buffer(model, torch.from_numpy(buf)))
            buf = buf[n * HOP_SAMPLES:]
    return (np.concatenate(out) if out else np.zeros(0, np.float32)), total


def _rows(roots, split):
    from wakeword.train import load_rows
    rows = load_rows(list(roots))
    return [r for r in rows if split is None or r.split == split]


def _neg_chunks(rows) -> Iterator[np.ndarray]:
    from wakeword.train import load_audio
    for r in rows:
        if r.label in ("negative", "near_miss", "background"):
            yield load_audio(r.path)


def _positive_streams(rows, bg_rows, mix_positives: bool, rng, mix: MixConfig):
    from wakeword.train import load_audio, load_audio_crop
    bgs = [load_audio_crop(r.path, 30.0, rng) for r in bg_rows[:20]] or [np.zeros(2 * WINDOW, np.float32)]

    def bg_piece(n):
        b = bgs[int(rng.integers(len(bgs)))]
        if len(b) < n:
            b = np.tile(b, int(np.ceil(n / max(len(b), 1))))
        s0 = int(rng.integers(0, len(b) - n + 1))
        return b[s0:s0 + n]

    streams = []
    for r in rows:
        clip = load_audio(r.path)
        if mix_positives:
            win = make_example(clip, bg_piece(WINDOW), mix.sample_snr(rng), rng, mix)
        else:  # real owner recordings are used as recorded, placed at the end of a window
            win = np.zeros(max(WINDOW, len(clip)), np.float32)
            win[len(win) - len(clip):] = clip
        streams.append(np.concatenate([bg_piece(WINDOW), win, bg_piece(WINDOW)]).astype(np.float32))
    return streams


def gate(model: WakeNet, roots, owner_dirs=None, *, mix_positives: bool = True, seed: int = 0,
         mix: MixConfig | None = None, min_negative_hours: float = MIN_NEGATIVE_HOURS) -> GateResult:
    """Calibrate on val, report on test (see module docstring).

    roots: data roots (manifest.csv each). owner_dirs: optional owner-recording dirs whose rows are
    ALL added to the test evaluation (positives as recorded; any negatives join the negative stream)."""
    from wakeword.train import load_rows
    roots = [roots] if isinstance(roots, str) else list(roots)
    owner_dirs = [owner_dirs] if isinstance(owner_dirs, str) else list(owner_dirs or [])
    mix = mix or MixConfig()
    rng = np.random.default_rng(seed)
    all_rows = load_rows(roots)
    owner_rows = load_rows(owner_dirs) if owner_dirs else []
    problems = validate_rows(all_rows + owner_rows)
    if problems:
        raise ManifestError("invalid eval data:\n" + "\n".join(problems[:20]))
    val_rows = [r for r in all_rows if r.split == "val"]
    test_rows = [r for r in all_rows if r.split == "test"]

    val_probs, _ = stream_probs_chunks(model, _neg_chunks(val_rows))
    if len(val_probs) == 0:
        raise ValueError("no val negative / background audio to calibrate the threshold on")
    threshold = lowest_threshold(val_probs)

    test_pos = [r for r in test_rows if r.label == "positive"]
    test_bg = [r for r in test_rows if r.label == "background"]
    pos_streams = _positive_streams(test_pos, test_bg, mix_positives, rng, mix)
    own_pos = [r for r in owner_rows if r.label == "positive"]
    pos_streams += _positive_streams(own_pos, test_bg, False, rng, mix)
    if not pos_streams:
        raise ValueError("no positive eval rows")
    neg_rows = test_rows + [r for r in owner_rows if r.label != "positive"]
    neg_probs, n_samples = stream_probs_chunks(model, _neg_chunks(neg_rows))
    if len(neg_probs) == 0:
        raise ValueError("no test negative / background audio")
    hours = n_samples / SR / 3600.0

    det = float(np.mean([count_triggers(stream_probs(model, s), threshold) > 0 for s in pos_streams]))
    fa = fa_per_hour(neg_probs, threshold)
    fails = []
    if det < MIN_DETECTION:
        fails.append(f"detection {det:.3f} < {MIN_DETECTION}")
    if fa > FA_BUDGET_PER_HOUR:
        fails.append(f"test FA/hour {fa:.3f} > {FA_BUDGET_PER_HOUR}")
    if hours < min_negative_hours:
        fails.append(f"negative stream {hours:.2f} h < required {min_negative_hours} h "
                     f"(add more test-split negative audio)")
    return GateResult(det, float(fa), min(threshold, 1.0), not fails, float(hours), "; ".join(fails))


def load_model(ckpt: str) -> WakeNet:
    ck = torch.load(ckpt, map_location="cpu", weights_only=False)
    m = WakeNet()
    m.load_state_dict(ck["state_dict"])
    return m.eval()


def run_gate_cli(ckpt: str, config: str, eval_dirs) -> GateResult:
    import yaml
    with open(config) as f:
        cfg = yaml.safe_load(f)
    res = gate(load_model(ckpt), cfg["train"]["roots"], eval_dirs, mix=MixConfig.from_dict(cfg.get("mix")),
               min_negative_hours=float(cfg.get("gate", {}).get("min_negative_hours", MIN_NEGATIVE_HOURS)))
    print(f"detection_rate={res.detection_rate:.4f} fa_per_hour={res.fa_per_hour:.4f} "
          f"threshold={res.threshold:.4f} negative_hours={res.negative_hours:.2f} "
          f"passed: {str(res.passed).lower()}")
    if res.reason:
        print("reason:", res.reason)
    return res


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--config", default="configs/base.yaml")
    ap.add_argument("--eval", action="append", default=None,
                    help="owner-recording dir with manifest.csv (repeatable); its rows are ADDED to the "
                         "test evaluation (positives as recorded)")
    args = ap.parse_args(argv)
    sys.exit(0 if run_gate_cli(args.ckpt, args.config, args.eval).passed else 1)


if __name__ == "__main__":
    main()
