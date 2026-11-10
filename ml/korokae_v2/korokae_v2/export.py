"""Export KorokaeNet v2 to ONNX and write the KRP2 parity fixture for the Java engine.

CLI:
    python -m korokae_v2.export --untrained --blocks 4 --dim 96 --out-dir DIR
writes DIR/korokae_v2_<blocks>_<dim>.onnx and DIR/korokae_v2_<blocks>_<dim>_parity.bin.

KRP2 layout (little-endian): b"KRP2", int32 version=1, int32 sampleRate, int32 frames,
float32[frames*2] input (interleaved L, R), float32[frames*2] expected instrumental.
"""
from __future__ import annotations

import argparse
import math
import os
import struct

import numpy as np
import torch

from .dsp import SR, separate
from .model import KorokaeNetV2, count_params

OPSET = 17


def export_onnx(model: torch.nn.Module, path: str) -> None:
    model = model.eval()
    example = torch.randn(1, 4, 1025, 64)
    torch.onnx.export(
        model, (example,), path,
        input_names=["spec"], output_names=["mask"],
        dynamic_axes={"spec": {3: "frames"}, "mask": {3: "frames"}},
        opset_version=OPSET, dynamo=False,
    )


def _fixture_signal(seconds: float, seed: int) -> torch.Tensor:
    frames = int(round(seconds * SR))
    g = torch.Generator().manual_seed(seed)
    t = torch.arange(frames, dtype=torch.float64) / SR
    noise = torch.randn(2, frames, generator=g, dtype=torch.float64) * 0.1  # -20 dBFS
    left = noise[0] + 0.3 * torch.sin(2 * math.pi * 220.0 * t)
    right = noise[1] + 0.3 * torch.sin(2 * math.pi * 330.0 * t)
    return torch.stack([left, right]).float()


def write_parity_fixture(model: torch.nn.Module, path: str,
                         seconds: float = 10.0, seed: int = 1234) -> None:
    model = model.eval()
    x = _fixture_signal(seconds, seed)
    y = separate(x, model)
    frames = x.shape[1]
    with open(path, "wb") as f:
        f.write(b"KRP2")
        f.write(struct.pack("<iii", 1, SR, frames))
        f.write(x.T.contiguous().numpy().astype("<f4").tobytes())
        f.write(y.T.contiguous().numpy().astype("<f4").tobytes())


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--untrained", action="store_true", help="export randomly initialised weights")
    ap.add_argument("--blocks", type=int, default=4)
    ap.add_argument("--dim", type=int, default=96)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--out-dir", required=True)
    args = ap.parse_args()
    if not args.untrained:
        raise SystemExit("only --untrained is supported until training lands (Plan 2)")
    torch.manual_seed(args.seed)
    model = KorokaeNetV2(n_blocks=args.blocks, d=args.dim).eval()
    os.makedirs(args.out_dir, exist_ok=True)
    stem = os.path.join(args.out_dir, f"korokae_v2_{args.blocks}_{args.dim}")
    export_onnx(model, stem + ".onnx")
    write_parity_fixture(model, stem + "_parity.bin")
    size_mb = os.path.getsize(stem + ".onnx") / 1e6
    print(f"params={count_params(model)} onnx={size_mb:.1f}MB -> {stem}.onnx, {stem}_parity.bin")


if __name__ == "__main__":
    main()
