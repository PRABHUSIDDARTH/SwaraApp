"""KorokaeNet v2: band-split transformer predicting a complex stereo vocal mask.

Input  spec [B, 4, BINS, T] = (L.re, L.im, R.re, R.im) of the mixture STFT.
Output mask [B, 4, BINS, T] = complex vocal mask per channel, same channel order.
The instrumental is X - M*X (see dsp.apply_mask).

Layout: band split (23 bands, dsp.band_edges) + learned band embedding -> n_blocks x
[time attention per band at half the frame rate (strided conv down, rotary self-attention,
transposed conv up, residual), band attention per frame] -> per-band MLP mask head with GLU.

Sized for the S21+ speed gate (4-minute song in <= 60 s): the phone sustains roughly
10-20 GMAC/s, so the whole network must stay near 1 MMAC per (band, frame) cell. The default
config (4 blocks, d 96) is ~3.3M parameters and ~13 GMAC per 8 s window.
"""
from __future__ import annotations

import torch
from torch import nn

from .dsp import band_edges


class _Rotary(nn.Module):
    """Rotary position embedding over the sequence axis of [N, H, S, dh]."""

    def __init__(self, dim: int):
        super().__init__()
        self.register_buffer("inv", 1.0 / (10000 ** (torch.arange(0, dim, 2).float() / dim)),
                             persistent=False)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        pos = torch.arange(x.shape[-2], dtype=torch.float32, device=x.device)
        f = pos[:, None] * self.inv[None, :]  # [S, dh/2]
        cos, sin = f.cos(), f.sin()
        x1, x2 = x[..., 0::2], x[..., 1::2]
        return torch.stack([x1 * cos - x2 * sin, x1 * sin + x2 * cos], dim=-1).flatten(-2)


class _Layer(nn.Module):
    """Pre-norm transformer layer on [N, S, d]."""

    def __init__(self, d: int, heads: int, ffn: int, rotary: bool):
        super().__init__()
        self.heads = heads
        self.norm1 = nn.LayerNorm(d)
        self.qkv = nn.Linear(d, 3 * d)
        self.out = nn.Linear(d, d)
        self.norm2 = nn.LayerNorm(d)
        self.ff = nn.Sequential(nn.Linear(d, ffn * d), nn.GELU(), nn.Linear(ffn * d, d))
        self.rotary = _Rotary(d // heads) if rotary else None

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        n, s, d = x.shape
        dh = d // self.heads
        q, k, v = self.qkv(self.norm1(x)).reshape(n, s, 3, self.heads, dh).permute(2, 0, 3, 1, 4)
        if self.rotary is not None:
            q, k = self.rotary(q), self.rotary(k)
        q = q * dh ** -0.5
        kt = k.transpose(-1, -2)
        # One head at a time: the [N, S, S] score tensor of a single head is what bounds peak
        # memory on the phone (all four at once held ~130 MB per window for time attention).
        a = torch.cat([torch.softmax(q[:, h:h + 1] @ kt[:, h:h + 1], dim=-1) @ v[:, h:h + 1]
                       for h in range(self.heads)], dim=1)  # [N, H, S, dh]
        x = x + self.out(a.transpose(1, 2).reshape(n, s, d))
        return x + self.ff(self.norm2(x))


class _Block(nn.Module):
    def __init__(self, d: int, heads: int, ffn: int):
        super().__init__()
        self.down = nn.Conv1d(d, d, kernel_size=3, stride=2, padding=1)  # T -> ceil(T / 2)
        self.time = _Layer(d, heads, ffn, rotary=True)
        self.up = nn.ConvTranspose1d(d, d, kernel_size=2, stride=2)  # -> 2 * ceil(T / 2)
        self.band = _Layer(d, heads, ffn, rotary=False)

    def forward(self, z: torch.Tensor) -> torch.Tensor:  # [B, K, T, d]
        b, k, t, d = z.shape
        h = self.down(z.reshape(b * k, t, d).transpose(1, 2)).transpose(1, 2)
        h = self.up(self.time(h).transpose(1, 2))[:, :, :t].transpose(1, 2)
        z = z + h.reshape(b, k, t, d)
        z = self.band(z.transpose(1, 2).reshape(b * t, k, d))
        return z.reshape(b, t, k, d).transpose(1, 2)


class KorokaeNetV2(nn.Module):
    def __init__(self, n_blocks: int = 4, d: int = 96, heads: int = 4, ffn: int = 2,
                 head_expansion: int = 2):
        super().__init__()
        self.edges = band_edges()
        widths = [hi - lo for lo, hi in self.edges]
        self.split = nn.ModuleList(
            nn.Sequential(nn.LayerNorm(4 * w), nn.Linear(4 * w, d)) for w in widths)
        self.band_emb = nn.Parameter(torch.randn(len(widths), d) * 0.02)
        self.blocks = nn.ModuleList(_Block(d, heads, ffn) for _ in range(n_blocks))
        self.heads = nn.ModuleList(
            nn.Sequential(
                nn.LayerNorm(d),
                nn.Linear(d, head_expansion * d),
                nn.Tanh(),
                nn.Linear(head_expansion * d, 2 * 4 * w),
                nn.GLU(dim=-1),
            )
            for w in widths)

    def forward(self, spec: torch.Tensor) -> torch.Tensor:
        b, _, _, t = spec.shape
        bands = []
        for (lo, hi), split in zip(self.edges, self.split):
            x = spec[:, :, lo:hi, :].permute(0, 3, 1, 2).reshape(b, t, 4 * (hi - lo))
            bands.append(split(x))
        z = torch.stack(bands, dim=1) + self.band_emb[None, :, None, :]  # [B, K, T, d]
        for block in self.blocks:
            z = block(z)
        masks = []
        for k, ((lo, hi), head) in enumerate(zip(self.edges, self.heads)):
            m = head(z[:, k])  # [B, T, 4 * w]
            masks.append(m.reshape(b, t, 4, hi - lo).permute(0, 2, 3, 1))
        return torch.cat(masks, dim=2)


def count_params(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters())
