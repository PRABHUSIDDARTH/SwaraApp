"""Causal real-time vocal-removal mask network shared by train/export.

Frame pipeline (must stay identical to KorokaeEngine.java):
  sqrt-Hann window, N_FFT=1024, HOP=512 (50% overlap, perfect reconstruction).
  feature  = (log1p(|mid|) - mean) / std, mid = (L + R) / 2   [513 bins]
  hidden   = relu(W_in feature + b_in)                       [256]
  2 x GRU(256) (PyTorch gate order r, z, n)
  mask     = sigmoid(W_out h + b_out)                         [513]
  output   = mask * STFT(L), mask * STFT(R)
"""
import torch
import torch.nn as nn

SR = 44100
N_FFT = 1024
HOP = 512
BINS = N_FFT // 2 + 1
HIDDEN = 256
LAYERS = 2


def window(device=None):
    return torch.hann_window(N_FFT, periodic=True, device=device).sqrt()


def stft(x: torch.Tensor) -> torch.Tensor:
    """x: [B, T] -> complex [B, BINS, F]."""
    return torch.stft(x, N_FFT, HOP, window=window(x.device), center=False, return_complex=True)


def istft(X: torch.Tensor, length: int) -> torch.Tensor:
    """Plain synthesis-window overlap-add, identical to the Java engine.

    sqrt-Hann analysis * sqrt-Hann synthesis at 50% overlap sums to exactly 1,
    so no normalisation is needed (edges of the signal fade in/out by design).
    """
    frames = torch.fft.irfft(X, n=N_FFT, dim=-2) * window(X.device)[:, None]  # [..., N_FFT, F]
    lead = frames.shape[:-2]
    f = frames.reshape(-1, N_FFT, frames.shape[-1])
    out = torch.nn.functional.fold(f, output_size=(1, length), kernel_size=(1, N_FFT), stride=(1, HOP))
    return out.reshape(*lead, length)


class KorokaeNet(nn.Module):
    def __init__(self):
        super().__init__()
        self.register_buffer("mean", torch.zeros(BINS))
        self.register_buffer("std", torch.ones(BINS))
        self.inp = nn.Linear(BINS, HIDDEN)
        self.gru = nn.GRU(HIDDEN, HIDDEN, num_layers=LAYERS, batch_first=True)
        self.out = nn.Linear(HIDDEN, BINS)

    def features(self, mid_mag: torch.Tensor) -> torch.Tensor:
        """mid_mag: [B, BINS, F] -> [B, F, BINS]."""
        f = torch.log1p(mid_mag).transpose(1, 2)
        return (f - self.mean) / self.std

    def forward(self, mid_mag: torch.Tensor) -> torch.Tensor:
        h = torch.relu(self.inp(self.features(mid_mag)))
        h, _ = self.gru(h)
        return torch.sigmoid(self.out(h)).transpose(1, 2)  # [B, BINS, F]
