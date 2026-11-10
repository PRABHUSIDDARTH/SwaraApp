import importlib
import os
import sys

import numpy as np
import pytest
import soundfile as sf
import torch

from korokae_v2 import v1_baseline
from korokae_v2.data.manifest import Row, write_manifest
from korokae_v2.evaluate import evaluate, gate
from korokae_v2.render_eval import render
from korokae_v2.train import build_model

V1_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "korokae")


def _v1_export_module():
    sys.path.insert(0, os.path.abspath(V1_DIR))
    try:
        return importlib.import_module("export")
    finally:
        sys.path.pop(0)


def test_load_v1_round_trips_export_format(tmp_path, monkeypatch):
    exp = _v1_export_module()
    torch.manual_seed(0)
    net = exp.KorokaeNet()
    with torch.no_grad():
        net.mean.normal_()
        net.std.uniform_(0.5, 2.0)
    monkeypatch.setattr(exp, "ASSET", tmp_path / "v1.bin")
    exp.export_weights(net)
    got = v1_baseline.load_v1(str(tmp_path / "v1.bin"))
    want = net.state_dict()
    assert set(got.state_dict()) == set(want)
    assert all(torch.equal(got.state_dict()[k], want[k]) for k in want)


def test_load_v1_rejects_bad_magic(tmp_path):
    p = tmp_path / "bad.bin"
    p.write_bytes(b"NOPE" + bytes(64))
    with pytest.raises(ValueError):
        v1_baseline.load_v1(str(p))


def test_v1_instrumental_keeps_length():
    net = v1_baseline.load_v1()
    for n in (44100, 44100 + 123):
        assert v1_baseline.v1_instrumental(net, torch.randn(2, n) * 0.1).shape == (2, n)


def test_gate_uses_plus_5_db_on_instrumental():
    assert not gate(v1_inst=3.0, v2_inst=7.9)
    assert gate(v1_inst=3.0, v2_inst=8.0)


def test_evaluate_runs_end_to_end_on_tiny_rendered_set(tmp_path):
    root = tmp_path / "d"
    root.mkdir()
    rows = []
    t = np.arange(3 * 44100) / 44100
    for role, x in (("vocal", 0.3 * np.sin(2 * np.pi * 300 * t)),
                    ("accompaniment", 0.1 * np.random.default_rng(0).standard_normal(t.size))):
        sf.write(str(root / f"{role}.flac"), x, 44100, subtype="PCM_16")
        rows.append(Row(path=f"{role}.flac", role=role, source="t", source_url="u", author="a",
                        licence="CC0-1.0", attribution="a", group=role, split="test"))
    write_manifest(rows, str(root / "manifest.csv"))
    render(str(root / "manifest.csv"), str(root), "test", 2, 1.0, 2002, str(tmp_path / "e"))
    cfg = {"model": {"n_blocks": 1, "d": 16, "heads": 2}}
    torch.save({"model": build_model(cfg["model"]).state_dict(), "cfg": cfg, "git_hash": "abc1234"},
               str(tmp_path / "best.pt"))
    out = evaluate(str(tmp_path / "e"), str(tmp_path / "best.pt"))
    assert out["n"] == 2 and isinstance(out["gate_pass"], bool)
    for who in ("v1", "v2"):
        assert set(out[who]) == {"vocal", "inst"} and all(np.isfinite(list(out[who].values())))
