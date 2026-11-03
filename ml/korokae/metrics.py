"""Evaluation metrics for Korokae models. All inputs are tensors shaped [C, T]."""
import torch

_EPS = 1e-10


def sdr(est: torch.Tensor, ref: torch.Tensor) -> float:
    """Signal-to-distortion ratio of est against ref, in dB."""
    num = (ref ** 2).sum().clamp(min=_EPS)
    den = ((ref - est) ** 2).sum().clamp(min=_EPS)
    return float(10 * torch.log10(num / den))


def vocal_attenuation_db(leak: torch.Tensor, voc: torch.Tensor) -> float:
    """How far the vocal stem is pushed down: energy(voc) / energy(leak), in dB.

    `leak` is the model's mask applied to the vocal stem alone. 0 dB means the
    vocal passes untouched; larger is better for karaoke.
    """
    num = (voc ** 2).sum().clamp(min=_EPS)
    den = (leak ** 2).sum().clamp(min=_EPS)
    return float(10 * torch.log10(num / den))


def passthrough_sdr(out: torch.Tensor, inp: torch.Tensor) -> float:
    """SDR of the model's output against its own input when the input has no vocals."""
    return sdr(out, inp)
