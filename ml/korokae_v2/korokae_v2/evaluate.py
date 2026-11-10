"""Synthetic-set evaluation (spec 7.2): median vocal / instrumental SDR of v1 and a v2 checkpoint
on the stored test mixes. Gate: v2 instrumental >= v1 instrumental + 5 dB.

CLI: python -m korokae_v2.evaluate --eval E --checkpoint best.pt [--split test] [--out DIR]
"""
from __future__ import annotations

import argparse
import json
import os

import numpy as np
import torch

from . import dsp
from .metrics import sdr
from .render_eval import load_clips
from .train import build_model
from .v1_baseline import load_v1, v1_instrumental

GATE_DB = 5.0


def gate(v1_inst: float, v2_inst: float) -> bool:
    return v2_inst >= v1_inst + GATE_DB


def load_v2(checkpoint: str) -> torch.nn.Module:
    state = torch.load(checkpoint, map_location="cpu", weights_only=False)
    model = build_model(state["cfg"]["model"])
    model.load_state_dict(state["model"])
    return model.eval()


@torch.no_grad()
def evaluate(eval_root: str, checkpoint: str | None, split: str = "test") -> dict:
    v1 = load_v1()
    v2 = load_v2(checkpoint) if checkpoint else None
    scores = {"v1": {"vocal": [], "inst": []}, "v2": {"vocal": [], "inst": []}}
    n = 0
    for mix, voc, acc in load_clips(eval_root, split):
        n += 1
        runs = [("v1", v1_instrumental(v1, mix))]
        if v2 is not None:
            runs.append(("v2", dsp.separate(mix, v2)))  # the phone's 8 s / 1 s chunking
        for who, inst in runs:
            scores[who]["inst"].append(float(sdr(inst, acc)))
            scores[who]["vocal"].append(float(sdr(mix - inst, voc)))
    med = {who: {k: float(np.median(v)) for k, v in s.items()} if s["inst"] else None
           for who, s in scores.items()}
    return {"v1": med["v1"], "v2": med["v2"], "n": n,
            "gate_pass": bool(med["v2"] and gate(med["v1"]["inst"], med["v2"]["inst"]))}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--eval", required=True)
    ap.add_argument("--checkpoint", default=None)
    ap.add_argument("--split", default="test")
    ap.add_argument("--out", default=".")
    a = ap.parse_args()
    out = evaluate(a.eval, a.checkpoint, a.split)
    commit = "none"
    if a.checkpoint:
        commit = str(torch.load(a.checkpoint, map_location="cpu", weights_only=False).get("git_hash", "unknown"))
    out["checkpoint"], out["git_hash"] = a.checkpoint, commit
    print(f"clips: {out['n']}")
    print(f"{'':4} {'vocal SDR':>10} {'inst SDR':>10}")
    for who in ("v1", "v2"):
        if out[who]:
            print(f"{who:4} {out[who]['vocal']:10.2f} {out[who]['inst']:10.2f}")
    print(f"gate (v2 inst >= v1 inst + {GATE_DB} dB): {'PASS' if out['gate_pass'] else 'FAIL'}")
    path = os.path.join(a.out, f"eval_{commit[:7]}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=1)
    print(f"wrote {path}")


if __name__ == "__main__":
    main()
