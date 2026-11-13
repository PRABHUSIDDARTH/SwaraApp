import numpy as np
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
               and r.speaker == "alice" and r.label == "positive" for r in rows)
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
