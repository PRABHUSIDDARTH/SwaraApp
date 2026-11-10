"""Entry points for the Kaggle notebooks (ml/korokae_v2/kaggle/*/run.py call these after cloning).

Data roots are found by searching /kaggle/input for manifest.csv files (prep notebook outputs);
the checkpoint dataset is the directory holding korokae_ckpt.txt.
"""
from __future__ import annotations

import glob
import os
import shutil

from .data import build
from .data.manifest import MANIFEST_NAME, merge_manifests, write_manifest
from .render_eval import render
from .train import load_config, train

INPUT = "/kaggle/input"
WORKING = "/kaggle/working"
CKPT_MARKER = "korokae_ckpt.txt"
VAL = dict(split="val", n_clips=64, seconds=10.0, seed=1001)
TEST = dict(split="test", n_clips=200, seconds=10.0, seed=2002)


def prep(sources: list[str], budget_hours: float) -> None:
    build.build(sources, os.path.join(WORKING, "data"), "/tmp/korokae_work", budget_hours)


def train_session(config: str, max_minutes: float) -> dict:
    roots = sorted(os.path.dirname(p) for p in glob.glob(f"{INPUT}/**/{MANIFEST_NAME}", recursive=True))
    if not roots:
        raise SystemExit("no prep outputs with manifest.csv under /kaggle/input")
    merged = os.path.join(WORKING, "merged")
    os.makedirs(merged, exist_ok=True)
    write_manifest(merge_manifests(roots), os.path.join(merged, MANIFEST_NAME))
    eval_root = os.path.join(WORKING, "eval")
    for spec in (VAL, TEST):  # deterministic: identical clips every session
        render(os.path.join(merged, MANIFEST_NAME), merged, out_dir=eval_root, **spec)
    ckpt = os.path.join(WORKING, "ckpt")
    os.makedirs(ckpt, exist_ok=True)
    for marker in glob.glob(f"{INPUT}/**/{CKPT_MARKER}", recursive=True):
        src = os.path.dirname(marker)
        for name in os.listdir(src):
            if name.endswith(".pt") or name == "log.jsonl":
                shutil.copy2(os.path.join(src, name), os.path.join(ckpt, name))
    return train(load_config(config), merged, eval_root, ckpt, max_minutes)
