import torch

from losses import compressed_loss, loss_v2, mrstft_loss, vocal_residual_loss


def _spec(seed, shape=(2, 2, 513, 20)):
    g = torch.Generator().manual_seed(seed)
    return torch.complex(torch.randn(*shape, generator=g), torch.randn(*shape, generator=g))


def _wave(seed, n=8192):
    g = torch.Generator().manual_seed(seed)
    return torch.randn(2, 2, n, generator=g) * 0.1


def test_compressed_loss_zero_when_equal():
    s = _spec(0)
    assert float(compressed_loss(s, s)) < 1e-6


def test_mrstft_zero_when_equal():
    w = _wave(1)
    assert float(mrstft_loss(w, w)) < 1e-6


def test_vocal_residual_zero_for_zero_mask():
    v = _spec(2)
    assert float(vocal_residual_loss(torch.zeros(2, 2, 513, 20), v)) == 0.0


def test_vocal_residual_positive_for_unit_mask():
    v = _spec(3)
    assert float(vocal_residual_loss(torch.ones(2, 2, 513, 20), v)) > 0.1


def test_loss_v2_is_finite_and_differentiable():
    mix, acc, voc = _spec(4), _spec(5), _spec(6)
    masks = torch.full((2, 2, 513, 20), 0.5, requires_grad=True)
    loss = loss_v2(masks, mix, acc, voc, _wave(7), _wave(8))
    loss.backward()
    assert torch.isfinite(loss) and torch.isfinite(masks.grad).all()
