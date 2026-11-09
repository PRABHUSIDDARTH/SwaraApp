import torch

from korokae_v2.model import KorokaeNetV2, count_params


def test_forward_shape():
    m = KorokaeNetV2(n_blocks=2, hidden=64, d=32).eval()
    x = torch.randn(1, 4, 1025, 50)
    assert m(x).shape == (1, 4, 1025, 50)


def test_default_size_in_spec_range():
    assert 15_000_000 <= count_params(KorokaeNetV2()) <= 25_000_000


def test_zero_input_is_finite():
    m = KorokaeNetV2(n_blocks=2, hidden=64, d=32).eval()
    assert torch.isfinite(m(torch.zeros(1, 4, 1025, 20))).all()


def test_time_axis_is_bidirectional():
    # changing a late frame must change an early frame's mask (look-ahead exists)
    torch.manual_seed(0)
    m = KorokaeNetV2(n_blocks=1, hidden=32, d=16).eval()
    x = torch.randn(1, 4, 1025, 30)
    y = x.clone()
    y[..., 29] += 1.0
    assert not torch.allclose(m(x)[..., 0], m(y)[..., 0])
