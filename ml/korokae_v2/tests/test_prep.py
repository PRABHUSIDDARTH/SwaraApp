import subprocess

import pytest
import soundfile as sf

from korokae_v2.data.prep import PrepError, to_flac
from korokae_v2.data.splits import assign_splits


def _ffmpeg(*args):
    subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", *args], check=True)


def test_prepare_converts_rate_and_keeps_channels(tmp_path):
    wav = tmp_path / "mono48.wav"
    mp3 = tmp_path / "stereo.mp3"
    _ffmpeg("-f", "lavfi", "-i", "sine=f=440:r=48000:d=2", "-c:a", "pcm_s24le", str(wav))
    _ffmpeg("-f", "lavfi", "-i", "sine=f=440:r=44100:d=2", "-ac", "2", str(mp3))
    for src, channels in ((wav, 1), (mp3, 2)):
        dst = tmp_path / (src.stem + ".flac")
        dur = to_flac(str(src), str(dst))
        info = sf.info(str(dst))
        assert (info.samplerate, info.subtype, info.channels) == (44100, "PCM_16", channels)
        assert abs(dur - 2.0) < 0.06  # mp3 encoder padding
        assert abs(info.duration - dur) < 1e-3


def test_failed_conversion_raises_and_leaves_no_file(tmp_path):
    src = tmp_path / "notes.txt"
    src.write_text("not audio")
    dst = tmp_path / "out.flac"
    with pytest.raises(PrepError):
        to_flac(str(src), str(dst))
    assert not dst.exists() and not (tmp_path / "out.flac.part").exists()


def test_splits_deterministic_nonempty_and_disjoint():
    g = [f"singer{i}" for i in range(20)]
    a, b = assign_splits(g), assign_splits(list(reversed(g)))
    assert a == b and {"train", "val", "test"} == set(a.values())
    assert sum(v == "test" for v in a.values()) == 2


def test_splits_tiny_groups():
    assert set(assign_splits(["a"]).values()) == {"train"}
    assert sorted(assign_splits(["a", "b"]).values()) == ["test", "train"]
    assert sorted(assign_splits(["a", "b", "c"]).values()) == ["test", "train", "val"]
