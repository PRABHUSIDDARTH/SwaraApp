import numpy as np
import pytest
import soundfile as sf

from wakeword.data.manifest import audit, read_manifest
from wakeword.data.record import record_session


def _capture(seconds, sr):
    return (0.1 * np.ones(int(seconds * sr))).astype(np.float32)


def test_no_consent_writes_nothing(tmp_path):
    for answer in ("", "n", "yes please", "Y!"):
        rows = record_session("alice", 3, str(tmp_path), capture=_capture, input_fn=lambda p: answer)
        assert rows == []
    assert list(tmp_path.iterdir()) == []


def test_consent_y_writes_wavs_and_rows(tmp_path):
    rows = record_session("alice", 3, str(tmp_path), capture=_capture, input_fn=lambda p: "y")
    assert len(rows) == 3
    assert all(r.licence == "own" and r.source == "recording" and r.consent == "yes"
               and r.speaker == "recording:alice" and r.label == "positive" for r in rows)
    data, sr = sf.read(str(tmp_path / rows[0].path))
    assert sr == 16000 and data.ndim == 1
    assert audit(read_manifest(str(tmp_path / "manifest.csv")), str(tmp_path)) == []


def test_second_session_appends(tmp_path):
    record_session("alice", 2, str(tmp_path), capture=_capture, input_fn=lambda p: "y")
    record_session("bob", 2, str(tmp_path), capture=_capture, input_fn=lambda p: "y")
    assert len(read_manifest(str(tmp_path / "manifest.csv"))) == 4


def test_capture_resampled_to_16k_mono(tmp_path):
    def cap48(seconds, sr):
        return np.zeros((int(seconds * 48000), 2), np.float32)  # stereo 48 kHz device
    rows = record_session("c", 1, str(tmp_path), capture=cap48, capture_sr=48000, input_fn=lambda p: "y")
    data, sr = sf.read(str(tmp_path / rows[0].path))
    assert sr == 16000 and data.ndim == 1 and abs(len(data) - 16000 * 2) < 50


@pytest.mark.parametrize("bad", ["Alice", "a b", "../x", "", "a/b", "é"])
def test_speaker_id_sanitised(tmp_path, bad):
    with pytest.raises(ValueError):
        record_session(bad, 1, str(tmp_path), capture=_capture, input_fn=lambda p: "y")
    assert list(tmp_path.iterdir()) == []


def test_numbering_continues_and_never_overwrites(tmp_path):
    a = record_session("alice", 2, str(tmp_path), capture=_capture, input_fn=lambda p: "y")
    b = record_session("alice", 2, str(tmp_path), capture=_capture, input_fn=lambda p: "y")
    paths = [r.path for r in a + b]
    assert len(set(paths)) == 4 and paths[2].endswith("_002.wav")
    assert len(read_manifest(str(tmp_path / "manifest.csv"))) == 4


def test_numbering_skips_orphan_files(tmp_path):
    (tmp_path / "positive").mkdir()
    (tmp_path / "positive" / "rec_alice_007.wav").write_bytes(b"x")
    r = record_session("alice", 1, str(tmp_path), capture=_capture, input_fn=lambda p: "y")
    assert r[0].path.endswith("_008.wav")


def test_rows_appended_per_take_so_crash_leaves_no_orphans(tmp_path):
    calls = {"n": 0}
    def flaky(seconds, sr):
        calls["n"] += 1
        if calls["n"] == 3:
            raise RuntimeError("mic unplugged")
        return _capture(seconds, sr)
    with pytest.raises(RuntimeError):
        record_session("alice", 5, str(tmp_path), capture=flaky, input_fn=lambda p: "y")
    rows = read_manifest(str(tmp_path / "manifest.csv"))
    assert len(rows) == 2 and len(list((tmp_path / "positive").iterdir())) == 2


def test_consent_timestamp_recorded(tmp_path):
    from datetime import datetime, timezone
    r = record_session("alice", 1, str(tmp_path), capture=_capture, input_fn=lambda p: "y",
                       now=lambda: datetime(2026, 11, 13, 10, 0, 5, tzinfo=timezone.utc))
    assert r[0].consent_ts == "2026-11-13T10:00:05Z"
    assert read_manifest(str(tmp_path / "manifest.csv"))[0].consent_ts == "2026-11-13T10:00:05Z"


def test_split_option_and_speaker_cannot_change_split(tmp_path):
    r = record_session("bob", 1, str(tmp_path), capture=_capture, input_fn=lambda p: "y", split="test")
    assert r[0].split == "test"
    with pytest.raises(ValueError):
        record_session("bob", 1, str(tmp_path), capture=_capture, input_fn=lambda p: "y", split="train")
    with pytest.raises(ValueError):
        record_session("carol", 1, str(tmp_path), capture=_capture, input_fn=lambda p: "y", split="dev")
