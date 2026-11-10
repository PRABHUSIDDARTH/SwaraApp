import json
import os

import numpy as np
import pytest
import soundfile as sf
import torch

from korokae_v2.data.manifest import Row, write_manifest
from korokae_v2.render_eval import render
from korokae_v2.train import load_config, train

SR = 44100


@pytest.fixture()
def data(tmp_path):
    root = tmp_path / "data"
    root.mkdir()
    rng = np.random.default_rng(0)
    rows = []
    for split in ("train", "val"):
        for k in range(2):
            t = np.arange(3 * SR) / SR
            voc = 0.3 * np.sin(2 * np.pi * (300 + 50 * k) * t) * (np.sin(2 * np.pi * 2 * t) > 0)
            acc = 0.2 * rng.standard_normal(t.size)
            for role, x in (("vocal", voc), ("accompaniment", acc)):
                rel = f"{split}_{role}_{k}.flac"
                sf.write(str(root / rel), x, SR, subtype="PCM_16")
                rows.append(Row(path=rel, role=role, source="t", source_url="u", author="a",
                                licence="CC0-1.0", attribution="a", group=rel, split=split))
    write_manifest(rows, str(root / "manifest.csv"))
    ev = tmp_path / "eval"
    render(str(root / "manifest.csv"), str(root), "val", 2, 1.0, 1001, str(ev))
    return str(root), str(ev)


def _cfg(**over):
    cfg = load_config(os.path.join(os.path.dirname(__file__), "..", "configs", "smoke.yaml"))
    cfg.update(over)
    return cfg


def _log(ckpt):
    with open(os.path.join(ckpt, "log.jsonl")) as f:
        return [json.loads(l) for l in f]


def test_smoke_training_reduces_loss(data, tmp_path):
    root, ev = data
    out = train(_cfg(total_steps=40, val_every=0), root, None, str(tmp_path / "c"), 60)
    assert out["stopped"] == "done" and out["step"] == 40
    losses = [e["loss"] for e in _log(str(tmp_path / "c")) if "loss" in e]
    assert np.mean(losses[-5:]) < np.mean(losses[:5])


def test_resume_is_exact(data, tmp_path):
    root, _ = data
    cfg = _cfg(total_steps=10, val_every=0, ckpt_every_minutes=0)
    train(cfg, root, None, str(tmp_path / "a"), 60)
    first = train(cfg, root, None, str(tmp_path / "b"), 60, max_steps=6)
    assert first["stopped"] == "steps" and first["step"] == 6
    train(cfg, root, None, str(tmp_path / "b"), 60)
    a = torch.load(str(tmp_path / "a" / "step_00000010.pt"), weights_only=False)["model"]
    b = torch.load(str(tmp_path / "b" / "step_00000010.pt"), weights_only=False)["model"]
    assert all(torch.equal(a[k], b[k]) for k in a)


def test_time_budget_stops_with_checkpoint(data, tmp_path):
    root, _ = data
    out = train(_cfg(total_steps=1000, val_every=0), root, None, str(tmp_path / "c"), 1e-6)
    assert out["stopped"] == "time" and out["step"] >= 1
    assert any(n.startswith("step_") for n in os.listdir(tmp_path / "c"))


def test_partial_checkpoint_is_ignored(data, tmp_path):
    root, _ = data
    c = tmp_path / "c"
    train(_cfg(total_steps=4, val_every=0, ckpt_every_minutes=0, keep_last=10), root, None, str(c), 60)
    (c / "step_00000004.pt").write_bytes(b"truncated")
    (c / "step_00000005.pt.tmp").write_bytes(b"half written")
    out = train(_cfg(total_steps=5, val_every=0, ckpt_every_minutes=0, keep_last=10), root, None, str(c), 60)
    assert out["step"] == 5
    starts = [e for e in _log(str(c)) if e.get("event") == "start"]
    assert starts[-1]["resumed_from"] == 3


def test_best_tracks_val_inst_sdr_and_log_has_git_hash(data, tmp_path):
    root, ev = data
    c = tmp_path / "c"
    out = train(_cfg(total_steps=6, val_every=3), root, ev, str(c), 60)
    vals = [e for e in _log(str(c)) if "val_inst_sdr" in e]
    assert [e["step"] for e in vals] == [3, 6]
    assert out["best_val_inst_sdr"] == max(e["val_inst_sdr"] for e in vals)
    best = torch.load(str(c / "best.pt"), weights_only=False)
    assert best["best"] == out["best_val_inst_sdr"] and len(best["git_hash"]) >= 7
    assert len(_log(str(c))[0]["git_hash"]) >= 7


def test_keep_last_prunes_old_checkpoints(data, tmp_path):
    root, _ = data
    c = tmp_path / "c"
    train(_cfg(total_steps=6, val_every=0, ckpt_every_minutes=0, keep_last=2), root, None, str(c), 60)
    assert sorted(n for n in os.listdir(c) if n.startswith("step_")) == ["step_00000005.pt", "step_00000006.pt"]
