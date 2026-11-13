"""Training-example mixing: clip + background -> one 1.0 s window as the detector sees it."""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy.signal import butter, fftconvolve, sosfilt

SR = 16000
WINDOW = 16000
END_SLACK = 3200  # the clip ends within the last 200 ms of the window


@dataclass(frozen=True)
class MixConfig:
    """Mix parameters; defaults equal configs/base.yaml mix.* (a test keeps them in sync)."""
    snr_db: tuple[float, float] = (-5.0, 15.0)
    cheap_mic_p: float = 0.3
    cheap_mic_band_hz: tuple[float, float] = (300.0, 3400.0)
    reverb_p: float = 0.5
    gain_db: tuple[float, float] = (-6.0, 0.0)

    @classmethod
    def from_dict(cls, d: dict | None) -> "MixConfig":
        d = d or {}
        kw = {}
        for k in ("snr_db", "cheap_mic_band_hz", "gain_db"):
            if k in d:
                kw[k] = tuple(float(v) for v in d[k])
        for k in ("cheap_mic_p", "reverb_p"):
            if k in d:
                kw[k] = float(d[k])
        return cls(**kw)

    def sample_snr(self, rng: np.random.Generator) -> float:
        return float(rng.uniform(*self.snr_db))


def synthetic_rir(rng: np.random.Generator, sr: int = SR) -> np.ndarray:
    """Exponentially decaying noise tail (RT60 0.1-0.5 s) with a unit direct path."""
    rt60 = rng.uniform(0.1, 0.5)
    n = int(rt60 * sr)
    t = np.arange(n) / sr
    tail = rng.standard_normal(n) * 10 ** (-3.0 * t / rt60)  # -60 dB at rt60
    rir = 0.3 * tail
    rir[0] = 1.0
    return rir.astype(np.float32)


def band_limit(x: np.ndarray, lo: float = 300.0, hi: float = 3400.0, sr: int = SR) -> np.ndarray:
    sos = butter(4, [lo, hi], btype="bandpass", fs=sr, output="sos")
    return sosfilt(sos, x).astype(np.float32)


def _background_window(background: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    bg = np.asarray(background, dtype=np.float32)
    if len(bg) == 0:
        return np.zeros(WINDOW, np.float32)
    if len(bg) < WINDOW:
        bg = np.tile(bg, int(np.ceil(WINDOW / len(bg))))
    start = int(rng.integers(0, len(bg) - WINDOW + 1))
    return bg[start:start + WINDOW].copy()


def make_example(clip: np.ndarray, background: np.ndarray, snr_db: float,
                 rng: np.random.Generator, cfg: "MixConfig | dict | None" = None, *,
                 return_parts: bool = False):
    """Mix `clip` over `background` into a float32 window of 16000 samples.

    `cfg` (a MixConfig or the mix: dict of configs/base.yaml) supplies reverb_p, cheap_mic_p,
    cheap_mic_band_hz and gain_db. The clip is placed so it ends in the last 200 ms of the window;
    its head is trimmed only when it is longer than the window. SNR is measured over the span the
    clip occupies (after reverb / cheap-mic filtering), so it holds to the requested value; a
    random gain is then applied to the whole mix, kept within +-1.
    With return_parts=True returns (clip_part, background_part, span) instead.
    """
    if not isinstance(cfg, MixConfig):
        cfg = MixConfig.from_dict(cfg)
    x = np.asarray(clip, dtype=np.float32)
    if rng.random() < cfg.reverb_p:
        x = fftconvolve(x, synthetic_rir(rng))[:len(x)].astype(np.float32)
    if rng.random() < cfg.cheap_mic_p:
        x = band_limit(x, *cfg.cheap_mic_band_hz)
    end = WINDOW - int(rng.integers(0, END_SLACK + 1))
    end = max(end, min(len(x), WINDOW))  # a clip that fits is never trimmed
    if len(x) > end:
        x = x[len(x) - end:]
    start = end - len(x)
    span = slice(start, end)
    clip_part = np.zeros(WINDOW, np.float32)
    clip_part[span] = x
    bg = _background_window(background, rng)
    p_clip = float(np.mean(x ** 2)) if len(x) else 0.0
    p_bg = float(np.mean(bg[span] ** 2)) if len(x) else 0.0
    if p_clip > 0 and p_bg > 0:
        bg *= np.sqrt(p_clip / (p_bg * 10 ** (snr_db / 10)))
    gain = 10 ** (rng.uniform(*cfg.gain_db) / 20)
    clip_part *= gain
    bg *= gain
    peak = float(np.max(np.abs(clip_part + bg)))
    if peak > 1.0:
        clip_part /= peak
        bg /= peak
    if return_parts:
        return clip_part, bg, span
    return (clip_part + bg).astype(np.float32)
