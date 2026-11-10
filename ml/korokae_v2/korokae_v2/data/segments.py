"""Random raw stem segments for training (mixing and augmentation happen later, on the GPU)."""
from __future__ import annotations

import os

import numpy as np
import soundfile as sf
import torch

from .manifest import Row, read_manifest


class StemPool:
    """Files of one role and split; draws duration-weighted random segments as float32 [2, L]."""

    def __init__(self, rows: list[Row], root: str, role: str, split: str):
        self.paths = [os.path.join(root, r.path) for r in rows if r.role == role and r.split == split]
        if not self.paths:
            raise ValueError(f"no {role} files in split {split!r}")
        self.frames = np.array([sf.info(p).frames for p in self.paths], dtype=np.int64)
        self.weights = self.frames / self.frames.sum()

    def _draw(self, rng: np.random.Generator, length: int) -> np.ndarray:
        i = rng.choice(len(self.paths), p=self.weights)
        n = int(self.frames[i])
        start = int(rng.integers(0, n - length + 1)) if n > length else 0
        with sf.SoundFile(self.paths[i]) as f:
            f.seek(start)
            x = f.read(min(length, n - start), dtype="float32", always_2d=True)  # [n, C]
        out = np.zeros((2, length), dtype=np.float32)
        x = x.T
        out[:, :x.shape[1]] = x[:2] if x.shape[0] >= 2 else np.repeat(x, 2, axis=0)
        return out

    def sample(self, rng: np.random.Generator, length: int, min_rms_db: float = -50.0,
               tries: int = 10) -> np.ndarray:
        """Retry up to `tries` draws until the segment's RMS exceeds min_rms_db; else the last."""
        x = None
        for _ in range(tries):
            x = self._draw(rng, length)
            if 10 * np.log10(np.mean(x.astype(np.float64) ** 2) + 1e-12) > min_rms_db:
                break
        return x


class PairDataset(torch.utils.data.IterableDataset):
    """Endless (vocal [2, L], accompaniment [2, L]) raw pairs from one split."""

    def __init__(self, manifest: str, root: str, split: str, length: int = 264600, seed: int = 0):
        super().__init__()
        rows = read_manifest(manifest)
        self.vocals = StemPool(rows, root, "vocal", split)
        self.accomp = StemPool(rows, root, "accompaniment", split)
        self.length, self.seed = length, seed

    def __iter__(self):
        info = torch.utils.data.get_worker_info()
        rng = np.random.default_rng(self.seed + (info.id if info else 0))
        while True:
            yield (torch.from_numpy(self.vocals.sample(rng, self.length)),
                   torch.from_numpy(self.accomp.sample(rng, self.length)))
