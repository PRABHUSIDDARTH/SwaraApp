import torch

from korokae_v2.model import KorokaeNetV2, count_params

SMALL = dict(n_blocks=1, d=16, heads=2)


def test_forward_shape_even_and_odd_frames():
    m = KorokaeNetV2(**SMALL).eval()
    for t in (50, 37, 1):
        x = torch.randn(1, 4, 1025, t)
        assert m(x).shape == (1, 4, 1025, t)


def test_default_size_in_speed_budget_range():
    assert 2_000_000 <= count_params(KorokaeNetV2()) <= 8_000_000


def test_zero_input_is_finite():
    m = KorokaeNetV2(**SMALL).eval()
    assert torch.isfinite(m(torch.zeros(1, 4, 1025, 20))).all()


def test_time_axis_is_bidirectional():
    # changing a late frame must change an early frame's mask (look-ahead exists)
    torch.manual_seed(0)
    m = KorokaeNetV2(**SMALL).eval()
    x = torch.randn(1, 4, 1025, 30)
    y = x.clone()
    y[..., 29] += 1.0
    assert not torch.allclose(m(x)[..., 0], m(y)[..., 0])


def test_time_attention_is_position_aware():
    # identical frames everywhere: without positional information every frame's mask would match
    torch.manual_seed(0)
    m = KorokaeNetV2(**SMALL).eval()
    x = torch.randn(1, 4, 1025, 1).repeat(1, 1, 1, 24)
    out = m(x)
    assert not torch.allclose(out[..., 4], out[..., 12], atol=1e-5)
