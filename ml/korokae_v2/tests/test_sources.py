import io
import json
import subprocess
import tarfile
import urllib.error
import zipfile

import pytest

from korokae_v2.data import build, sources
from korokae_v2.data.manifest import audit, read_manifest


def _wav_bytes(tmp_path, seconds=1.0, channels=1, name="t.wav"):
    p = tmp_path / name
    subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", "-f", "lavfi", "-i",
                    f"sine=f=330:r=44100:d={seconds}", "-ac", str(channels), str(p)], check=True)
    return p.read_bytes()


def _flac_bytes(tmp_path, seconds=1.0):
    p = tmp_path / "m.flac"
    subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", "-f", "lavfi", "-i",
                    f"sine=f=220:r=44100:d={seconds}", str(p)], check=True)
    return p.read_bytes()


def _zip(entries):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for name, data in entries.items():
            z.writestr(name, data)
    return buf.getvalue()


class FakeHttp:
    def __init__(self, json_map=None, blobs=None):
        self.json_map, self.blobs, self.calls = json_map or {}, blobs or {}, []

    def get_json(self, url):
        self.calls.append(url)
        v = self.json_map[url]
        return v() if callable(v) else v

    def download(self, url, dst):
        self.calls.append(url)
        v = self.blobs[url]
        if isinstance(v, Exception):
            raise v
        with open(dst, "wb") as f:
            f.write(v)

    def stream(self, url):
        return io.BytesIO(self.blobs[url])


def _zenodo(record, licence, key):
    url = f"https://zenodo.org/api/records/{record}/files/{key}/content"
    return url, {"metadata": {"license": {"id": licence}}, "files": [{"key": key, "links": {"self": url}}]}


def test_vocalset_rows_have_cc_by_singer_groups_and_record_url(tmp_path):
    wav = _wav_bytes(tmp_path)
    url, rec = _zenodo(1203819, "cc-by-4.0", "VocalSet11.zip")
    blob = _zip({"FULL/female1/arpeggios/belt/f1_a.wav": wav, "FULL/male2/scales/m2_b.wav": wav,
                 "FULL/female1/.DS_Store": b"x", "__MACOSX/FULL/female1/._f1_a.wav": b"x"})
    http = FakeHttp({sources.ZENODO_API + "1203819": rec}, {url: blob})
    rows = list(sources.fetch_vocalset(str(tmp_path / "w"), str(tmp_path / "o"), http, 1e9))
    assert sorted(r.group for r in rows) == ["female1", "male2"]
    assert {r.licence for r in rows} == {"CC-BY-4.0"} and {r.role for r in rows} == {"vocal"}
    assert {r.source_url for r in rows} == {"https://zenodo.org/records/1203819"}
    assert all((tmp_path / "o" / r.path).is_file() for r in rows)


def test_vocadito_groups_by_singer_id(tmp_path):
    wav = _wav_bytes(tmp_path)
    url, rec = _zenodo(5578807, "cc-by-4.0", "vocadito.zip")
    meta = "track_id,singer_id,average_pitch,language\n1,S1,50,Tagalog\n3,S1,52,Tagalog\n2,S2,65,Spanish\n"
    blob = _zip({"Audio/vocadito_1.wav": wav, "Audio/vocadito_2.wav": wav, "Audio/vocadito_3.wav": wav,
                 "vocadito_metadata.csv": meta, "__MACOSX/Audio/._vocadito_1.wav": b"x"})
    http = FakeHttp({sources.ZENODO_API + "5578807": rec}, {url: blob})
    rows = list(sources.fetch_vocadito(str(tmp_path / "w"), str(tmp_path / "o"), http, 1e9))
    assert sorted(r.group for r in rows) == ["S1", "S1", "S2"]


def test_zenodo_record_with_non_allowed_licence_raises(tmp_path):
    url, rec = _zenodo(1203819, "cc-by-nc-4.0", "VocalSet11.zip")
    http = FakeHttp({sources.ZENODO_API + "1203819": rec})
    with pytest.raises(sources.LicenceError):
        list(sources.fetch_vocalset(str(tmp_path / "w"), str(tmp_path / "o"), http, 1e9))


def _cc_record(i, lic, tags=",acappella,", ext="mp3"):
    return {"upload_id": i, "upload_name": f"Song {i}", "user_name": f"user{i}",
            "license_url": lic, "upload_tags": tags, "file_page_url": f"https://ccmixter.org/files/user{i}/{i}",
            "files": [{"download_url": f"https://ccmixter.org/content/u{i}.{ext}", "file_name": f"u{i}.{ext}",
                       "file_format_info": {"default-ext": ext}}]}


def _cc_pages(pages):
    return {sources.ccmixter_page_url(i * sources.CCMIXTER_PAGE): p for i, p in enumerate(pages)}


def test_ccmixter_rejects_ai_and_nc(tmp_path):
    recs = [_cc_record(1, "https://creativecommons.org/licenses/by/4.0/"),
            _cc_record(2, "https://creativecommons.org/licenses/by-nc/4.0/"),
            _cc_record(3, "https://creativecommons.org/licenses/by/4.0/", ",acappella,ia_stems_generated,"),
            _cc_record(4, "https://creativecommons.org/publicdomain/zero/1.0/", ",AI_assisted,acappella,")]
    wav = _wav_bytes(tmp_path, channels=2)
    http = FakeHttp(_cc_pages([recs, [], [], []]),
                    {r["files"][0]["download_url"]: wav for r in recs})
    rows = list(sources.fetch_ccmixter(str(tmp_path / "w"), str(tmp_path / "o"), http, 1e9, pause=0))
    assert len(rows) == 1
    r = rows[0]
    assert (r.licence, r.author, r.source_url) == ("CC-BY-4.0", "user1", "https://creativecommons.org/licenses/by/4.0/")
    assert "Song 1" in r.attribution and "https://ccmixter.org/files/user1/1" in r.attribution
    reasons = sources.ccmixter_reasons(recs)
    assert reasons == {"accepted": 1, "licence": 1, "ai_tag": 2}


def test_ccmixter_paging_survives_empty_pages_and_errors(tmp_path):
    rec = _cc_record(5, "https://creativecommons.org/licenses/by/3.0/")
    pages = _cc_pages([[], [], [rec], [], [], []])
    first = sources.ccmixter_page_url(0)
    pages[first] = _Flaky([OSError("rate limited"), OSError("again")], [])
    wav = _wav_bytes(tmp_path)
    http = FakeHttp(pages, {rec["files"][0]["download_url"]: wav})
    rows = list(sources.fetch_ccmixter(str(tmp_path / "w"), str(tmp_path / "o"), http, 1e9, pause=0))
    assert [r.licence for r in rows] == ["CC-BY-3.0"]


def _forbidden(url):
    return urllib.error.HTTPError(url, 403, "Forbidden", {}, None)


def test_ccmixter_skips_a_download_that_fails(tmp_path):
    recs = [_cc_record(i, "https://creativecommons.org/licenses/by/4.0/") for i in (1, 2)]
    wav = _wav_bytes(tmp_path)
    bad = recs[0]["files"][0]["download_url"]
    http = FakeHttp(_cc_pages([recs, [], [], []]),
                    {bad: _forbidden(bad), recs[1]["files"][0]["download_url"]: wav})
    rows = list(sources.fetch_ccmixter(str(tmp_path / "w"), str(tmp_path / "o"), http, 1e9, pause=0))
    assert [r.author for r in rows] == ["user2"]


def test_ccmixter_gives_up_after_consecutive_download_failures(tmp_path):
    n = sources.CCMIXTER_MAX_FAILED_DOWNLOADS
    recs = [_cc_record(i, "https://creativecommons.org/licenses/by/4.0/") for i in range(n + 8)]
    pages = [recs[i:i + sources.CCMIXTER_PAGE] for i in range(0, len(recs), sources.CCMIXTER_PAGE)]
    urls = [r["files"][0]["download_url"] for r in recs]
    http = FakeHttp(_cc_pages(pages + [[], [], []]), {u: _forbidden(u) for u in urls})
    rows = list(sources.fetch_ccmixter(str(tmp_path / "w"), str(tmp_path / "o"), http, 1e9, pause=0))
    assert rows == []
    assert sum(c in urls for c in http.calls) == n


class _Flaky:
    def __init__(self, errors, value):
        self.errors, self.value = list(errors), value

    def __call__(self):
        if self.errors:
            raise self.errors.pop(0)
        return self.value


def _slakh_tar(tmp_path, tracks):
    flac = _flac_bytes(tmp_path, seconds=2.0)
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as t:
        for split, tid in tracks:
            for name in ("mix.flac", "stems/S00.flac"):
                data = flac
                info = tarfile.TarInfo(f"slakh2100_flac_redux/{split}/{tid}/{name}")
                info.size = len(data)
                t.addfile(info, io.BytesIO(data))
    return buf.getvalue()


def test_slakh_stream_keeps_only_mix_and_respects_budget(tmp_path):
    url, rec = _zenodo(4599666, "cc-by-4.0", "slakh2100_flac_redux.tar.gz")
    blob = _slakh_tar(tmp_path, [("train", "Track00001"), ("train", "Track00002"), ("train", "Track00003"),
                                 ("validation", "Track01501"), ("omitted", "Track00009")])
    http = FakeHttp({sources.ZENODO_API + "4599666": rec}, {url: blob})
    rows = list(sources.fetch_slakh(str(tmp_path / "w"), str(tmp_path / "o"), http, budget_s=3.0))
    assert sorted((r.split, r.group) for r in rows) == [("train", "Track00001"), ("train", "Track00002"),
                                                        ("val", "Track01501")]
    assert all(r.path.endswith("mix.flac") and r.role == "accompaniment" for r in rows)


def test_build_writes_manifest_that_passes_audit(tmp_path, monkeypatch):
    wav = _wav_bytes(tmp_path)
    url, rec = _zenodo(1203819, "cc-by-4.0", "VocalSet11.zip")
    blob = _zip({f"FULL/{s}/x/{s}_a.wav": wav for s in ("female1", "female2", "male1", "male2")})
    http = FakeHttp({sources.ZENODO_API + "1203819": rec}, {url: blob})
    out = tmp_path / "out"
    build.build(["vocalset"], str(out), str(tmp_path / "w"), budget_hours=1.0, http=http)
    rows = read_manifest(str(out / "manifest.csv"))
    assert len(rows) == 4 and audit(rows, str(out)) == []
    assert {"train", "val", "test"} == {r.split for r in rows}


def test_ccmixter_bad_page_falls_back_to_single_records(tmp_path):
    import http.client
    good = [_cc_record(10 + i, "https://creativecommons.org/licenses/by/4.0/") for i in range(sources.CCMIXTER_PAGE)]
    broken = _Flaky([http.client.LineTooLong("header line")] * 99, [])
    pages = {sources.ccmixter_page_url(0): broken}
    for i, rec in enumerate(good):
        single = sources.ccmixter_page_url(i, limit=1)
        pages[single] = _Flaky([http.client.LineTooLong("x")] * 99, []) if i == 1 else [rec]
    for k in (1, 2, 3):
        pages[sources.ccmixter_page_url(k * sources.CCMIXTER_PAGE)] = []
    recs = list(sources.ccmixter_records(FakeHttp(pages), pause=0))
    assert [r.get("upload_id") for r in recs if "_error" not in r] == [10, 12, 13]
    assert sources.ccmixter_reasons(recs)["fetch_error"] == 1


def test_slakh_stops_at_byte_cap(tmp_path):
    url, rec = _zenodo(4599666, "cc-by-4.0", "slakh2100_flac_redux.tar.gz")
    blob = _slakh_tar(tmp_path, [("train", "Track00001"), ("train", "Track00002"), ("validation", "Track01501")])
    http = FakeHttp({sources.ZENODO_API + "4599666": rec}, {url: blob})
    rows = list(sources.fetch_slakh(str(tmp_path / "w"), str(tmp_path / "o"), http, budget_s=1e9, max_bytes=1))
    assert len(rows) == 1
