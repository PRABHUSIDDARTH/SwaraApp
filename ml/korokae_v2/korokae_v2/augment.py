"""On-the-fly mixing and augmentation (spec 3.3), batched on the training device.

Every random value comes from the given CPU torch.Generator, so a (seed, step) pair reproduces a
batch exactly. Mastering is a time-varying gain applied identically to both stems, so
vocals + accompaniment == mix always holds and the targets stay exact.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import torch
import torch.nn.functional as F

SR = 44100
_EQ_FFT = 2048
_EQ_HOP = 512
_BLOCK = 441  # 10 ms compressor blocks


@dataclass
class AugmentConfig:
    level_db: tuple[float, float] = (-10.0, 10.0)
    varispeed_semitones: float = 2.0
    p_varispeed: float = 0.5
    p_reverb: float = 0.5
    rt60_s: tuple[float, float] = (0.2, 2.0)
    eq_db: float = 6.0
    p_eq: float = 0.7
    p_master: float = 0.8
    comp_ratio: tuple[float, float] = (1.5, 6.0)
    limit_ceiling_db: tuple[float, float] = (-1.0, -0.1)
    width: tuple[float, float] = (0.0, 1.0)
    pan: tuple[float, float] = (-0.5, 0.5)


def _u(gen: torch.Generator, n: int, lo: float, hi: float) -> torch.Tensor:
    return lo + (hi - lo) * torch.rand(n, generator=gen, dtype=torch.float64)


def _p(gen: torch.Generator, n: int, p: float) -> torch.Tensor:
    return torch.rand(n, generator=gen) < p


def varispeed(x: torch.Tensor, semitones: float) -> torch.Tensor:
    """Resample [..., L] so pitch moves by `semitones` (tempo moves with it); crop/pad to L."""
    length = x.shape[-1]
    m = max(2, round(length / 2 ** (semitones / 12)))
    spec = torch.fft.rfft(x.double(), n=length)
    bins = m // 2 + 1
    out_spec = torch.zeros(*spec.shape[:-1], bins, dtype=spec.dtype, device=x.device)
    k = min(bins, spec.shape[-1])
    out_spec[..., :k] = spec[..., :k]
    y = torch.fft.irfft(out_spec, n=m) * (m / length)
    y = y[..., :length] if m >= length else F.pad(y, (0, length - m))
    return y.to(x.dtype)


def make_ir(rt60: float, gen: torch.Generator, sr: int = SR) -> torch.Tensor:
    """Our own synthetic stereo room: direct path, 3-8 early reflections, decaying noise tail."""
    n = int(rt60 * sr)
    t = torch.arange(n, dtype=torch.float64) / sr
    tail = torch.randn(2, n, generator=gen, dtype=torch.float64) * torch.exp(-6.91 * t / rt60)
    tail *= 0.1 + 0.4 * torch.rand(1, generator=gen, dtype=torch.float64)
    ir = tail
    ir[:, 0] = 1.0
    for _ in range(int(torch.randint(3, 9, (1,), generator=gen))):
        pos = int(torch.randint(int(0.005 * sr), int(0.08 * sr), (1,), generator=gen))
        ir[:, min(pos, n - 1)] += 0.6 * (torch.rand(2, generator=gen, dtype=torch.float64) - 0.3)
    ir /= ir.pow(2).sum(dim=1, keepdim=True).sqrt()
    return ir.float()


def reverb(x: torch.Tensor, ir: torch.Tensor) -> torch.Tensor:
    """x [B, 2, L] convolved per channel with ir [2, n] via FFT, cropped to L."""
    length = x.shape[-1]
    n = length + ir.shape[-1] - 1
    y = torch.fft.irfft(torch.fft.rfft(x.double(), n=n) * torch.fft.rfft(ir.double().to(x.device), n=n), n=n)
    return y[..., :length].to(x.dtype)


def random_eq(x: torch.Tensor, gen: torch.Generator, eq_db: float = 6.0) -> torch.Tensor:
    """Smooth random gain curve (8 points on log-frequency, within ±eq_db), per batch item."""
    b, c, length = x.shape
    win = torch.hann_window(_EQ_FFT, device=x.device)
    spec = torch.stft(x.reshape(b * c, length), _EQ_FFT, _EQ_HOP, window=win, return_complex=True)
    freqs = torch.linspace(0, SR / 2, spec.shape[1], dtype=torch.float64)
    logf = torch.log10(freqs.clamp(min=20.0, max=20000.0))
    knots = torch.linspace(math.log10(20.0), math.log10(20000.0), 8, dtype=torch.float64)
    gains_db = _u(gen, b * 8, -eq_db, eq_db).reshape(b, 8)
    idx = torch.bucketize(logf, knots).clamp(1, 7)
    w = ((logf - knots[idx - 1]) / (knots[idx] - knots[idx - 1])).clamp(0, 1)
    curve_db = gains_db[:, idx - 1] * (1 - w) + gains_db[:, idx] * w  # [B, F]
    gain = (10 ** (curve_db / 20)).float().to(x.device).repeat_interleave(c, dim=0)
    y = torch.istft(spec * gain[:, :, None], _EQ_FFT, _EQ_HOP, window=win, length=length)
    return y.reshape(b, c, length)


def pan_width(x: torch.Tensor, pan: torch.Tensor, width: torch.Tensor) -> torch.Tensor:
    """Mid/side width scaling, then constant-power pan (pan in [-1, 1])."""
    pan = pan.to(x.device, x.dtype)[:, None]
    width = width.to(x.device, x.dtype)[:, None]
    mid = (x[:, 0] + x[:, 1]) / 2
    side = (x[:, 0] - x[:, 1]) / 2 * width
    theta = (pan + 1) * math.pi / 4
    left = (mid + side) * torch.cos(theta) * math.sqrt(2)
    right = (mid - side) * torch.sin(theta) * math.sqrt(2)
    return torch.stack([left, right], dim=1)


def master_gain(mix: torch.Tensor, ratio: torch.Tensor, ceiling_db: torch.Tensor,
                thr_below_peak_db: torch.Tensor | None = None) -> torch.Tensor:
    """Time-varying gain [B, 1, L] in (0, 1]: block-RMS compressor then a peak limiter.

    The limiter gain is a min-filter followed by a same-width moving average, so it never exceeds
    the instantaneous requirement and |mix * g| <= ceiling holds at every sample.
    """
    b, _, length = mix.shape
    dev = mix.device
    ratio = ratio.to(dev, torch.float32)[:, None]
    ceiling = (10 ** (ceiling_db.to(dev, torch.float32) / 20))[:, None, None]
    below = (thr_below_peak_db if thr_below_peak_db is not None
             else torch.full((b,), 12.0)).to(dev, torch.float32)[:, None]
    pad = (-length) % _BLOCK
    blocks = F.pad(mix.float().pow(2).mean(dim=1), (0, pad)).reshape(b, -1, _BLOCK).mean(-1)
    level = 10 * torch.log10(blocks + 1e-10)  # [B, nb]
    thr = level.max(dim=1, keepdim=True).values - below
    gain_db = -(level - thr).clamp(min=0) * (1 - 1 / ratio)
    gain_db = F.avg_pool1d(gain_db[:, None], 9, 1, 4, count_include_pad=False)
    comp = 10 ** (F.interpolate(gain_db, size=length, mode="linear", align_corners=False) / 20)
    peak = (mix.float() * comp).abs().amax(dim=1, keepdim=True)
    need = torch.clamp(ceiling / (peak + 1e-9), max=1.0)
    w = 221  # ±2.5 ms
    lim = -F.max_pool1d(-need, w, 1, w // 2)
    lim = F.avg_pool1d(F.pad(lim, (w // 2, w // 2), mode="replicate"), w, 1)
    return (comp * lim).clamp(max=1.0)


def mix_batch(vocal: torch.Tensor, accomp: torch.Tensor, cfg: AugmentConfig,
              gen: torch.Generator) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    """Raw stems [B, 2, L] -> (mix, vocal target, accompaniment target), all [B, 2, L]."""
    b = vocal.shape[0]
    v, a = vocal.float().clone(), accomp.float().clone()
    for stem in (v, a):
        do = _p(gen, b, cfg.p_varispeed)
        st = _u(gen, b, -cfg.varispeed_semitones, cfg.varispeed_semitones)
        for i in range(b):
            if do[i]:
                stem[i] = varispeed(stem[i], float(st[i]))
    for stem in (v, a):
        do = _p(gen, b, cfg.p_eq).to(stem.device)[:, None, None]
        stem.copy_(torch.where(do, random_eq(stem, gen, cfg.eq_db), stem))
    do_rev = _p(gen, b, cfg.p_reverb)
    rt = _u(gen, b, *cfg.rt60_s)
    for i in range(b):
        if do_rev[i]:
            v[i] = reverb(v[i:i + 1], make_ir(float(rt[i]), gen))[0]
    v = pan_width(v, _u(gen, b, *cfg.pan), _u(gen, b, *cfg.width))
    a = pan_width(a, _u(gen, b, *cfg.pan) * 0.5, _u(gen, b, 0.5, 1.0))
    ev = v.double().pow(2).sum(dim=(1, 2))
    ea = a.double().pow(2).sum(dim=(1, 2))
    target = 10 ** (_u(gen, b, *cfg.level_db) / 10).to(v.device)
    scale = torch.where((ev > 1e-10) & (ea > 1e-10), (ea * target / ev.clamp(min=1e-10)).sqrt(),
                        torch.ones_like(ev))
    v = v * scale.float()[:, None, None]
    do_m = _p(gen, b, cfg.p_master).to(v.device)[:, None, None]
    g = master_gain(v + a, _u(gen, b, *cfg.comp_ratio), _u(gen, b, *cfg.limit_ceiling_db),
                    _u(gen, b, 6.0, 18.0))
    g = torch.where(do_m, g, torch.ones_like(g))
    v, a = v * g, a * g
    peak = (v + a).abs().amax(dim=(1, 2), keepdim=True)
    norm = torch.where(peak > 1.0, 1.0 / peak, torch.ones_like(peak))
    v, a = v * norm, a * norm
    return v + a, v, a
