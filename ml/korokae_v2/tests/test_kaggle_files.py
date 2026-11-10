import glob
import json
import os
import re
import subprocess

KAGGLE = os.path.join(os.path.dirname(__file__), "..", "kaggle")
USER = "prabhusiddarthav"


def _meta(name):
    with open(os.path.join(KAGGLE, name, "kernel-metadata.json")) as f:
        return json.load(f)


def test_kaggle_metadata_is_valid():
    for name in ("prep_vocals", "prep_accomp"):
        m = _meta(name)
        assert m["enable_gpu"] is False and m["enable_internet"] is True and m["is_private"] is True
        assert m["id"] == f"{USER}/korokae-{name.replace('_', '-')}" and m["code_file"] == "run.py"
    t = _meta("train")
    assert t["enable_gpu"] is True and t["enable_internet"] is True and t["is_private"] is True
    assert t["dataset_sources"] == [f"{USER}/korokae-ckpt"]
    assert sorted(t["kernel_sources"]) == [f"{USER}/korokae-prep-accomp", f"{USER}/korokae-prep-vocals"]


def test_every_run_py_pins_a_commit():
    files = glob.glob(os.path.join(KAGGLE, "*", "run.py"))
    assert len(files) == 3
    for p in files:
        assert re.search(r'^COMMIT = "[0-9a-f]{40}"$', open(p).read(), re.M), p


def test_cycle_script_is_strict_bash():
    p = os.path.join(KAGGLE, "kaggle_cycle.sh")
    assert "set -euo pipefail" in open(p).read()
    assert subprocess.run(["bash", "-n", p]).returncode == 0


def test_train_session_merges_inputs_renders_eval_and_resumes(tmp_path, monkeypatch):
    import numpy as np
    import soundfile as sf

    from korokae_v2 import kaggle_entry
    from korokae_v2.data.manifest import Row, write_manifest

    inp, work = tmp_path / "input", tmp_path / "working"
    sr, t = 44100, np.arange(3 * 44100) / 44100
    for slug, role, x in (("korokae-prep-vocals", "vocal", 0.3 * np.sin(2 * np.pi * 300 * t)),
                          ("korokae-prep-accomp", "accompaniment", 0.1 * np.cos(2 * np.pi * 90 * t))):
        root = inp / slug / "data"
        root.mkdir(parents=True)
        rows = []
        for split in ("train", "val", "test"):
            sf.write(str(root / f"{split}.flac"), x, sr, subtype="PCM_16")
            rows.append(Row(path=f"{split}.flac", role=role, source="t", source_url="u", author="a",
                            licence="CC0-1.0", attribution="a", group=split, split=split))
        write_manifest(rows, str(root / "manifest.csv"))
    (inp / "korokae-ckpt").mkdir()
    (inp / "korokae-ckpt" / kaggle_entry.CKPT_MARKER).write_text("x")
    monkeypatch.setattr(kaggle_entry, "INPUT", str(inp))
    monkeypatch.setattr(kaggle_entry, "WORKING", str(work))
    monkeypatch.setattr(kaggle_entry, "VAL", dict(split="val", n_clips=1, seconds=1.0, seed=1001))
    monkeypatch.setattr(kaggle_entry, "TEST", dict(split="test", n_clips=1, seconds=1.0, seed=2002))
    smoke = os.path.join(os.path.dirname(__file__), "..", "configs", "smoke.yaml")
    out = kaggle_entry.train_session(smoke, 60)
    assert out["step"] == 20 and (work / "eval" / "test" / "0000_mix.flac").exists()
    # next session: previous checkpoints arrive through the ckpt dataset and training resumes
    for p in (work / "ckpt").iterdir():
        if p.suffix == ".pt" or p.name == "log.jsonl":
            (inp / "korokae-ckpt" / p.name).write_bytes(p.read_bytes())
    import shutil
    shutil.rmtree(work)
    out2 = kaggle_entry.train_session(smoke, 60)
    assert out2["step"] == 20
    starts = [json.loads(l) for l in open(work / "ckpt" / "log.jsonl") if '"start"' in l]
    assert starts[-1]["resumed_from"] == 20
