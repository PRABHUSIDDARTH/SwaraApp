import io
import os
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
    assert r.label == "negative" and r.licence == "CC-BY-4.0" and r.speaker == "librispeech:84" and r.split == "val"
    assert audit(rows, str(tmp_path)) == []


def test_backgrounds_from_korokae_reject_own_licence(tmp_path):
    p = _korokae_csv(tmp_path)
    p.write_text(p.read_text().replace("CC-BY-4.0,Ann", "own,Ann", 1))
    assert fetch.backgrounds_from_korokae(str(p)) == []


def test_backgrounds_get_hashed_splits_covering_val_and_test(tmp_path):
    d = tmp_path / "acc"
    d.mkdir()
    lines = [f"{i}.flac,accompaniment,ccmixter,https://x/{i},A{i},CC-BY-4.0,A,g{i},train" for i in range(60)]
    (d / "manifest.csv").write_text("path,role,source,source_url,author,licence,attribution,group,split\n"
                                   + "\n".join(lines) + "\n")
    rows = fetch.backgrounds_from_korokae(str(d / "manifest.csv"))
    assert {r.split for r in rows} == {"train", "val", "test"}


def _uploads(urls, lic="http://creativecommons.org/licenses/by/3.0/"):
    return [{"upload_id": i, "upload_name": f"T{i}", "user_name": f"U{i}", "file_page_url": f"http://c/{i}",
             "license_url": lic, "files": [{"download_url": u}]} for i, u in enumerate(urls)]


def _mock_cc(tmp_path, urls):
    got = []
    rows = fetch.fetch_ccmixter(
        {"query_url": "http://ccmixter.org/q", "max_files": 50}, str(tmp_path),
        http_get=lambda u: json.dumps(_uploads(urls)).encode(),
        download=lambda url, dest: (got.append((url, dest)), open(dest, "wb").write(b"x")))
    return rows, got


def test_ccmixter_rejects_non_http_and_bad_extensions(tmp_path):
    rows, got = _mock_cc(tmp_path, ["file:///etc/passwd.mp3", "ftp://h/a.mp3", "http://h/a.exe",
                                    "http://h/a.mp3"])
    assert [u for u, _ in got] == ["http://h/a.mp3"] and len(rows) == 1


def test_ccmixter_same_basename_does_not_collide_and_stays_in_root(tmp_path):
    rows, got = _mock_cc(tmp_path, ["http://h/x/song.mp3", "http://h/y/song.mp3", "http://h/x/song.mp3"])
    assert len(rows) == 2 and len({d for _, d in got}) == 2
    root = os.path.realpath(str(tmp_path))
    assert all(os.path.realpath(d).startswith(root + os.sep) for _, d in got)
    assert all(".." not in r.path for r in rows)


def test_download_rejects_file_scheme(tmp_path):
    with pytest.raises(ValueError):
        fetch._download("file:///etc/passwd", str(tmp_path / "x.mp3"))


class _Resp:
    def __init__(self, n):
        self.left = n
    def __enter__(self): return self
    def __exit__(self, *a): return False
    def read(self, k):
        k = min(k, self.left)
        self.left -= k
        return b"x" * k


def test_download_enforces_size_cap_timeout_and_cleans_up(monkeypatch, tmp_path):
    seen = {}
    def fake_open(url, timeout=None):
        seen["timeout"] = timeout
        return _Resp(3 * 1024 * 1024)
    monkeypatch.setattr(fetch.urllib.request, "urlopen", fake_open)
    with pytest.raises(ValueError):
        fetch._download("https://h/a.mp3", str(tmp_path / "a.mp3"), max_bytes=1024 * 1024)
    assert seen["timeout"] and not list(tmp_path.iterdir())
    fetch._download("https://h/a.mp3", str(tmp_path / "a.mp3"), max_bytes=4 * 1024 * 1024)
    assert (tmp_path / "a.mp3").stat().st_size == 3 * 1024 * 1024


def test_fetch_main_validates_before_writing(monkeypatch, tmp_path):
    cfg = tmp_path / "c.yaml"
    c = yaml.safe_load(open("configs/base.yaml"))
    c["fetch"]["korokae_manifest_csv"] = ""
    cfg.write_text(yaml.safe_dump(c))
    bad = fetch.Row("a.flac", "negative", "librispeech", "", "x", "CC-BY-4.0", "a", "librispeech:1", "train")
    monkeypatch.setattr(fetch, "fetch_librispeech", lambda *a, **k: [bad])
    monkeypatch.setattr(fetch, "fetch_ccmixter", lambda *a, **k: [])
    with pytest.raises(fetch.ManifestError):
        fetch.main(["--config", str(cfg), "--root", str(tmp_path / "out")])
    assert not (tmp_path / "out" / "manifest.csv").exists()
