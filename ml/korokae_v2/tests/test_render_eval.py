import json

import numpy as np
import soundfile as sf

from korokae_v2.data.manifest import Row, write_manifest
from korokae_v2.render_eval import load_clips, render

SR = 44100


def _data(root):
    rows = []
    for split in ("train", "val", "test"):
        for role, f in (("vocal", 300), ("accompaniment", 110)):
            for k in range(2):
                rel = f"{split}_{role}_{k}.flac"
                t = np.arange(4 * SR) / SR
                sf.write(str(root / rel), 0.3 * np.sin(2 * np.pi * (f + 40 * k) * t), SR, subtype="PCM_16")
                rows.append(Row(path=rel, role=role, source="t", source_url="u", author="a",
                                licence="CC0-1.0", attribution="a", group=rel, split=split))
    write_manifest(rows, str(root / "manifest.csv"))
    return str(root / "manifest.csv")


def _same_audio(p, q):
    return np.array_equal(sf.read(str(p))[0], sf.read(str(q))[0])


def test_render_is_sample_identical_for_same_seed(tmp_path):
    # FLAC container bytes may differ between encodes; the decoded samples must not
    m = _data(tmp_path)
    for name in ("a", "b"):
        render(m, str(tmp_path), "test", 3, 2.0, 2002, str(tmp_path / name))
    for f in sorted((tmp_path / "a" / "test").glob("*.flac")):
        assert _same_audio(f, tmp_path / "b" / "test" / f.name), f.name
    assert (tmp_path / "a/test/meta.json").read_bytes() == (tmp_path / "b/test/meta.json").read_bytes()


def test_clip_i_does_not_depend_on_clip_count(tmp_path):
    m = _data(tmp_path)
    render(m, str(tmp_path), "val", 2, 2.0, 7, str(tmp_path / "two"))
    render(m, str(tmp_path), "val", 4, 2.0, 7, str(tmp_path / "four"))
    for name in ("0001_mix.flac", "0001_vocals.flac"):
        assert _same_audio(tmp_path / "two/val" / name, tmp_path / "four/val" / name)


def test_rendered_mix_equals_vocals_plus_accomp(tmp_path):
    m = _data(tmp_path)
    render(m, str(tmp_path), "test", 3, 2.0, 2002, str(tmp_path / "o"))
    clips = list(load_clips(str(tmp_path / "o"), "test"))
    assert len(clips) == 3
    for mix, v, a in clips:
        assert mix.shape == (2, 2 * SR)
        assert (mix - v - a).abs().max() < 1e-6
        assert mix.abs().max() < 1.0


def test_meta_records_manifest_hash_and_seed(tmp_path):
    m = _data(tmp_path)
    render(m, str(tmp_path), "test", 2, 2.0, 2002, str(tmp_path / "o"))
    meta = json.loads((tmp_path / "o" / "test" / "meta.json").read_text())
    assert meta["seed"] == 2002 and meta["n_clips"] == 2 and meta["seconds"] == 2.0
    assert len(meta["manifest_sha1"]) == 40 and len(meta["clips"]) == 2


def test_render_uses_only_requested_split(tmp_path):
    m = _data(tmp_path)
    render(m, str(tmp_path), "val", 4, 2.0, 1, str(tmp_path / "o"))
    meta = json.loads((tmp_path / "o" / "val" / "meta.json").read_text())
    used = {p for c in meta["clips"] for p in (c["vocal"], c["accompaniment"])}
    assert used and all(p.startswith("val_") for p in used)
