"""WakeNet: "hey swara" detector. float32 [B, 16000] audio in [-1, 1] -> float32 [B] probability.

The log-mel front end lives inside the module so the Android side feeds raw PCM:
n_fft 400, hop 160, 40 mels (HTK scale, 0-8 kHz, slaney-free triangular), log(power + 1e-6),
Hann window, no centring (98 frames per 1 s window). The STFT is a fixed conv1d against a
real/imag DFT basis, so it exports to ONNX opset 17 as plain Conv/Mul/Add and runs in onnxruntime.
"""
from __future__ import annotations

import math

import numpy as np
import torch
from torch import nn

SR = 16000
WINDOW = 16000
N_FFT = 400
HOP = 160
N_MELS = 40
LOG_EPS = 1e-6
N_FREQ = N_FFT // 2 + 1


def _hz_to_mel(f):
    return 2595.0 * np.log10(1.0 + np.asarray(f, dtype=np.float64) / 700.0)


def _mel_to_hz(m):
    return 700.0 * (10.0 ** (np.asarray(m, dtype=np.float64) / 2595.0) - 1.0)


def mel_filterbank(sr: int = SR, n_fft: int = N_FFT, n_mels: int = N_MELS) -> np.ndarray:
    """[n_mels, n_fft//2+1] triangular filters, HTK mel scale from 0 Hz to sr/2."""
    freqs = np.linspace(0, sr / 2, n_fft // 2 + 1)
    pts = _mel_to_hz(np.linspace(_hz_to_mel(0), _hz_to_mel(sr / 2), n_mels + 2))
    fb = np.zeros((n_mels, len(freqs)))
    for i in range(n_mels):
        lo, ctr, hi = pts[i], pts[i + 1], pts[i + 2]
        up = (freqs - lo) / (ctr - lo)
        down = (hi - freqs) / (hi - ctr)
        fb[i] = np.maximum(0.0, np.minimum(up, down))
    return fb.astype(np.float32)


class DSBlock(nn.Module):
    """Depthwise 3x3 + pointwise 1x1, each with BatchNorm and ReLU."""

    def __init__(self, cin: int, cout: int, stride: tuple[int, int]):
        super().__init__()
        self.dw = nn.Conv2d(cin, cin, 3, stride=stride, padding=1, groups=cin, bias=False)
        self.bn1 = nn.BatchNorm2d(cin)
        self.pw = nn.Conv2d(cin, cout, 1, bias=False)
        self.bn2 = nn.BatchNorm2d(cout)
        self.act = nn.ReLU()

    def forward(self, x):
        x = self.act(self.bn1(self.dw(x)))
        return self.act(self.bn2(self.pw(x)))


class WakeNet(nn.Module):
    CHANNELS = (32, 48, 64, 96, 128)

    def __init__(self):
        super().__init__()
        k = torch.arange(N_FFT, dtype=torch.float64)
        win = torch.hann_window(N_FFT, periodic=True, dtype=torch.float64)
        f = torch.arange(N_FREQ, dtype=torch.float64)[:, None]
        ang = 2 * math.pi * f * k[None, :] / N_FFT
        basis = torch.cat([torch.cos(ang) * win, -torch.sin(ang) * win], dim=0)  # [2*N_FREQ, N_FFT]
        self.register_buffer("stft_basis", basis.float()[:, None, :], persistent=False)
        self.register_buffer("mel_fb", torch.from_numpy(mel_filterbank()), persistent=False)
        self.stem = nn.Sequential(nn.Conv2d(1, 16, 3, stride=(1, 2), padding=1, bias=False),
                                  nn.BatchNorm2d(16), nn.ReLU())
        strides = [(2, 1), (2, 1), (1, 1), (2, 1), (1, 1)]  # downsample mel axis, keep time resolution
        chans = (16,) + self.CHANNELS
        self.blocks = nn.Sequential(*[DSBlock(chans[i], chans[i + 1], strides[i]) for i in range(5)])
        self.head = nn.Linear(self.CHANNELS[-1], 1)

    def features(self, audio: torch.Tensor) -> torch.Tensor:
        """[B, 16000] -> log-mel [B, 40, 98]."""
        spec = nn.functional.conv1d(audio[:, None, :], self.stft_basis, stride=HOP)  # [B, 2F, T]
        re, im = spec[:, :N_FREQ], spec[:, N_FREQ:]
        power = re * re + im * im
        mel = torch.matmul(self.mel_fb, power)  # [B, 40, T]
        return torch.log(mel + LOG_EPS)

    def logits(self, audio: torch.Tensor) -> torch.Tensor:
        x = self.features(audio)[:, None]  # [B, 1, 40, T]
        x = self.blocks(self.stem(x))
        x = x.mean(dim=(2, 3))  # global average pooling
        return self.head(x)[:, 0]

    def forward(self, audio: torch.Tensor) -> torch.Tensor:
        return torch.sigmoid(self.logits(audio))


def count_params(m: nn.Module) -> int:
    return sum(p.numel() for p in m.parameters())
