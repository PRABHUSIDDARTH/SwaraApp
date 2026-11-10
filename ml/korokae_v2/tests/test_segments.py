import numpy as np
import soundfile as sf
import torch

from korokae_v2.data.manifest import Row, write_manifest
from korokae_v2.data.segments import PairDataset, StemPool

SR = 44100


def _write(root, rel, audio):
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(p), audio, SR, subtype="PCM_16")
    return rel


def _row(rel, role="vocal", split="train"):
    return Row(path=rel, role=role, source="t", source_url="u", author="a", licence="CC0-1.0",
               attribution="a", group=rel, split=split)


def _tone(seconds, channels=1, amp=0.3):
    t = np.arange(int(seconds * SR)) / SR
    x = amp * np.sin(2 * np.pi * 330 * t)
    return x if channels == 1 else np.stack([x, -x], axis=1)


def test_sample_shape_dtype_and_mono_duplication(tmp_path):
    rel = _write(tmp_path, "a.flac", _tone(3))
    pool = StemPool([_row(rel)], str(tmp_path), "vocal", "train")
    x = pool.sample(np.random.default_rng(0), SR)
    assert x.shape == (2, SR) and x.dtype == np.float32
    assert np.array_equal(x[0], x[1]) and np.abs(x).max() > 0.1


def test_stereo_file_keeps_channels(tmp_path):
    rel = _write(tmp_path, "s.flac", _tone(3, channels=2))
    x = StemPool([_row(rel)], str(tmp_path), "vocal", "train").sample(np.random.default_rng(0), SR)
    assert np.allclose(x[0], -x[1], atol=1e-4)


def test_sample_prefers_active_audio(tmp_path):
    audio = np.concatenate([np.zeros(5 * SR), _tone(5)])
    rel = _write(tmp_path, "half.flac", audio)
    pool = StemPool([_row(rel)], str(tmp_path), "vocal", "train")
    rng = np.random.default_rng(1)
    active = 0
    for _ in range(50):
        x = pool.sample(rng, 2 * SR)
        rms = 10 * np.log10(np.mean(x ** 2) + 1e-12)
        active += rms > -50
    assert active >= 45


def test_sample_pads_files_shorter_than_length(tmp_path):
    rel = _write(tmp_path, "short.flac", _tone(1))
    x = StemPool([_row(rel)], str(tmp_path), "vocal", "train").sample(np.random.default_rng(0), 3 * SR)
    assert x.shape == (2, 3 * SR)
    assert np.abs(x[:, :SR]).max() > 0.1 and np.abs(x[:, SR:]).max() == 0


def test_pool_is_reproducible_for_a_seed(tmp_path):
    rows = [_row(_write(tmp_path, f"{i}.flac", _tone(4, amp=0.1 * (i + 1)))) for i in range(3)]
    pool = StemPool(rows, str(tmp_path), "vocal", "train")
    a = pool.sample(np.random.default_rng(7), SR)
    b = pool.sample(np.random.default_rng(7), SR)
    assert np.array_equal(a, b)


def test_pair_dataset_only_uses_requested_split(tmp_path):
    rows = [_row(_write(tmp_path, "vt.flac", _tone(3, amp=0.2)), "vocal", "train"),
            _row(_write(tmp_path, "vv.flac", _tone(3, amp=0.5)), "vocal", "val"),
            _row(_write(tmp_path, "at.flac", _tone(3, amp=0.3)), "accompaniment", "train"),
            _row(_write(tmp_path, "av.flac", _tone(3, amp=0.6)), "accompaniment", "val")]
    write_manifest(rows, str(tmp_path / "manifest.csv"))
    ds = PairDataset(str(tmp_path / "manifest.csv"), str(tmp_path), "train", length=SR, seed=3)
    for _, (v, a) in zip(range(5), ds):
        assert isinstance(v, torch.Tensor) and v.shape == (2, SR)
        assert 0.15 < v.abs().max() < 0.25 and 0.25 < a.abs().max() < 0.35
