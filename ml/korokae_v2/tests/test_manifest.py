import pytest

from korokae_v2.data.credits import credits_text
from korokae_v2.data.manifest import Row, audit, normalise_licence, read_manifest, write_manifest


@pytest.mark.parametrize("text,want", [
    ("CC0 1.0", "CC0-1.0"), ("https://creativecommons.org/publicdomain/zero/1.0/", "CC0-1.0"),
    ("Creative Commons Attribution 4.0 International", "CC-BY-4.0"), ("CC BY 4.0", "CC-BY-4.0"),
    ("https://creativecommons.org/licenses/by/3.0/", "CC-BY-3.0"), ("Attribution (4.0)", "CC-BY-4.0"),
    ("cc-by-4.0", "CC-BY-4.0"), ("CC-BY-4.0", "CC-BY-4.0"), ("own", "own")])
def test_normalise_licence_forms(text, want):
    assert normalise_licence(text) == want


@pytest.mark.parametrize("text", [
    "CC BY-NC 4.0", "https://creativecommons.org/licenses/by-sa/4.0/", "CC BY-ND 3.0",
    "https://creativecommons.org/licenses/by-nc-sa/3.0/", "Attribution Noncommercial (3.0)",
    "All rights reserved", "", "CC BY 2.0"])
def test_normalise_licence_rejects(text):
    assert normalise_licence(text) is None


def _row(path, licence="CC-BY-4.0", author="Ann", source="vocalset", role="vocal"):
    return Row(path=path, role=role, source=source, source_url="https://zenodo.org/records/1",
               author=author, licence=licence, attribution=f"{author}, {source}",
               group="g1", split="train")


def test_manifest_round_trip(tmp_path):
    rows = [_row("audio/b.flac"), _row("audio/a.flac", licence="CC0-1.0", author="Bo")]
    p = tmp_path / "manifest.csv"
    write_manifest(rows, str(p))
    assert read_manifest(str(p)) == rows


def test_audit_flags_bad_licence_missing_file_and_unlisted_file(tmp_path):
    (tmp_path / "audio").mkdir()
    for name in ("ok.flac", "nc.flac", "stray.flac"):
        (tmp_path / "audio" / name).write_bytes(b"x")
    rows = [_row("audio/ok.flac"), _row("audio/nc.flac", licence="CC-BY-NC-4.0"),
            _row("audio/missing.flac")]
    problems = audit(rows, str(tmp_path))
    assert len(problems) == 3
    joined = "\n".join(problems)
    for name in ("nc.flac", "missing.flac", "stray.flac"):
        assert name in joined
    assert "ok.flac" not in joined


def test_audit_passes_clean_manifest(tmp_path):
    (tmp_path / "a.flac").write_bytes(b"x")
    assert audit([_row("a.flac")], str(tmp_path)) == []


def test_credits_lists_each_cc_by_author_once_and_skips_cc0_and_own():
    rows = [_row("1.flac", author="Ann"), _row("2.flac", author="Ann"),
            _row("3.flac", author="Cy", source="ccmixter"),
            _row("4.flac", licence="CC0-1.0", author="Zed"),
            _row("5.flac", licence="own", author="Owner")]
    text = credits_text(rows)
    lines = [l for l in text.splitlines() if " — " in l]
    assert len(lines) == 2
    assert lines[0].startswith("Cy, ccmixter — CC-BY-4.0")
    assert lines[1].startswith("Ann, vocalset — CC-BY-4.0")
    assert "Zed" not in text and "Owner" not in text


def test_merge_manifests_makes_paths_absolute_and_keeps_rows(tmp_path):
    from korokae_v2.data.manifest import merge_manifests
    for name in ("v", "a"):
        (tmp_path / name).mkdir()
        (tmp_path / name / "x.flac").write_bytes(b"x")
        write_manifest([_row("x.flac", role="vocal" if name == "v" else "accompaniment")],
                       str(tmp_path / name / "manifest.csv"))
    rows = merge_manifests([str(tmp_path / "v"), str(tmp_path / "a")])
    assert [r.path for r in rows] == [str(tmp_path / "v" / "x.flac"), str(tmp_path / "a" / "x.flac")]
    assert audit(rows, str(tmp_path / "empty_root")) == []
