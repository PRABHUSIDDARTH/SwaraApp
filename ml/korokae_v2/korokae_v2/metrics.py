"""Our own signal-to-distortion ratio (the MDX-style "new SDR"), in dB."""
from __future__ import annotations

import torch


def sdr(est: torch.Tensor, ref: torch.Tensor, eps: float = 1e-8) -> torch.Tensor:
    """10·log10((‖ref‖² + eps) / (‖ref − est‖² + eps)) over the last two axes (channel, time)."""
    ref64, est64 = ref.double(), est.double()
    num = ref64.pow(2).sum(dim=(-2, -1)) + eps
    den = (ref64 - est64).pow(2).sum(dim=(-2, -1)) + eps
    return (10 * torch.log10(num / den)).float()
