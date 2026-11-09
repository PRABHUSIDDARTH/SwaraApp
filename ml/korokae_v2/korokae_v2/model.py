"""KorokaeNet v2: band-split RNN predicting a complex stereo vocal mask.

Input  spec [B, 4, BINS, T] = (L.re, L.im, R.re, R.im) of the mixture STFT.
Output mask [B, 4, BINS, T] = complex vocal mask per channel, same channel order.
The instrumental is X - M*X (see dsp.apply_mask).

Layout: band split (39 bands, dsp.band_edges) -> n_blocks x [BiLSTM across time per band,
BiLSTM across bands per frame], each residual -> per-band MLP mask head with GLU.
Mask-head expansion factor is 4 (4*d hidden units); the default config (8 blocks, hidden 256,
d 128) has ~21M parameters.
"""
from __future__ import annotations

import torch
from torch import nn

from .dsp import band_edges

MASK_EXPANSION = 4


class _ResidualBiLSTM(nn.Module):
    def __init__(self, d: int, hidden: int):
        super().__init__()
        self.norm = nn.LayerNorm(d)
        self.rnn = nn.LSTM(d, hidden, batch_first=True, bidirectional=True)
        self.proj = nn.Linear(2 * hidden, d)

    def forward(self, x: torch.Tensor) -> torch.Tensor:  # [N, S, d]
        y, _ = self.rnn(self.norm(x))
        return x + self.proj(y)


class _Block(nn.Module):
    def __init__(self, d: int, hidden: int):
        super().__init__()
        self.time = _ResidualBiLSTM(d, hidden)
        self.band = _ResidualBiLSTM(d, hidden)

    def forward(self, z: torch.Tensor) -> torch.Tensor:  # [B, K, T, d]
        b, k, t, d = z.shape
        z = self.time(z.reshape(b * k, t, d)).reshape(b, k, t, d)
        z = z.transpose(1, 2).reshape(b * t, k, d)
        z = self.band(z).reshape(b, t, k, d).transpose(1, 2)
        return z


class KorokaeNetV2(nn.Module):
    def __init__(self, n_blocks: int = 8, hidden: int = 256, d: int = 128):
        super().__init__()
        self.edges = band_edges()
        widths = [hi - lo for lo, hi in self.edges]
        self.split = nn.ModuleList(
            nn.Sequential(nn.LayerNorm(4 * w), nn.Linear(4 * w, d)) for w in widths)
        self.blocks = nn.ModuleList(_Block(d, hidden) for _ in range(n_blocks))
        self.heads = nn.ModuleList(
            nn.Sequential(
                nn.LayerNorm(d),
                nn.Linear(d, MASK_EXPANSION * d),
                nn.Tanh(),
                nn.Linear(MASK_EXPANSION * d, 2 * 4 * w),
                nn.GLU(dim=-1),
            )
            for w in widths)

    def forward(self, spec: torch.Tensor) -> torch.Tensor:
        b, _, _, t = spec.shape
        bands = []
        for (lo, hi), split in zip(self.edges, self.split):
            x = spec[:, :, lo:hi, :].permute(0, 3, 1, 2).reshape(b, t, 4 * (hi - lo))
            bands.append(split(x))
        z = torch.stack(bands, dim=1)  # [B, K, T, d]
        for block in self.blocks:
            z = block(z)
        masks = []
        for k, ((lo, hi), head) in enumerate(zip(self.edges, self.heads)):
            m = head(z[:, k])  # [B, T, 4 * w]
            masks.append(m.reshape(b, t, 4, hi - lo).permute(0, 2, 3, 1))
        return torch.cat(masks, dim=2)


def count_params(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters())
