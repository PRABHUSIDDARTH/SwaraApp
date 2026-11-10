import torch

from korokae_v2 import dsp
from korokae_v2.forward import separate_batch
from korokae_v2.losses import separation_loss
from korokae_v2.metrics import sdr
from korokae_v2.model import KorokaeNetV2


def _tiny():
    torch.manual_seed(0)
    return KorokaeNetV2(n_blocks=1, d=16, heads=2).eval()


def test_separate_batch_matches_dsp_separate_for_one_window():
    model = _tiny()
    # dsp.separate makes one window for L <= WIN - OVERLAP and zero-pads it to WIN samples
    n = dsp.WIN_SAMPLES - dsp.OVERLAP
    x = torch.randn(2, n) * 0.1
    padded = torch.nn.functional.pad(x, (0, dsp.WIN_SAMPLES - n))
    with torch.no_grad():
        _, inst = separate_batch(model, padded[None])
    assert torch.max(torch.abs(inst[0, :, :n] - dsp.separate(x, model))) < 1e-5


def test_vocals_plus_instrumental_equals_mix():
    x = torch.randn(3, 2, 44100) * 0.1
    with torch.no_grad():
        v, i = separate_batch(_tiny(), x)
    stft_round_trip = dsp.istft(dsp.stft(x.reshape(6, -1)), 44100).reshape(3, 2, -1)
    assert v.shape == x.shape and torch.allclose(v + i, stft_round_trip, atol=1e-5)
    assert torch.allclose(v + i, x, atol=1e-4)


def test_sdr_known_value():
    g = torch.Generator().manual_seed(0)
    ref = torch.randn(2, 2, 44100, generator=g)
    noise = torch.randn(2, 2, 44100, generator=g)
    noise *= (ref.pow(2).sum(dim=(1, 2)) / noise.pow(2).sum(dim=(1, 2)) / 100).sqrt()[:, None, None]
    assert torch.allclose(sdr(ref + noise, ref), torch.tensor([20.0, 20.0]), atol=0.01)


def test_sdr_silent_target_is_finite():
    ref = torch.zeros(2, 44100)
    est = torch.randn(2, 44100) * 1e-3
    s = sdr(est, ref)
    assert torch.isfinite(s) and s < 0


def test_loss_zero_for_perfect_estimate_and_positive_otherwise():
    v, i = torch.randn(2, 2, 44100), torch.randn(2, 2, 44100)
    assert separation_loss(v, i, v, i).item() < 1e-6
    assert separation_loss(v * 0.5, i, v, i).item() > 0.1


def test_loss_finite_on_silent_vocals():
    v = torch.zeros(2, 2, 44100)
    v_hat = torch.zeros(2, 2, 44100, requires_grad=True)
    i = torch.randn(2, 2, 44100)
    loss = separation_loss(v_hat, i, v, i)
    loss.backward()
    assert torch.isfinite(loss) and torch.isfinite(v_hat.grad).all()
