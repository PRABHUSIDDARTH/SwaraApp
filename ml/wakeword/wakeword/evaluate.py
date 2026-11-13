"""Streaming eval gate. The WakeGate rules here MUST equal the Android WakeGate (Task 12).

Gate semantics (hop = 80 ms = 1280 samples, window = 1 s):
  * a trigger fires on a hop whose prob >= threshold when the previous hop also met the threshold
    (2 consecutive hops); a hop below the threshold resets the consecutive counter to 0;
  * after a trigger there is a 2000 ms refractory period (25 hops): those hops are ignored and the
    counter stays 0, so a new trigger needs 2 fresh consecutive hops after the refractory ends.

    python -m wakeword.evaluate --ckpt best.pt [--eval data/eval ...]
"""
from __future__ import annotations

import argparse
import os
import sys
from dataclasses import dataclass

import numpy as np
import torch

from wakeword.data.mix import MixConfig, make_example
from wakeword.model import WINDOW, WakeNet

HOP_SAMPLES = 1280
HOP_MS = 80
CONSECUTIVE = 2
REFRACTORY_MS = 2000
REFRACTORY_HOPS = REFRACTORY_MS // HOP_MS
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


def fa_per_hour(probs, threshold: float) -> float:
    return len(stream_triggers(probs, threshold)) * HOPS_PER_HOUR / max(len(probs), 1)


@dataclass(frozen=True)
class GateResult:
    detection_rate: float
    fa_per_hour: float
    threshold: float
    passed: bool


@torch.no_grad()
def stream_probs(model: WakeNet, audio: np.ndarray, batch: int = 256) -> np.ndarray:
    """Model prob for each full 1 s window at an 80 ms hop (one value per hop)."""
    model.eval()
    x = torch.from_numpy(np.ascontiguousarray(audio, dtype=np.float32))
    if len(x) < WINDOW:
        x = torch.nn.functional.pad(x, (0, WINDOW - len(x)))
    n = (len(x) - WINDOW) // HOP_SAMPLES + 1
    out = []
    for s in range(0, n, batch):
        idx = torch.arange(s, min(s + batch, n))
        win = x[idx[:, None] * HOP_SAMPLES + torch.arange(WINDOW)[None, :]]
        out.append(model(win).cpu().numpy())
    return np.concatenate(out)


def _gather(roots, split):
    from wakeword.train import load_rows
    rows = load_rows(roots)
    return [r for r in rows if split is None or r.split == split]


def build_eval(roots, split, mix_positives: bool, seed: int, mix: MixConfig | None = None):
    """(positive_streams, negative_stream) from the manifests under `roots` (rows filtered by split)."""
    rng = np.random.default_rng(seed)
    mix = mix or MixConfig()
    rows = _gather(roots, split)
    from wakeword.train import load_audio
    pos = [r for r in rows if r.label == "positive"]
    bgs = [load_audio(r.path) for r in rows if r.label == "background"]
    negs = [load_audio(r.path) for r in rows if r.label in ("negative", "near_miss")]
    if not pos:
        raise ValueError("no positive eval rows")
    quiet = [np.zeros(2 * WINDOW, np.float32)]
    bg_pool = bgs or quiet

    def bg_piece(n):
        b = bg_pool[int(rng.integers(len(bg_pool)))]
        if len(b) < n:
            b = np.tile(b, int(np.ceil(n / max(len(b), 1))))
        s = int(rng.integers(0, len(b) - n + 1))
        return b[s:s + n]

    streams = []
    for r in pos:
        clip = load_audio(r.path)
        if mix_positives:
            win = make_example(clip, bg_piece(WINDOW), mix.sample_snr(rng), rng, mix)
        else:  # real owner recordings are used as recorded, placed at the end of a window
            win = np.zeros(max(WINDOW, len(clip)), np.float32)
            win[len(win) - len(clip):] = clip
        streams.append(np.concatenate([bg_piece(WINDOW), win, bg_piece(WINDOW)]).astype(np.float32))
    # held-out long negatives: speech/music/noise concatenated into one stream
    neg_parts = negs + bgs
    if not neg_parts:
        raise ValueError("no negative / background eval rows")
    neg_stream = np.concatenate(neg_parts).astype(np.float32)
    return streams, neg_stream


def gate(model: WakeNet, eval_dirs, split: str | None = "test", *, mix_positives: bool = True,
         seed: int = 0, mix: MixConfig | None = None) -> GateResult:
    """Run the gate. Threshold = lowest value with <= 0.1 FA/hour on the negative stream;
    detection = fraction of positive streams that trigger at that threshold; pass if >= 0.95."""
    if isinstance(eval_dirs, str):
        eval_dirs = [eval_dirs]
    pos_streams, neg_stream = build_eval(list(eval_dirs), split, mix_positives, seed, mix)
    neg_probs = stream_probs(model, neg_stream)
    pos_probs = [stream_probs(model, s) for s in pos_streams]
    cands = np.unique(np.concatenate([neg_probs, np.linspace(0, 1, 201), np.concatenate(pos_probs)]))
    cands = np.append(cands, np.nextafter(np.float32(1.0), np.float32(2.0)))  # a threshold nothing reaches
    threshold, fa = float(cands[-1]), 0.0
    for t in cands:
        f = fa_per_hour(neg_probs, float(t))
        if f <= FA_BUDGET_PER_HOUR:
            threshold, fa = float(t), f
            break
    det = float(np.mean([len(stream_triggers(p, threshold)) > 0 for p in pos_probs]))
    return GateResult(det, float(fa), min(threshold, 1.0), det >= MIN_DETECTION)


def load_model(ckpt: str) -> WakeNet:
    ck = torch.load(ckpt, map_location="cpu", weights_only=False)
    m = WakeNet()
    m.load_state_dict(ck["state_dict"])
    return m.eval()


def run_gate_cli(ckpt: str, config: str, eval_dirs) -> GateResult:
    import yaml
    with open(config) as f:
        cfg = yaml.safe_load(f)
    model = load_model(ckpt)
    mix = MixConfig.from_dict(cfg.get("mix"))
    if eval_dirs:  # external (owner-recorded) eval: every row, used as recorded
        res = gate(model, eval_dirs, split=None, mix_positives=False, mix=mix)
    else:
        res = gate(model, cfg["train"]["roots"], split="test", mix=mix)
    print(f"detection_rate={res.detection_rate:.4f} fa_per_hour={res.fa_per_hour:.4f} "
          f"threshold={res.threshold:.4f} passed: {str(res.passed).lower()}")
    return res


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--config", default="configs/base.yaml")
    ap.add_argument("--eval", action="append", default=None,
                    help="external eval dir with manifest.csv (repeatable); default: test split of the data roots")
    args = ap.parse_args(argv)
    sys.exit(0 if run_gate_cli(args.ckpt, args.config, args.eval).passed else 1)


if __name__ == "__main__":
    main()
