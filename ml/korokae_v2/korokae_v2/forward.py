"""Batched, unchunked separation for training and validation (same maths as dsp.separate)."""
from __future__ import annotations

import torch

from . import dsp


def separate_batch(model: torch.nn.Module, mix: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
    """mix [B, 2, L] (L <= 8 s) -> (vocals, instrumental), both [B, 2, L], float32.

    STFT/iSTFT run in float32 even under autocast; only the model may run in half precision.
    """
    b, c, length = mix.shape
    spec = dsp.stft(mix.float().reshape(b * c, length))
    spec = spec.reshape(b, c, dsp.BINS, -1)  # complex [B, 2, BINS, T]
    inp = torch.stack([spec[:, 0].real, spec[:, 0].imag, spec[:, 1].real, spec[:, 1].imag], dim=1)
    mask = model(inp).float()
    m = torch.complex(mask[:, 0::2], mask[:, 1::2])  # [B, 2, BINS, T]
    vocals_spec = m * spec
    inst_spec = spec - vocals_spec
    frames = spec.shape[-1]
    vocals = dsp.istft(vocals_spec.reshape(b * c, dsp.BINS, frames), length).reshape(b, c, length)
    inst = dsp.istft(inst_spec.reshape(b * c, dsp.BINS, frames), length).reshape(b, c, length)
    return vocals, inst
