# normalise_licence tests copied from feature/korokae-v2-training:ml/korokae_v2/tests/test_manifest.py
import pytest

from wakeword.data.manifest import ManifestError, Row, assign_splits, audit, validate_rows, normalise_licence, read_manifest, write_manifest


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


def _row(path, licence="CC-BY-4.0", label="negative", source="librispeech", consent="n/a", **kw):
    return Row(path=path, label=label, source=source, source_url="https://www.openslr.org/12/",
               author="Ann", licence=licence, attribution=f"Ann, {source}",
               speaker="s1", split="train", consent=consent, **kw)


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
    good = _row("r.wav", licence="own", label="positive", source="recording", consent="yes",
                consent_ts="2026-11-13T10:00:00Z")
    assert audit([good], str(tmp_path)) == []


def test_synthetic_own_rows_need_no_consent(tmp_path):
    (tmp_path / "s.wav").write_bytes(b"x")
    assert audit([_row("s.wav", licence="own", label="positive", source="kokoro-82m")],
                 str(tmp_path)) == []


def test_audit_rejects_unknown_label(tmp_path):
    (tmp_path / "s.wav").write_bytes(b"x")
    assert any("label" in p for p in audit([_row("s.wav", label="speech")], str(tmp_path)))


def _fields(**kw):
    base = dict(path="a.wav", label="negative", source="librispeech", source_url="https://x/",
                author="Ann", licence="CC-BY-4.0", attribution="Ann", speaker="s1", split="train")
    base.update(kw)
    return Row(**base)


def test_recording_needs_consent_timestamp():
    r = _fields(licence="own", source="recording", consent="yes", consent_ts="", source_url="")
    assert any("consent" in p for p in validate_rows([r]))


@pytest.mark.parametrize("source", ["librispeech", "ccmixter", "", "kokoro"])
def test_own_licence_restricted_to_own_sources(source):
    r = _fields(licence="own", source=source, source_url="")
    assert any("'own'" in p or "empty source" in p for p in validate_rows([r]))


@pytest.mark.parametrize("src", ["kokoro-82m", "synthetic-noise"])
def test_own_allowed_for_synthetic_sources(src):
    assert validate_rows([_fields(licence="own", source=src, source_url="")]) == []


@pytest.mark.parametrize("field", ["source", "licence", "attribution", "author", "speaker"])
def test_required_fields_non_empty(field):
    assert any(f"empty {field}" in p or "licence" in p for p in validate_rows([_fields(**{field: " "})]))


def test_source_url_required_unless_own():
    assert any("source_url" in p for p in validate_rows([_fields(source_url="")]))


@pytest.mark.parametrize("kw", [{"split": "dev"}, {"label": "speech"}])
def test_invalid_split_or_label(kw):
    assert validate_rows([_fields(**kw)])


def test_duplicate_paths_rejected():
    assert any("duplicate" in p for p in validate_rows([_fields(), _fields(speaker="s2")]))


def test_speaker_in_two_splits_rejected():
    problems = validate_rows([_fields(path="a.wav"), _fields(path="b.wav", split="val")])
    assert any("more than one split" in p for p in problems)


def test_write_manifest_fails_loudly_on_bad_rows(tmp_path):
    with pytest.raises(ManifestError):
        write_manifest([_fields(licence="CC-BY-NC-4.0")], str(tmp_path / "m.csv"))
    assert not (tmp_path / "m.csv").exists()


def test_assign_splits_gives_every_split_a_member_and_is_deterministic():
    ids = [f"v{i}" for i in range(12)]
    a = assign_splits(ids)
    assert set(a.values()) == {"train", "val", "test"} and a == assign_splits(list(reversed(ids)))
