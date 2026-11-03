"""Korokae v2: stereo-aware vocal-removal mask network (Standard and High configs).

Same STFT framing as v1 (see model.py). Differences:
  input    log1p|mid| and log1p|side| (side = (L - R) / 2), each normalised  [2 * BINS]
  output   one sigmoid mask per channel (left, right)                        [2, BINS]
  High     adds `lookahead` frames of future context via a right-looking conv
Standard has lookahead 0 and is strictly causal.
"""
from dataclasses import dataclass

import torch
import torch.nn as nn
import torch.nn.functional as F

from model import BINS, HOP, N_FFT, SR, istft, stft  # noqa: F401  (re-exported for callers)


@dataclass(frozen=True)
class Config:
    name: str
    hidden: int
    layers: int
    lookahead: int


STANDARD = Config("standard", 256, 2, 0)
HIGH = Config("high", 512, 3, 2)


class KorokaeNet2(nn.Module):
    def __init__(self, cfg: Config):
        super().__init__()
        self.cfg = cfg
        self.register_buffer("mean", torch.zeros(2, BINS))
        self.register_buffer("std", torch.ones(2, BINS))
        self.inp = nn.Linear(2 * BINS, cfg.hidden)
        self.look = (nn.Conv1d(cfg.hidden, cfg.hidden, kernel_size=cfg.lookahead + 1)
                     if cfg.lookahead > 0 else None)
        self.gru = nn.GRU(cfg.hidden, cfg.hidden, num_layers=cfg.layers, batch_first=True)
        self.out = nn.Linear(cfg.hidden, 2 * BINS)

    def features(self, mid_mag: torch.Tensor, side_mag: torch.Tensor) -> torch.Tensor:
        """[B, BINS, F] x2 -> [B, F, 2 * BINS]."""
        f = torch.log1p(torch.stack([mid_mag, side_mag], dim=1))  # [B, 2, BINS, F]
        f = f.permute(0, 3, 1, 2)  # [B, F, 2, BINS]
        f = (f - self.mean) / self.std
        return f.reshape(f.shape[0], f.shape[1], 2 * BINS)

    def forward(self, mid_mag: torch.Tensor, side_mag: torch.Tensor) -> torch.Tensor:
        h = torch.relu(self.inp(self.features(mid_mag, side_mag)))  # [B, F, H]
        if self.look is not None:
            h = h.transpose(1, 2)
            h = torch.relu(self.look(F.pad(h, (0, self.cfg.lookahead))))
            h = h.transpose(1, 2)
        h, _ = self.gru(h)
        m = torch.sigmoid(self.out(h))  # [B, F, 2 * BINS]
        return m.reshape(m.shape[0], m.shape[1], 2, BINS).permute(0, 2, 3, 1)  # [B, 2, BINS, F]


def make_mask_fn(net):
    def fn(x):  # x [2, n] -> mask [2, BINS, F]
        mid = stft(x.mean(0, keepdim=True)).abs()
        side = stft(0.5 * (x[0:1] - x[1:2])).abs()
        return net(mid, side)[0]
    return fn
