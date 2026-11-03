import shutil

import pytest
import torch

from augment import random_eq, random_reverb


def _x(seed=0, n=44100):
    g = torch.Generator().manual_seed(seed)
    return torch.randn(2, n, generator=g) * 0.1


def test_eq_identity_with_zero_gain():
    x = _x()
    y = random_eq(x, torch.Generator().manual_seed(1), gains_db=torch.zeros(3))
    assert float((y - x).abs().max()) < 1e-4


def test_eq_changes_signal_with_nonzero_gain():
    x = _x()
    y = random_eq(x, torch.Generator().manual_seed(1), gains_db=torch.tensor([6.0, -6.0, 6.0]))
    assert float((y - x).abs().max()) > 1e-3


def test_reverb_keeps_shape_and_finite():
    x = _x(2)
    y = random_reverb(x, torch.Generator().manual_seed(3))
    assert y.shape == x.shape and torch.isfinite(y).all()


@pytest.mark.skipif(shutil.which("ffmpeg") is None, reason="ffmpeg missing")
def test_codec_roundtrip_keeps_length_and_finite():
    import numpy as np

    from prepare_data import codec_roundtrip

    x = (np.random.default_rng(0).standard_normal((2, 44100)) * 0.1).astype(np.float32)
    for codec in ("mp3", "aac"):
        y = codec_roundtrip(x, codec)
        assert y.shape[0] == 2 and np.isfinite(y).all()
        assert abs(y.shape[1] - x.shape[1]) < 4096
