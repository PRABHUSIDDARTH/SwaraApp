"""ONNX export (opset 17) + wakeword.json. Refuses to export a model that did not pass the gate.

    python -m wakeword.export --ckpt best.pt --out ../../app/src/main/assets/voice/wakeword/
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import torch

from wakeword.evaluate import CONSECUTIVE, HOP_SAMPLES, REFRACTORY_MS, GateResult, load_model
from wakeword.model import WINDOW, WakeNet


def export_onnx(model: WakeNet, path: str) -> None:
    model = model.eval().cpu()
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    dummy = torch.zeros(1, WINDOW)
    torch.onnx.export(model, (dummy,), path, input_names=["audio"], output_names=["prob"],
                      dynamic_axes={"audio": {0: "batch"}, "prob": {0: "batch"}},
                      opset_version=17, dynamo=False)
    _shrink_large_constants(path)


def _shrink_large_constants(path: str, min_elems: int = 10000) -> None:
    """Store big fp32 Constant tensors (the STFT basis, already fp16-exact) as fp16 initializers
    followed by a Cast to fp32: halves their size with identical values."""
    import numpy as np
    import onnx
    from onnx import TensorProto, helper, numpy_helper
    m = onnx.load(path)
    new_nodes = []
    for n in m.graph.node:
        t = n.attribute[0].t if n.op_type == "Constant" and n.attribute and n.attribute[0].name == "value" else None
        if t is not None and t.data_type == TensorProto.FLOAT and int(np.prod(t.dims)) >= min_elems:
            arr = numpy_helper.to_array(t)
            half = arr.astype(np.float16)
            if not np.array_equal(half.astype(np.float32), arr):
                new_nodes.append(n)  # not fp16-exact: keep as is
                continue
            name = n.output[0] + "_fp16"
            m.graph.initializer.append(numpy_helper.from_array(half, name))
            new_nodes.append(helper.make_node("Cast", [name], list(n.output), to=TensorProto.FLOAT))
        else:
            new_nodes.append(n)
    del m.graph.node[:]
    m.graph.node.extend(new_nodes)
    onnx.save(m, path)


def write_config(path: str, res: GateResult) -> None:
    cfg = {"threshold": float(res.threshold), "consecutive_hops": CONSECUTIVE,
           "refractory_ms": REFRACTORY_MS, "window": WINDOW, "hop": HOP_SAMPLES,
           "detection_rate": float(res.detection_rate),
           "false_triggers_per_hour": float(res.fa_per_hour),
           "negative_hours": float(res.negative_hours)}
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
