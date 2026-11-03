"""Evaluate Korokae models on the MUSDB18 test split.

Usage: python3 evaluate.py --model v1 --out results/v1.json
"""
import argparse
import json
import pathlib
import sys

import numpy as np
import torch

from metrics import passthrough_sdr, sdr, vocal_attenuation_db
from model import BINS, HOP, N_FFT, KorokaeNet, istft, stft
from model2 import HIGH, STANDARD, KorokaeNet2, make_mask_fn

ROOT = pathlib.Path(__file__).resolve().parent


def load_tracks(split="test", data_root=None, subdir="npy"):
    base = pathlib.Path(data_root) if data_root else ROOT / "data"
    tracks = []
    for p in sorted((base / subdir / split).glob("*.npz")):
        d = np.load(p)
        tracks.append((torch.from_numpy(d["acc"]), torch.from_numpy(d["voc"])))
    return tracks


def _apply(mask_fn, x):
    """Run the mask on x [2, n] and return the processed signal [2, n]."""
    n = x.shape[1]
    mask = mask_fn(x)  # [C, BINS, F], C is 1 (shared) or 2 (per channel)
    return istft(mask * stft(x), n), mask


@torch.no_grad()
def evaluate(mask_fn, tracks, device):
    """mask_fn(mix[2, T]) -> mask [C, BINS, F]. Returns median metrics over usable tracks."""
    acc_sdrs, atts, passes = [], [], []
    for acc, voc in tracks:
        if (acc ** 2).sum() < 1e-6 or (voc ** 2).sum() < 1e-6:
            continue
        mix = (acc + voc).to(device)
        acc = acc.to(device)
        voc = voc.to(device)
        n = mix.shape[1] - ((mix.shape[1] - N_FFT) % HOP)
        mix, acc, voc = mix[:, :n], acc[:, :n], voc[:, :n]
        e = slice(N_FFT, n - N_FFT)

        est, mask = _apply(mask_fn, mix)
        acc_sdrs.append(sdr(est[:, e], acc[:, e]))
        leak = istft(mask * stft(voc), n)
        atts.append(vocal_attenuation_db(leak[:, e], voc[:, e]))
        out_acc, _ = _apply(mask_fn, acc)
        passes.append(passthrough_sdr(out_acc[:, e], acc[:, e]))
    return {
        "acc_sdr_median": float(np.median(acc_sdrs)),
        "vocal_attenuation_median_db": float(np.median(atts)),
        "passthrough_sdr_median": float(np.median(passes)),
        "n_tracks": len(acc_sdrs),
    }


def v1_mask_fn(net):
    def fn(mix):
        return net(stft(mix.mean(0, keepdim=True)).abs())
    return fn


def load_v1(device):
    net = KorokaeNet().to(device)
    net.load_state_dict(torch.load(ROOT / "checkpoints" / "korokae_best.pt", map_location=device))
    net.eval()
    return v1_mask_fn(net)


def load_v2(name, device):
    net = KorokaeNet2({"standard": STANDARD, "high": HIGH}[name]).to(device)
    net.load_state_dict(torch.load(ROOT / "checkpoints" / f"korokae2_{name}.pt", map_location=device))
    net.eval()
    return make_mask_fn(net)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    if args.model == "v1":
        mask_fn = load_v1(device)
    elif args.model in ("v2_standard", "v2_high"):
        mask_fn = load_v2(args.model.split("_")[1], device)
    else:
        raise SystemExit(f"unknown model {args.model!r}")
    result = evaluate(mask_fn, load_tracks("test"), device)
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
