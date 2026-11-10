"""Korokae v2 DSP: band layout, STFT / iSTFT and chunked separation.

This module is the source of truth that the Java engine (com.psthetech.swara.korokae.v2)
mirrors exactly. Conventions (see the plan's "Shared definitions"):

- Window: sqrt of the periodic Hann, used for analysis and synthesis.
- Framing: left-pad PAD = N_FFT - HOP zeros, right-pad so frames tile exactly and every sample
  is covered by N_FFT / HOP frames; overlap-add is divided by 2.0 (sum of Hann at 75% overlap).
- Model tensors: [1, 4, BINS, T] with channels (L.re, L.im, R.re, R.im).
- Chunking: 8 s windows with 1 s overlap and linear crossfades.
"""
from __future__ import annotations

import math
from typing import Callable

import torch

SR = 44100
N_FFT = 2048
HOP = 512
PAD = N_FFT - HOP  # 1536
BINS = N_FFT // 2 + 1  # 1025
WIN_SAMPLES = 8 * SR  # 352800
OVERLAP = SR  # 44100
OLA_GAIN = 2.0  # sum over frames of the Hann window at hop N_FFT / 4

# (upper edge in Hz, band width in Hz); the last band takes the remainder up to Nyquist.
_BAND_PLAN = ((1000, 100), (4000, 500), (8000, 1000), (16000, 4000))  # 23 bands


def band_edges() -> list[tuple[int, int]]:
    """Half-open bin ranges [lo, hi) covering bins 0 .. BINS-1 contiguously."""
    hz_edges = [0]
    for upper, width in _BAND_PLAN:
        while hz_edges[-1] < upper:
            hz_edges.append(hz_edges[-1] + width)
    bins = [round(hz * N_FFT / SR) for hz in hz_edges] + [BINS]
    return [(bins[i], bins[i + 1]) for i in range(len(bins) - 1)]


def window(device: torch.device | str | None = None) -> torch.Tensor:
    n = torch.arange(N_FFT, dtype=torch.float64)
    return torch.sqrt(0.5 - 0.5 * torch.cos(2 * math.pi * n / N_FFT)).float().to(device)


def frame_count(length: int) -> int:
    need = length + 2 * PAD
    return -(-(need - N_FFT) // HOP) + 1


def _padded_length(frames: int) -> int:
    return (frames - 1) * HOP + N_FFT


def stft(x: torch.Tensor) -> torch.Tensor:
    """x: [C, L] float -> complex64 [C, BINS, T]."""
    length = x.shape[-1]
    frames = frame_count(length)
    lp = _padded_length(frames)
    xp = torch.nn.functional.pad(x, (PAD, lp - PAD - length))
    seg = xp.unfold(-1, N_FFT, HOP) * window(x.device)  # [C, T, N_FFT]
    return torch.fft.rfft(seg, dim=-1).transpose(-1, -2)  # [C, BINS, T]


def istft(spec: torch.Tensor, length: int) -> torch.Tensor:
    """spec: complex [C, BINS, T] -> [C, length]."""
    frames = spec.shape[-1]
    lp = _padded_length(frames)
    seg = torch.fft.irfft(spec.transpose(-1, -2), n=N_FFT, dim=-1) * window(spec.device)  # [C, T, N_FFT]
    c = seg.shape[0]
    out = torch.nn.functional.fold(
        seg.transpose(1, 2),  # [C, N_FFT, T]
        output_size=(1, lp), kernel_size=(1, N_FFT), stride=(1, HOP),
    ).reshape(c, lp)
    return out[:, PAD:PAD + length] / OLA_GAIN


def to_model_input(spec: torch.Tensor) -> torch.Tensor:
    """complex [2, BINS, T] -> float [1, 4, BINS, T] as (L.re, L.im, R.re, R.im)."""
    return torch.stack([spec[0].real, spec[0].imag, spec[1].real, spec[1].imag]).unsqueeze(0)


def apply_mask(spec: torch.Tensor, mask: torch.Tensor) -> torch.Tensor:
    """Instrumental = X - M*X for complex X [2, BINS, T] and mask [1, 4, BINS, T]."""
    m = torch.complex(mask[0, 0::2], mask[0, 1::2])  # [2, BINS, T]
    return spec - m * spec


def _window_starts(length: int) -> list[int]:
    step = WIN_SAMPLES - OVERLAP
    starts = [0]
    while starts[-1] + step < length:
        starts.append(starts[-1] + step)
    return starts


def _crossfade(first: bool, last: bool) -> torch.Tensor:
    i = torch.arange(WIN_SAMPLES, dtype=torch.float64)
    fade_in = torch.ones_like(i) if first else torch.clamp((i + 1) / (OVERLAP + 1), max=1.0)
    fade_out = torch.ones_like(i) if last else torch.clamp((WIN_SAMPLES - i) / (OVERLAP + 1), max=1.0)
    return fade_in * fade_out


@torch.no_grad()
def separate(x: torch.Tensor,
             mask_fn: Callable[[torch.Tensor], torch.Tensor]) -> torch.Tensor:
    """Instrumental for stereo x [2, L] using the 8 s / 1 s-overlap chunking rule."""
    length = x.shape[-1]
    starts = _window_starts(length)
    acc = torch.zeros(2, length, dtype=torch.float64)
    wsum = torch.zeros(length, dtype=torch.float64)
    for k, start in enumerate(starts):
        seg = x[:, start:start + WIN_SAMPLES]
        seg = torch.nn.functional.pad(seg, (0, WIN_SAMPLES - seg.shape[-1]))
        spec = stft(seg)
        inst = istft(apply_mask(spec, mask_fn(to_model_input(spec))), WIN_SAMPLES).double()
        w = _crossfade(k == 0, k == len(starts) - 1)
        n = min(WIN_SAMPLES, length - start)
        acc[:, start:start + n] += inst[:, :n] * w[:n]
        wsum[start:start + n] += w[:n]
    return (acc / wsum).float()
