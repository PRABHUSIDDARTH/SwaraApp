import json
import os

import numpy as np
import onnxruntime as ort
import torch

from wakeword.evaluate import GateResult
from wakeword.export import export_onnx, write_config
from wakeword.model import WakeNet


def _model():
    torch.manual_seed(0)
    return WakeNet().eval()


def test_onnx_matches_torch_1e4(tmp_path):
    m = _model()
    path = str(tmp_path / "wakeword.onnx")
    export_onnx(m, path)
    assert ort.__version__.startswith("1.31")
    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    assert sess.get_inputs()[0].name == "audio" and sess.get_outputs()[0].name == "prob"
    for batch in (1, 5):  # dynamic batch axis
        x = (torch.randn(batch, 16000) * 0.2).clamp(-1, 1)
        with torch.no_grad():
            ref = m(x).numpy()
        out = sess.run(["prob"], {"audio": x.numpy()})[0]
        assert out.shape == (batch,) and out.dtype == np.float32
        assert float(np.abs(out - ref).max()) < 1e-4


def test_onnx_opset_17(tmp_path):
    import onnx
    path = str(tmp_path / "w.onnx")
    export_onnx(_model(), path)
    assert onnx.load(path).opset_import[0].version == 17


def test_onnx_size_le_1mb(tmp_path):
    path = str(tmp_path / "w.onnx")
    export_onnx(_model(), path)
    assert os.path.getsize(path) <= 1_000_000


def test_wakeword_json(tmp_path):
    path = str(tmp_path / "wakeword.json")
    write_config(path, GateResult(detection_rate=0.97, fa_per_hour=0.05, threshold=0.8, passed=True,
                                    negative_hours=12.5))
    assert json.load(open(path)) == {
        "threshold": 0.8, "consecutive_hops": 2, "refractory_ms": 2000, "window": 16000,
        "hop": 1280, "detection_rate": 0.97, "false_triggers_per_hour": 0.05,
        "negative_hours": 12.5}


def test_onnx_latency_smoke(tmp_path):
    import time
    path = str(tmp_path / "w.onnx")
    export_onnx(_model(), path)
    so = ort.SessionOptions()
    so.intra_op_num_threads = 1
    sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
    x = {"audio": (np.random.randn(1, 16000) * 0.1).astype(np.float32)}
    for _ in range(5):
        sess.run(None, x)
    n = 50
    t0 = time.perf_counter()
    for _ in range(n):
        sess.run(None, x)
    ms = (time.perf_counter() - t0) / n * 1000
    print(f"ORT single-thread mean latency: {ms:.2f} ms/call")
    assert ms < 20
