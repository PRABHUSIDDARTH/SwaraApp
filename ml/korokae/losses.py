"""Loss terms for Korokae v2 training."""
import torch

from model import SR  # noqa: F401

C_EXP = 0.3
EPS = 1e-8


def compressed_loss(est: torch.Tensor, ref: torch.Tensor) -> torch.Tensor:
    """v1 loss (train.loss_fn): power-compressed magnitude plus a weighted complex term."""
    est_abs = est.abs() + EPS
    ref_abs = ref.abs() + EPS
    mag = est_abs ** C_EXP - ref_abs ** C_EXP
    cplx = est / est_abs * est_abs ** C_EXP - ref / ref_abs * ref_abs ** C_EXP
    return mag.abs().mean() + 0.3 * cplx.abs().mean()


def vocal_residual_loss(mask: torch.Tensor, voc_spec: torch.Tensor) -> torch.Tensor:
    """Energy of whatever vocal the mask lets through: mean |mask * V|."""
    return (mask * voc_spec).abs().mean()


def mrstft_loss(est_wave: torch.Tensor, ref_wave: torch.Tensor,
                fft_sizes=(512, 1024, 2048)) -> torch.Tensor:
    """Multi-resolution STFT loss: spectral convergence plus log-magnitude L1, averaged."""
    est = est_wave.reshape(-1, est_wave.shape[-1])
    ref = ref_wave.reshape(-1, ref_wave.shape[-1])
    total = est.new_zeros(())
    for n in fft_sizes:
        win = torch.hann_window(n, device=est.device)
        e = torch.stft(est, n, n // 4, window=win, return_complex=True).abs()
        r = torch.stft(ref, n, n // 4, window=win, return_complex=True).abs()
        conv = torch.linalg.norm(r - e) / (torch.linalg.norm(r) + EPS)
        logm = (torch.log(r + EPS) - torch.log(e + EPS)).abs().mean()
        total = total + conv + logm
    return total / len(fft_sizes)


def loss_v2(masks, mix_spec, acc_spec, voc_spec, est_wave, acc_wave) -> torch.Tensor:
    """compressed + 0.5 * vocal residual + 0.5 * multi-resolution STFT."""
    est = masks * mix_spec
    return (compressed_loss(est, acc_spec)
            + 0.5 * vocal_residual_loss(masks, voc_spec)
            + 0.5 * mrstft_loss(est_wave, acc_wave))
