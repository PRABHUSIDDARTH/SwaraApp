import io
import json
import tarfile

import numpy as np
import pytest
import soundfile as sf
import yaml

from wakeword.data import fetch
from wakeword.data.manifest import Row, audit, write_manifest

with open("configs/base.yaml") as f:
    CFG = yaml.safe_load(f)["fetch"]


def test_plan_passes_licence_gate_for_librispeech():
    items = fetch.plan(CFG)
    ls = [i for i in items if i.source == "librispeech"]
    assert {i.name for i in ls} == {"train-clean-100", "dev-clean", "test-clean"}
    assert all(i.licence == "CC-BY-4.0" and i.allowed for i in ls)


def test_gate_blocks_noncommercial_source():
    bad = json.loads(json.dumps(CFG))
    bad["librispeech"]["licence"] = "CC BY-NC 4.0"
    with pytest.raises(fetch.LicenceError):
        fetch.plan(bad)


def test_dry_run_prints_and_never_touches_network(monkeypatch, capsys, tmp_path):
    def boom(*a, **k):
        raise AssertionError("network used")
    monkeypatch.setattr(fetch, "_http_get", boom)
    monkeypatch.setattr(fetch, "_download", boom)
    rc = fetch.main(["--dry-run", "--config", "configs/base.yaml", "--root", str(tmp_path)])
    out = capsys.readouterr().out
    assert rc == 0
    assert "train-clean-100" in out and "CC-BY-4.0" in out and "dry run" in out.lower()
    assert list(tmp_path.iterdir()) == []


def _korokae_csv(tmp_path):
    d = tmp_path / "acc"
    d.mkdir()
    (d / "a.flac").write_bytes(b"x")
    (d / "b.flac").write_bytes(b"x")
    (d / "v.flac").write_bytes(b"x")
    hdr = "path,role,source,source_url,author,licence,attribution,group,split\n"
    rows = [
        "a.flac,accompaniment,ccmixter,https://x/a,Ann,CC-BY-4.0,Ann,g1,train",
        "b.flac,accompaniment,ccmixter,https://x/b,Bo,CC-BY-NC-4.0,Bo,g2,train",
        "v.flac,vocal,vocalset,https://x/v,Cy,CC-BY-4.0,Cy,g3,train",
    ]
    (d / "manifest.csv").write_text(hdr + "\n".join(rows) + "\n")
    return d / "manifest.csv"


def test_backgrounds_from_korokae_manifest_filters_role_and_licence(tmp_path):
    rows = fetch.backgrounds_from_korokae(str(_korokae_csv(tmp_path)))
    assert [r.path.endswith("a.flac") for r in rows] == [True]
    r = rows[0]
    assert r.label == "background" and r.licence == "CC-BY-4.0" and r.consent == "n/a"
    assert r.path.startswith(str(tmp_path))


def test_ccmixter_rows_use_gate_with_mocked_network(tmp_path):
    listing = [
        {"upload_name": "Good", "user_name": "Ann", "file_page_url": "http://ccmixter.org/files/a/1",
         "license_url": "http://creativecommons.org/licenses/by/3.0/",
         "files": [{"download_url": "http://ccmixter.org/content/a/1.mp3"}]},
        {"upload_name": "NC", "user_name": "Bo", "file_page_url": "http://ccmixter.org/files/b/2",
         "license_url": "http://creativecommons.org/licenses/by-nc/3.0/",
         "files": [{"download_url": "http://ccmixter.org/content/b/2.mp3"}]},
    ]
    downloaded = []
    rows = fetch.fetch_ccmixter(
        CFG["ccmixter"], str(tmp_path),
        http_get=lambda url: json.dumps(listing).encode(),
        download=lambda url, dest: (downloaded.append(url), open(dest, "wb").write(b"x")))
    assert downloaded == ["http://ccmixter.org/content/a/1.mp3"]
    assert len(rows) == 1 and rows[0].licence == "CC-BY-3.0" and rows[0].author == "Ann"
    assert rows[0].label == "background" and rows[0].source == "ccmixter"
    assert audit(rows, str(tmp_path)) == []


def test_librispeech_rows_from_archive_with_mocked_download(tmp_path):
    def fake_download(url, dest):
        buf = io.BytesIO()
        wav = io.BytesIO()
        sf.write(wav, np.zeros(1600, np.float32), 16000, format="FLAC")
        with tarfile.open(dest, "w:gz") as tf:
            info = tarfile.TarInfo("LibriSpeech/dev-clean/84/121123/84-121123-0000.flac")
            info.size = len(wav.getvalue())
            tf.addfile(info, io.BytesIO(wav.getvalue()))
    rows = fetch.fetch_librispeech(CFG["librispeech"], str(tmp_path), download=fake_download,
                                   only=["dev-clean"])
    assert len(rows) == 1
    r = rows[0]
    assert r.label == "negative" and r.licence == "CC-BY-4.0" and r.speaker == "84" and r.split == "val"
    assert audit(rows, str(tmp_path)) == []
