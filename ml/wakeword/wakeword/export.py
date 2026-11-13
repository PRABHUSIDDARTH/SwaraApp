"""ONNX export (opset 17) + wakeword.json. Refuses to export a model that did not pass the gate.

    python -m wakeword.export --ckpt best.pt --out ../../app/src/main/assets/voice/wakeword/
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import torch

from wakeword.evaluate import GateResult, load_model
from wakeword.model import HOP, WINDOW, WakeNet

CONSECUTIVE_HOPS = 2
REFRACTORY_MS = 2000
HOP_SAMPLES = 1280  # 80 ms at 16 kHz


def export_onnx(model: WakeNet, path: str) -> None:
    model = model.eval().cpu()
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    dummy = torch.zeros(1, WINDOW)
    torch.onnx.export(model, (dummy,), path, input_names=["audio"], output_names=["prob"],
                      dynamic_axes={"audio": {0: "batch"}, "prob": {0: "batch"}},
                      opset_version=17, dynamo=False)


def write_config(path: str, res: GateResult) -> None:
    cfg = {"threshold": float(res.threshold), "consecutive_hops": CONSECUTIVE_HOPS,
           "refractory_ms": REFRACTORY_MS, "window": WINDOW, "hop": HOP_SAMPLES,
           "detection_rate": float(res.detection_rate),
           "false_triggers_per_hour": float(res.fa_per_hour)}
    with open(path, "w") as f:
        json.dump(cfg, f, indent=2)
        f.write("\n")


def main(argv: list[str] | None = None) -> None:
    from wakeword.evaluate import run_gate_cli
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--config", default="configs/base.yaml")
    ap.add_argument("--eval", action="append", default=None,
                    help="external eval dir(s) with manifest.csv (all rows); default: test split of the data roots")
    args = ap.parse_args(argv)
    res = run_gate_cli(args.ckpt, args.config, args.eval)
    if not res.passed:
        print("gate FAILED; not exporting. Report these numbers to the owner.")
        sys.exit(1)
    os.makedirs(args.out, exist_ok=True)
    export_onnx(load_model(args.ckpt), os.path.join(args.out, "wakeword.onnx"))
    write_config(os.path.join(args.out, "wakeword.json"), res)
    print(f"exported to {args.out}")


if __name__ == "__main__":
    main()
