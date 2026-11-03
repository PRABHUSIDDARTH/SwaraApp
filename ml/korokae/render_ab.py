"""Render a fixed A/B listening set: 20 s excerpts (from 60 s) of 5 test tracks.

Writes results/ab/<track>_{mix,v1,v2_standard,v2_high}.wav (git-ignored). Models without a
checkpoint are skipped. Usage: python3 render_ab.py
"""
import pathlib
import sys
import wave

import numpy as np
import torch

from evaluate import ROOT, load_tracks, load_v1, load_v2
from model import HOP, N_FFT, SR, istft, stft

START_S, LEN_S, N_TRACKS = 60, 20, 5


def write_wav(path, x):  # x [2, T] float
    pcm = (np.clip(x.T, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


@torch.no_grad()
def main() -> int:
    device = "cuda" if torch.cuda.is_available() else "cpu"
    out = ROOT / "results" / "ab"
    out.mkdir(parents=True, exist_ok=True)
    names = sorted(p.stem for p in (ROOT / "data" / "npy" / "test").glob("*.npz"))
    tracks = load_tracks("test")
    pick = np.linspace(0, len(tracks) - 1, N_TRACKS).astype(int)
    models = {"v1": load_v1(device)}
    for n in ("standard", "high"):
        if (ROOT / "checkpoints" / f"korokae2_{n}.pt").exists():
            models[f"v2_{n}"] = load_v2(n, device)
    for i in pick:
        acc, voc = tracks[i]
        start = min(START_S * SR, max(0, acc.shape[1] - LEN_S * SR))  # short tracks start earlier
        mix = (acc + voc)[:, start:start + LEN_S * SR].to(device)
        n = mix.shape[1] - ((mix.shape[1] - N_FFT) % HOP)
        mix = mix[:, :n]
        stem = names[i].replace(" ", "_")
        write_wav(out / f"{stem}_mix.wav", mix.cpu().numpy())
        for label, fn in models.items():
            y = istft(fn(mix) * stft(mix), n)
            write_wav(out / f"{stem}_{label}.wav", y.cpu().numpy())
    print(f"wrote excerpts to {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
