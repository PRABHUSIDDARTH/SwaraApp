"""Training loop for WakeNet.

    python -m wakeword.train --config configs/base.yaml --out checkpoints

Batches are one third positives; each example is a clip mixed over a background at an SNR drawn
per example with MixConfig.sample_snr. After every epoch the top-scoring negative windows from the
background stream are added to a hard-negative pool used by the following epochs. Training uses
split "train"; early stopping / best checkpoint use the "val" split.
"""
from __future__ import annotations

import argparse
import dataclasses
import math
import os
from collections import OrderedDict

import numpy as np
import soundfile as sf
import torch
import yaml
from scipy.signal import resample_poly

from wakeword.data.manifest import MANIFEST_NAME, ManifestError, Row, read_manifest, validate_rows
from wakeword.data.mix import SR, WINDOW, MixConfig, make_example
from wakeword.model import WakeNet


def _to_16k(x: np.ndarray, sr: int) -> np.ndarray:
    x = x.mean(axis=1)
    if sr != SR:
        g = math.gcd(sr, SR)
        x = resample_poly(x, SR // g, sr // g)
    return np.ascontiguousarray(x, dtype=np.float32)


def load_audio(path: str) -> np.ndarray:
    x, sr = sf.read(path, dtype="float32", always_2d=True)
    return _to_16k(x, sr)


def audio_seconds(path: str) -> float:
    info = sf.info(path)
    return info.frames / info.samplerate


def load_audio_crop(path: str, seconds: float, rng: np.random.Generator) -> np.ndarray:
    """Whole file when it is at most `seconds` long, else a random `seconds`-long crop (only the
    crop is read from disk)."""
    info = sf.info(path)
    n = int(seconds * info.samplerate)
    if info.frames <= n:
        return load_audio(path)
    start = int(rng.integers(0, info.frames - n + 1))
    x, sr = sf.read(path, start=start, frames=n, dtype="float32", always_2d=True)
    return _to_16k(x, sr)


def load_rows(roots: list[str]) -> list[Row]:
    """Merge manifest.csv of every existing root (paths made absolute) and validate the union,
    so a speaker split across roots, duplicate paths and bad licences are all caught."""
    rows: list[Row] = []
    for root in roots:
        mp = os.path.join(root, MANIFEST_NAME)
        if not os.path.isfile(mp):
            continue
        for r in read_manifest(mp):
            p = r.path if os.path.isabs(r.path) else os.path.abspath(os.path.join(root, r.path))
            rows.append(dataclasses.replace(r, path=p))
    problems = validate_rows(rows)
    if problems:
        raise ManifestError("invalid training data:\n" + "\n".join(problems[:20]))
    return rows


class Batcher:
    """Draws mixed training windows from the rows of one split."""

    def __init__(self, rows: list[Row], split: str, cfg: dict, seed: int = 0):
        self.rng = np.random.default_rng(seed)
        self.mix = MixConfig.from_dict(cfg.get("mix"))
        tc = cfg.get("train", {})
        self.hard_frac = float(tc.get("hard_neg_frac", 0.25))
        self.hard_max = int(tc.get("hard_neg_max", 2048))
        self.cache_cap = int(tc.get("cache_bytes", 2_000_000_000))
        self.crop_seconds = float(tc.get("long_clip_seconds", 10.0))
        self.cache_bytes = 0
        rows = [r for r in rows if r.split == split]
        self._cache: OrderedDict[str, np.ndarray] = OrderedDict()  # LRU, bounded by cache_cap bytes
        self.pos = [r.path for r in rows if r.label == "positive"]
        self.neg = [r.path for r in rows if r.label in ("negative", "near_miss")]
        self.bg = [r.path for r in rows if r.label == "background"]
        if not self.pos:
            raise ValueError(f"no positive rows in split {split!r}")
        if not (self.neg or self.bg):
            raise ValueError(f"no negative/background rows in split {split!r}")
        self.hard: list[np.ndarray] = []

    def _audio(self, path: str) -> np.ndarray:
        """Short files come from a byte-bounded LRU cache; files longer than crop_seconds are never
        cached whole: a fresh random crop is read from disk each time."""
        a = self._cache.get(path)
        if a is not None:
            self._cache.move_to_end(path)
            return a
        a = load_audio_crop(path, self.crop_seconds, self.rng)
        if audio_seconds(path) <= self.crop_seconds and a.nbytes <= self.cache_cap:
            self._cache[path] = a
            self.cache_bytes += a.nbytes
            while self.cache_bytes > self.cache_cap:
                _, old = self._cache.popitem(last=False)
                self.cache_bytes -= old.nbytes
        return a

    def _pick(self, paths: list[str]) -> np.ndarray:
        return self._audio(paths[int(self.rng.integers(len(paths)))])

    def _background(self) -> np.ndarray:
        pool = self.bg or self.neg
        return self._pick(pool)

    def positive_window(self) -> np.ndarray:
        return make_example(self._pick(self.pos), self._background(), self.mix.sample_snr(self.rng),
                            self.rng, self.mix)

    def negative_window(self) -> np.ndarray:
        """A background crop alone, or a negative / near-miss clip mixed over one."""
        if self.neg and (not self.bg or self.rng.random() < 0.7):
            clip = self._pick(self.neg)
            if len(clip) > WINDOW:  # random WINDOW-long crop, not always the tail
                s0 = int(self.rng.integers(0, len(clip) - WINDOW + 1))
                clip = clip[s0:s0 + WINDOW]
            return make_example(clip, self._background(), self.mix.sample_snr(self.rng),
                                self.rng, self.mix)
        bg = self._background()
        if len(bg) < WINDOW:
            bg = np.tile(bg, int(np.ceil(WINDOW / max(len(bg), 1))))
        s = int(self.rng.integers(0, len(bg) - WINDOW + 1))
        return np.clip(bg[s:s + WINDOW], -1, 1).astype(np.float32)

    def add_hard(self, windows: list[np.ndarray]) -> None:
        self.hard = (self.hard + list(windows))[-self.hard_max:]

    def batch(self, n: int) -> tuple[torch.Tensor, torch.Tensor]:
        n_pos = max(1, round(n / 3))
        xs, ys = [], []
        for _ in range(n_pos):
            xs.append(self.positive_window())
            ys.append(1.0)
        for _ in range(n - n_pos):
            if self.hard and self.rng.random() < self.hard_frac:
                xs.append(self.hard[int(self.rng.integers(len(self.hard)))])
            else:
                xs.append(self.negative_window())
            ys.append(0.0)
        order = self.rng.permutation(n)
        x = torch.from_numpy(np.stack(xs)[order].astype(np.float32))
        y = torch.tensor(ys, dtype=torch.float32)[order]
        return x, y


@torch.no_grad()
def mine_hard_negatives(model: WakeNet, batcher: Batcher, n_keep: int, n_candidates: int,
                        chunk: int = 128) -> list[np.ndarray]:
    """Score `n_candidates` fresh negative windows; return the n_keep highest-scoring, best first."""
    model.eval()
    wins = [batcher.negative_window() for _ in range(n_candidates)]
    scores = []
    for i in range(0, len(wins), chunk):
        scores.append(model(torch.from_numpy(np.stack(wins[i:i + chunk]))).cpu().numpy())
    scores = np.concatenate(scores)
    top = np.argsort(-scores, kind="stable")[:n_keep]
    return [wins[i] for i in top]


@torch.no_grad()
def _val_loss(model, x, y, device) -> float:
    model.eval()
    return float(torch.nn.functional.binary_cross_entropy_with_logits(model.logits(x.to(device)), y.to(device)))


def train(cfg: dict, out_dir: str, device: str = "cpu", seed: int | None = None, log=print) -> str:
    """Train; returns the path of the best (lowest val loss) checkpoint in out_dir."""
    tc = cfg["train"]
    seed = tc.get("seed", 0) if seed is None else seed
    torch.manual_seed(seed)
    rows = load_rows(tc["roots"])
    train_b = Batcher(rows, "train", cfg, seed)
    val_b = Batcher(rows, "val", cfg, seed + 1)
    vx, vy = val_b.batch(int(tc["val_examples"]))
    model = WakeNet().to(device)
    opt = torch.optim.Adam(model.parameters(), lr=float(tc["lr"]))
    os.makedirs(out_dir, exist_ok=True)
    best_path = os.path.join(out_dir, "best.pt")
    best, bad = float("inf"), 0
    for epoch in range(int(tc["epochs"])):
        model.train()
        total = 0.0
        for _ in range(int(tc["steps_per_epoch"])):
            x, y = train_b.batch(int(tc["batch_size"]))
            loss = torch.nn.functional.binary_cross_entropy_with_logits(model.logits(x.to(device)), y.to(device))
            opt.zero_grad()
            loss.backward()
            opt.step()
            total += float(loss.detach())
        train_b.add_hard(mine_hard_negatives(model, train_b,
                                             int(tc["hard_neg_per_epoch"]), int(tc["hard_neg_candidates"])))
        vl = _val_loss(model, vx, vy, device)
        log(f"epoch {epoch}: train_loss={total / int(tc['steps_per_epoch']):.4f} val_loss={vl:.4f} "
            f"hard_pool={len(train_b.hard)}")
        if vl < best:
            best, bad = vl, 0
            torch.save({"state_dict": {k: v.cpu() for k, v in model.state_dict().items()},
                        "val_loss": vl, "epoch": epoch}, best_path)
        else:
            bad += 1
            if bad >= int(tc["patience"]):
                log("early stop")
                break
    return best_path


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", default="configs/base.yaml")
    ap.add_argument("--out", default="checkpoints")
    ap.add_argument("--device", default="cpu")
    args = ap.parse_args(argv)
    with open(args.config) as f:
        cfg = yaml.safe_load(f)
    print("best checkpoint:", train(cfg, args.out, args.device))


if __name__ == "__main__":
    main()
