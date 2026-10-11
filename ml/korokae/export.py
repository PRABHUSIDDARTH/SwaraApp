"""Export the trained KorokaeNet to the binary format read by KorokaeModel.java,
plus a parity vector used by the JVM unit test (KorokaeEngineParityTest).

Usage: python3 export.py [checkpoint]
"""
import pathlib
import struct
import sys

import numpy as np
import torch

from model import HIDDEN, HOP, LAYERS, N_FFT, KorokaeNet, istft, stft

ROOT = pathlib.Path(__file__).resolve().parent
# Android module lives in <repo>/android/app; the iOS app (ios/) bundles the same asset
# by reference and reuses the parity fixture in its SwaraCore tests.
APP = ROOT.parent.parent / "android" / "app"
ASSET = APP / "src" / "main" / "assets" / "models" / "korokae_v1.bin"
PARITY = APP / "src" / "test" / "resources" / "korokae_parity.bin"


def f32(a) -> bytes:
    return np.ascontiguousarray(a.detach().cpu().numpy(), dtype="<f4").tobytes()


def export_weights(net: KorokaeNet) -> None:
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    with open(ASSET, "wb") as fh:
        fh.write(b"KRK1")
        fh.write(struct.pack("<5i", 1, N_FFT, HOP, HIDDEN, LAYERS))
        fh.write(f32(net.mean))
        fh.write(f32(net.std))
        fh.write(f32(net.inp.weight))
        fh.write(f32(net.inp.bias))
        for l in range(LAYERS):
            fh.write(f32(getattr(net.gru, f"weight_ih_l{l}")))
            fh.write(f32(getattr(net.gru, f"weight_hh_l{l}")))
            fh.write(f32(getattr(net.gru, f"bias_ih_l{l}")))
            fh.write(f32(getattr(net.gru, f"bias_hh_l{l}")))
        fh.write(f32(net.out.weight))
        fh.write(f32(net.out.bias))
    print(f"wrote {ASSET} ({ASSET.stat().st_size / 1e6:.2f} MB)")


@torch.no_grad()
def streaming_reference(net: KorokaeNet, x: torch.Tensor) -> torch.Tensor:
    """Offline equivalent of KorokaeEngine: hop zeros of pre-roll, frames, mask, OLA.
    Returns wet output in padded coordinates [2, hop + T]."""
    xp = torch.cat([torch.zeros(2, HOP), x], dim=1)
    n = xp.shape[1] - ((xp.shape[1] - N_FFT) % HOP)
    xp = xp[:, :n]
    X = stft(xp)
    mask = net(stft(xp.mean(0, keepdim=True)).abs())
    return istft(mask * X, n)


def export_parity(net: KorokaeNet) -> None:
    g = torch.Generator().manual_seed(7)
    T = 44100 // 2
    t = torch.arange(T) / 44100.0
    tone = 0.3 * torch.sin(2 * np.pi * 220 * t) + 0.2 * torch.sin(2 * np.pi * 1330 * t)
    x = torch.stack([tone, tone * 0.8]) + 0.05 * torch.randn(2, T, generator=g)
    y = streaming_reference(net, x)
    PARITY.parent.mkdir(parents=True, exist_ok=True)
    with open(PARITY, "wb") as fh:
        fh.write(struct.pack("<2i", T, y.shape[1]))
        fh.write(f32(x.T.reshape(-1)))  # interleaved input
        fh.write(f32(y.T.reshape(-1)))  # interleaved reference (padded coords)
    print(f"wrote {PARITY}")


def main() -> int:
    ckpt = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "checkpoints" / "korokae_best.pt"
    net = KorokaeNet()
    net.load_state_dict(torch.load(ckpt, map_location="cpu"))
    net.eval()
    export_weights(net)
    export_parity(net)
    return 0


if __name__ == "__main__":
    sys.exit(main())
