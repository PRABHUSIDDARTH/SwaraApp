"""Training-time augmentation for Korokae v2 (EQ and reverb on a stem)."""
import math
from typing import Optional

import torch

from model import SR

EQ_ANCHORS_HZ = (100.0, 1000.0, 8000.0)
EQ_RANGE_DB = 6.0
EQ_NFFT = 2048
EQ_HOP = 512


def _uniform(rng: torch.Generator, lo: float, hi: float) -> float:
    return lo + (hi - lo) * float(torch.rand(1, generator=rng))


def random_eq(x: torch.Tensor, rng: torch.Generator,
              gains_db: Optional[torch.Tensor] = None) -> torch.Tensor:
    """Random 3-band EQ in the STFT domain. x: [C, T]. gains_db overrides the random draw."""
    if gains_db is None:
        gains_db = (torch.rand(3, generator=rng) * 2 - 1) * EQ_RANGE_DB
    freqs = torch.fft.rfftfreq(EQ_NFFT, 1.0 / SR).clamp(min=1.0).log()
    anchors = torch.tensor(EQ_ANCHORS_HZ).log()
    curve = torch.empty_like(freqs)
    for i, f in enumerate(freqs):  # piecewise-linear in log frequency, flat beyond the anchors
        if f <= anchors[0]:
            curve[i] = gains_db[0]
        elif f >= anchors[-1]:
            curve[i] = gains_db[-1]
        else:
            k = int((f > anchors).sum()) - 1
            t = (f - anchors[k]) / (anchors[k + 1] - anchors[k])
            curve[i] = gains_db[k] * (1 - t) + gains_db[k + 1] * t
    gain = 10 ** (curve / 20)
    win = torch.hann_window(EQ_NFFT)
    X = torch.stft(x, EQ_NFFT, EQ_HOP, window=win, return_complex=True)
    return torch.istft(X * gain[None, :, None], EQ_NFFT, EQ_HOP, window=win, length=x.shape[-1])


def random_reverb(x: torch.Tensor, rng: torch.Generator) -> torch.Tensor:
    """Adds decorrelated exponentially decaying noise reverb. x: [C, T]; RT60 0.1-1.2 s, wet 0.1-0.5."""
    rt60 = _uniform(rng, 0.1, 1.2)
    wet = _uniform(rng, 0.1, 0.5)
    C, T = x.shape
    L = int(rt60 * SR)
    t = torch.arange(L) / SR
    ir = torch.randn(C, L, generator=rng) * torch.exp(-6.908 * t / rt60)
    n = T + L - 1
    rev = torch.fft.irfft(torch.fft.rfft(x, n) * torch.fft.rfft(ir, n), n)[:, :T]
    scale = (x.pow(2).mean().sqrt() / rev.pow(2).mean().sqrt().clamp(min=1e-8))
    return (1 - wet) * x + wet * rev * scale
