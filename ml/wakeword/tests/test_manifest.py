# normalise_licence tests copied from feature/korokae-v2-training:ml/korokae_v2/tests/test_manifest.py
import pytest

from wakeword.data.manifest import Row, audit, normalise_licence, read_manifest, write_manifest


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
    "All rights reserved", "", "CC BY 2.0",
    "Attribution Non-Commercial 3.0", "Attribution-No Derivatives 4.0", "CC BY 4.0 Share Alike",
    "Attribution Share-Alike 3.0", "Creative Commons Attribution 4.0, non commercial use only",
    "CC BY 4.0 (no commercial use)", "GPL-3.0 by", "https://creativecommons.org/licenses/by/4.0/legalcode.nc",
    "https://example.com/licenses/by/4.0/", "Attribution NoDerivs (3.0)"])
def test_normalise_licence_rejects(text):
    assert normalise_licence(text) is None


def _row(path, licence="CC-BY-4.0", label="negative", source="librispeech", consent="n/a"):
    return Row(path=path, label=label, source=source, source_url="https://www.openslr.org/12/",
               author="Ann", licence=licence, attribution=f"Ann, {source}",
               speaker="s1", split="train", consent=consent)


def test_manifest_round_trip(tmp_path):
    rows = [_row("b.wav"), _row("a.wav", licence="CC0-1.0")]
    p = tmp_path / "manifest.csv"
    write_manifest(rows, str(p))
    assert read_manifest(str(p)) == rows


def test_audit_flags_bad_licence_missing_file_and_unlisted_file(tmp_path):
    for name in ("ok.wav", "nc.wav", "stray.wav"):
        (tmp_path / name).write_bytes(b"x")
    rows = [_row("ok.wav"), _row("nc.wav", licence="CC-BY-NC-4.0"), _row("missing.wav")]
    problems = audit(rows, str(tmp_path))
    assert len(problems) == 3
    joined = "\n".join(problems)
    for name in ("nc.wav", "missing.wav", "stray.wav"):
        assert name in joined
    assert "ok.wav" not in joined


def test_audit_passes_clean_manifest(tmp_path):
    (tmp_path / "a.wav").write_bytes(b"x")
    assert audit([_row("a.wav")], str(tmp_path)) == []


def test_rejects_missing_consent_for_own_recording(tmp_path):
    (tmp_path / "r.wav").write_bytes(b"x")
    bad = _row("r.wav", licence="own", label="positive", source="recording", consent="n/a")
    problems = audit([bad], str(tmp_path))
    assert len(problems) == 1 and "consent" in problems[0]
    good = _row("r.wav", licence="own", label="positive", source="recording", consent="yes")
    assert audit([good], str(tmp_path)) == []


def test_synthetic_own_rows_need_no_consent(tmp_path):
    (tmp_path / "s.wav").write_bytes(b"x")
    assert audit([_row("s.wav", licence="own", label="positive", source="kokoro-82m")],
                 str(tmp_path)) == []


def test_audit_rejects_unknown_label(tmp_path):
    (tmp_path / "s.wav").write_bytes(b"x")
    assert any("label" in p for p in audit([_row("s.wav", label="speech")], str(tmp_path)))
