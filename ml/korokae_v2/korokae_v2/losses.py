"""Training loss (spec 5): waveform L1 + multi-resolution STFT magnitude L1, both stems."""
from __future__ import annotations

import torch

RESOLUTIONS = (512, 1024, 2048)


def _mag(x: torch.Tensor, n_fft: int) -> torch.Tensor:
    win = torch.hann_window(n_fft, device=x.device)
    s = torch.stft(x.reshape(-1, x.shape[-1]).float(), n_fft, n_fft // 4, window=win,
                   return_complex=True)
    return torch.sqrt(s.real.pow(2) + s.imag.pow(2) + 1e-8)  # finite gradient at zero


def _stem_loss(est: torch.Tensor, ref: torch.Tensor) -> torch.Tensor:
    loss = (est - ref).abs().mean()
    for n in RESOLUTIONS:
        loss = loss + (_mag(est, n) - _mag(ref, n)).abs().mean()
    return loss


def separation_loss(vocals_hat: torch.Tensor, inst_hat: torch.Tensor,
                    vocals: torch.Tensor, inst: torch.Tensor) -> torch.Tensor:
    return _stem_loss(vocals_hat, vocals) + _stem_loss(inst_hat, inst)
