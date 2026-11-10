"""Entry points for the Kaggle notebooks (ml/korokae_v2/kaggle/*/run.py call these after cloning).

Data roots are found by searching /kaggle/input for manifest.csv files (prep notebook outputs);
the checkpoint dataset is the directory holding korokae_ckpt.txt.
"""
from __future__ import annotations

import glob
import os
import shutil
import time

from .data import build
from .data.manifest import MANIFEST_NAME, merge_manifests, write_manifest
from .render_eval import render
from .train import _checkpoints, load_config, train

INPUT = "/kaggle/input"
WORKING = "/kaggle/working"
CKPT_MARKER = "korokae_ckpt.txt"
VAL = dict(split="val", n_clips=64, seconds=10.0, seed=1001)
TEST = dict(split="test", n_clips=200, seconds=10.0, seed=2002)


def prep(sources: list[str], budget_hours: float) -> None:
    build.build(sources, os.path.join(WORKING, "data"), "/tmp/korokae_work", budget_hours)


def _marker_dir(marker: str) -> str | None:
    found = glob.glob(f"{INPUT}/**/{marker}", recursive=True)
    return os.path.dirname(found[0]) if found else None


def train_session(config: str, max_minutes: float, started_at: float | None = None,
                  fresh: bool = False, ckpt_marker: str = CKPT_MARKER,
                  init_marker: str | None = None) -> dict:
    """One Kaggle session. Never restarts silently: without `fresh` the checkpoint dataset must
    be mounted and hold checkpoints; with `fresh` it must hold none. `max_minutes` counts from
    `started_at` (the notebook's start), so setup time is included in the budget."""
    started_at = time.time() if started_at is None else started_at
    src = _marker_dir(ckpt_marker)
    if src is None:
        raise SystemExit(f"checkpoint dataset ({ckpt_marker}) is not mounted under {INPUT}")
    has_ckpt = bool(_checkpoints(src))
    if not fresh and not has_ckpt:
        raise SystemExit("checkpoint dataset has no checkpoints; start the first session with FRESH")
    if fresh and has_ckpt:
        raise SystemExit("refusing a FRESH start: the checkpoint dataset already has checkpoints")
    cfg = load_config(config)
    if cfg.get("init_from") == "auto":
        init_dir = _marker_dir(init_marker) if init_marker else None
        if init_dir is None or not os.path.isfile(os.path.join(init_dir, "best.pt")):
            raise SystemExit(f"init_from: auto needs best.pt next to {init_marker}")
        cfg["init_from"] = os.path.join(init_dir, "best.pt")

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
    for name in os.listdir(src):
        if name.endswith(".pt") or name == "log.jsonl":
            shutil.copy2(os.path.join(src, name), os.path.join(ckpt, name))
    remaining = max_minutes - (time.time() - started_at) / 60
    print(f"setup took {(time.time() - started_at) / 60:.1f} min; training for {remaining:.1f} min",
          flush=True)
    return train(cfg, merged, eval_root, ckpt, max(remaining, 1e-3))
