"""Render fixed-seed validation / test mixes once and store them (spec 3.4, 7.2).

CLI: python -m korokae_v2.render_eval --manifest M --root R --out O --split test --n 200
         --seconds 10 --seed 2002
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from typing import Iterator

import numpy as np
import soundfile as sf
import torch

from .augment import AugmentConfig, mix_batch
from .data.manifest import read_manifest
from .data.segments import StemPool

SR = 44100
PEAK = 0.999  # headroom so PCM_24 never clips


def _sha1(path: str) -> str:
    with open(path, "rb") as f:
        return hashlib.sha1(f.read()).hexdigest()


def render(manifest: str, root: str, split: str, n_clips: int, seconds: float, seed: int,
           out_dir: str) -> None:
    rows = read_manifest(manifest)
    vocals = StemPool(rows, root, "vocal", split)
    accomp = StemPool(rows, root, "accompaniment", split)
    length = int(round(seconds * SR))
    dst = os.path.join(out_dir, split)
    os.makedirs(dst, exist_ok=True)
    clips = []
    for i in range(n_clips):
        rng = np.random.default_rng([seed, i])  # clip i is independent of n_clips
        v = vocals.sample(rng, length)
        v_src = vocals.last_path
        a = accomp.sample(rng, length)
        a_src = accomp.last_path
        gen = torch.Generator().manual_seed(seed * 1_000_003 + i)
        mix, vt, at = mix_batch(torch.from_numpy(v)[None], torch.from_numpy(a)[None],
                                AugmentConfig(), gen)
        peak = float(mix.abs().max())
        if peak > PEAK:
            mix, vt, at = (t * (PEAK / peak) for t in (mix, vt, at))
        for name, t in (("mix", mix), ("vocals", vt), ("accomp", at)):
            sf.write(os.path.join(dst, f"{i:04d}_{name}.flac"), t[0].T.numpy(), SR, subtype="PCM_24")
        clips.append({"index": i, "vocal": v_src, "accompaniment": a_src})
    meta = {"seed": seed, "n_clips": n_clips, "seconds": seconds,
            "manifest_sha1": _sha1(manifest), "clips": clips}
    with open(os.path.join(dst, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=1)


def load_clips(out_dir: str, split: str) -> Iterator[tuple[torch.Tensor, torch.Tensor, torch.Tensor]]:
    """Yields (mix, vocals, accomp), each float32 [2, L], in clip order."""
    dst = os.path.join(out_dir, split)
    with open(os.path.join(dst, "meta.json"), encoding="utf-8") as f:
        n = json.load(f)["n_clips"]
    for i in range(n):
        yield tuple(torch.from_numpy(sf.read(os.path.join(dst, f"{i:04d}_{name}.flac"),
                                             dtype="float32", always_2d=True)[0].T.copy())
                    for name in ("mix", "vocals", "accomp"))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--root", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--split", required=True, choices=["val", "test"])
    ap.add_argument("--n", type=int, required=True)
    ap.add_argument("--seconds", type=float, default=10.0)
    ap.add_argument("--seed", type=int, required=True)
    a = ap.parse_args()
    render(a.manifest, a.root, a.split, a.n, a.seconds, a.seed, a.out)


if __name__ == "__main__":
    main()
