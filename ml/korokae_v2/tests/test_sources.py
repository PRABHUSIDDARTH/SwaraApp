import io
import json
import os
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


# ---------------------------------------------------------------- JaCRC, AdoVoc Pro, AlbumDB

def _np_wav(tmp_path, data, name="np.wav"):
    import numpy as np
    import soundfile as sf
    p = tmp_path / name
    sf.write(str(p), np.asarray(data, dtype="float32"), 44100, subtype="FLOAT")
    return p.read_bytes()


def _zenodo_multi(record, licence, keys):
    urls = {k: f"https://zenodo.org/api/records/{record}/files/{k}/content" for k in keys}
    rec = {"metadata": {"license": {"id": licence}},
           "files": [{"key": k, "links": {"self": u}} for k, u in urls.items()]}
    return urls, rec


JACRC_INFO = ("file name;source;role type;shengqiang;banshi;work;artist;level;accompaniment\n"
              "a-Song-nacta.wav;NACTA;dan;xipi;yuanban;w;Artist One ;professional;yes\n"
              "b-Song-svad.wav;SVAD;dan;xipi;yuanban;w;Artist Two;amateur;\n"
              "c-Song-comm.wav;commercial;dan;xipi;yuanban;w;Artist Three;professional;\n")
JACRC_STUDENTS = ("file name;source;role type;shengqiang;banshi;work;artist;date\n"
                  "a-Song-nacta-S1.wav;DX;dan;xipi;yuanban;w;adult student;20171214\n")


def test_jacrc_keeps_cc_by_recordings_only(tmp_path):
    wav = _wav_bytes(tmp_path)
    urls, rec = _zenodo_multi(6536490, "cc-by-4.0", ["JaCRC-recordings.zip", "JaCRC-recordings-info.csv",
                                                     "JaCRC-students-recordings-info.csv"])
    root = "JaCRC-recordings/"
    blob = _zip({root + "a-Song-nacta.wav": wav, root + "b-Song-svad.wav": wav,
                 root + "c-Song-comm.wav": wav, root + "d-unlisted.wav": wav,
                 root + "JaCRC-students-recordings/a-Song-nacta-S1.wav": wav,
                 root + "JaCRC-accompaniment-recordings/a-Song-nacta-jh.wav": wav,
                 root + "JaCRC-accompaniment-recordings/b-Song-svad-jh.wav": wav})
    http = FakeHttp({sources.ZENODO_API + "6536490": rec},
                    {urls["JaCRC-recordings.zip"]: blob,
                     urls["JaCRC-recordings-info.csv"]: JACRC_INFO.encode("utf-8-sig"),
                     urls["JaCRC-students-recordings-info.csv"]: JACRC_STUDENTS.encode()})
    rows = list(sources.fetch_jacrc(str(tmp_path / "w"), str(tmp_path / "o"), http, 1e9))
    got = sorted((os.path.basename(r.path), r.role, r.group) for r in rows)
    assert got == [("a-Song-nacta-S1.flac", "vocal", "students-DX"),
                   ("a-Song-nacta-jh.flac", "accompaniment", "Artist One"),
                   ("a-Song-nacta.flac", "vocal", "Artist One")]
    assert {r.licence for r in rows} == {"CC-BY-4.0"} and {r.source for r in rows} == {"jacrc"}
    assert all((tmp_path / "o" / r.path).is_file() for r in rows)


def test_adovoc_uses_solo_mono_takes_grouped_by_singer(tmp_path):
    wav = _wav_bytes(tmp_path)
    url, rec = _zenodo(3383118, "cc-by-4.0", "AdoVoc Pro.zip")
    r = "AdoVoc Pro/"
    blob = _zip({r + "jipio/JP_mono/JP_alba_A.wav": wav, r + "jipio/JP_mono/JP_cris_E.wav": wav,
                 r + "vibrato_marcado/VM_mono/VM_J_a.wav": wav, r + "jipio/JP_mono/JP_frase_aislado.wav": wav,
                 r + "jipio/JP_true/JP_alba_A_1.wav": wav, r + "jipio/JP_performance/JP_alba_acc.wav": wav,
                 r + "fandango_performance/f1.wav": wav, r + "ataque_soplado/AS_mono/AS_polifonia.wav": wav,
                 "__MACOSX/AdoVoc Pro/jipio/JP_mono/._JP_alba_A.wav": b"x"})
    http = FakeHttp({sources.ZENODO_API + "3383118": rec}, {url: blob})
    rows = list(sources.fetch_adovoc(str(tmp_path / "w"), str(tmp_path / "o"), http, 1e9))
    assert sorted((os.path.basename(x.path), x.group) for x in rows) == [
        ("JP_alba_A.flac", "alba"), ("JP_cris_E.flac", "cris"),
        ("JP_frase_aislado.flac", "adovoc-unknown"), ("VM_J_a.flac", "joaquin")]
    assert {x.role for x in rows} == {"vocal"} and {x.licence for x in rows} == {"CC-BY-4.0"}


def test_albumdb_sums_vocal_and_other_stems_per_song_with_one_gain(tmp_path):
    import numpy as np
    import soundfile as sf
    n = 4410
    t = np.arange(n) / 44100
    lead = np.stack([0.7 * np.sin(2 * np.pi * 440 * t)] * 2, axis=1)
    back = np.stack([0.6 * np.sin(2 * np.pi * 660 * t)] * 2, axis=1)
    bass = np.stack([0.8 * np.sin(2 * np.pi * 55 * t)] * 2, axis=1)
    drum = np.stack([0.5 * np.sin(2 * np.pi * 110 * t)] * 2, axis=1)
    url, rec = _zenodo(19683001, "cc-by-4.0", "stems_mixed.zip")
    s = "stems_mixed/"
    blob = _zip({s + "01 Song/vox_lead.wav": _np_wav(tmp_path, lead),
                 s + "01 Song/vox_backing_l.wav": _np_wav(tmp_path, back),
                 s + "01 Song/bass.wav": _np_wav(tmp_path, bass),
                 s + "01 Song/drums.wav": _np_wav(tmp_path, drum),
                 s + "02 Quiet/vox_lead.wav": _np_wav(tmp_path, 0.1 * lead),
                 s + "02 Quiet/bass.wav": _np_wav(tmp_path, 0.1 * bass)})
    http = FakeHttp({sources.ZENODO_API + "19683001": rec}, {url: blob})
    rows = list(sources.fetch_albumdb(str(tmp_path / "w"), str(tmp_path / "o"), http, 1e9))
    assert sorted((r.group, r.role) for r in rows) == [("01 Song", "accompaniment"), ("01 Song", "vocal"),
                                                     ("02 Quiet", "accompaniment"), ("02 Quiet", "vocal")]
    by = {(r.group, r.role): sf.read(str(tmp_path / "o" / r.path))[0] for r in rows}
    v, a = by[("01 Song", "vocal")], by[("01 Song", "accompaniment")]
    vref, aref = lead + back, bass + drum
    g = 0.99 / np.abs(vref + aref).max()  # the song's own mix must not clip; same gain on both
    assert np.abs(v - g * vref).max() < 2e-4 and np.abs(a - g * aref).max() < 2e-4
    q = by[("02 Quiet", "vocal")]
    assert np.abs(q - 0.1 * lead).max() < 2e-4  # quiet songs are not boosted
    assert {r.licence for r in rows} == {"CC-BY-4.0"} and {r.source for r in rows} == {"albumdb"}


def test_new_sources_are_registered_for_build_and_dry_run():
    for name, record in (("jacrc", 6536490), ("adovoc", 3383118), ("albumdb", 19683001)):
        assert name in sources.FETCHERS and build.ZENODO_RECORDS[name] == record
