import math

import torch

from korokae_v2.augment import (AugmentConfig, make_ir, master_gain, mix_batch, pan_width,
                                random_eq, reverb, varispeed)

SR = 44100
OFF = dict(p_varispeed=0.0, p_reverb=0.0, p_eq=0.0, p_master=0.0, width=(1.0, 1.0), pan=(0.0, 0.0))


def _gen(seed=0):
    return torch.Generator().manual_seed(seed)


def _sine(f, seconds=1.0, amp=0.5):
    t = torch.arange(int(seconds * SR)) / SR
    return amp * torch.sin(2 * math.pi * f * t)


def test_mix_equals_sum_of_targets():
    v, a = torch.randn(4, 2, SR), torch.randn(4, 2, SR) * 3  # loud: forces limiting
    m, vt, at = mix_batch(v, a, AugmentConfig(p_master=1.0), _gen())
    assert torch.allclose(m, vt + at, atol=1e-6) and m.abs().max() <= 1.0 + 1e-6
    assert m.shape == v.shape and m.dtype == torch.float32


def test_varispeed_shifts_pitch():
    x = _sine(440.0, 2.0)[None]
    y = varispeed(x, 2.0)
    assert y.shape == x.shape
    seg = y[0, : int(1.5 * SR)]
    spec = torch.fft.rfft(seg * torch.hann_window(seg.numel(), periodic=False))
    peak_hz = spec.abs().argmax().item() * SR / seg.numel()
    assert abs(peak_hz - 440.0 * 2 ** (2 / 12)) < 3.0


def test_reverb_extends_tail_and_ir_is_unit_energy():
    ir = make_ir(0.8, _gen())
    assert ir.shape[0] == 2 and abs(ir.pow(2).sum(dim=1).mean().item() - 1.0) < 1e-4
    x = torch.zeros(1, 2, SR)
    x[:, :, SR // 4] = 1.0
    y = reverb(x, ir)
    assert y.shape == x.shape
    assert y[0, :, SR // 4 + 4410:].pow(2).sum() > 1e-3  # energy 100 ms after the click


def test_eq_bounded():
    x = torch.randn(1, 2, 5 * SR, generator=_gen(1))
    y = random_eq(x, _gen(2), eq_db=6.0)
    X, Y = torch.fft.rfft(x[0, 0]), torch.fft.rfft(y[0, 0])
    freqs = torch.fft.rfftfreq(x.shape[-1], 1 / SR)
    lo = 50.0
    while lo < 16000:
        band = (freqs >= lo) & (freqs < 2 * lo)
        change = 10 * math.log10(Y[band].abs().pow(2).sum() / X[band].abs().pow(2).sum())
        assert abs(change) <= 7.0, (lo, change)
        lo *= 2


def test_level_range_respected():
    v, a = torch.randn(32, 2, SR, generator=_gen(3)) * 0.01, torch.randn(32, 2, SR, generator=_gen(4))
    _, vt, at = mix_batch(v, a, AugmentConfig(**OFF), _gen(5))
    ratio = 10 * torch.log10(vt.pow(2).sum(dim=(1, 2)) / at.pow(2).sum(dim=(1, 2)))
    assert ratio.min() >= -10.01 and ratio.max() <= 10.01 and ratio.max() - ratio.min() > 5


def test_mono_vocal_gets_stereo_image():
    x = _sine(300.0)[None, None].repeat(1, 2, 1)
    y = pan_width(x, pan=torch.tensor([0.4]), width=torch.tensor([1.0]))
    assert not torch.allclose(y[0, 0], y[0, 1])
    m, vt, _ = mix_batch(x.repeat(8, 1, 1), torch.randn(8, 2, SR) * 0.1, AugmentConfig(), _gen(6))
    assert any(not torch.allclose(vt[i, 0], vt[i, 1]) for i in range(8))


def test_master_gain_is_bounded_and_limits_peaks():
    mix = torch.randn(2, 2, SR) * 2
    g = master_gain(mix, torch.tensor([3.0, 3.0]), torch.tensor([-1.0, -1.0]))
    assert g.shape == (2, 1, SR) and (g > 0).all() and (g <= 1.0 + 1e-6).all()
    assert (mix * g).abs().max() <= 10 ** (-1.0 / 20) + 1e-4


def test_deterministic_for_generator_seed():
    v, a = torch.randn(3, 2, SR), torch.randn(3, 2, SR)
    r1 = mix_batch(v, a, AugmentConfig(), _gen(9))
    r2 = mix_batch(v, a, AugmentConfig(), _gen(9))
    assert all(torch.equal(x, y) for x, y in zip(r1, r2))
