import torch

from wakeword.model import WakeNet, count_params


def test_param_count_le_250k():
    assert count_params(WakeNet()) <= 250_000


def test_output_shape_and_range():
    m = WakeNet().eval()
    x = torch.randn(3, 16000).clamp(-1, 1) * 0.3
    with torch.no_grad():
        p = m(x)
    assert p.shape == (3,) and p.dtype == torch.float32
    assert bool(((p >= 0) & (p <= 1)).all())


def test_front_end_matches_reference_log_mel():
    m = WakeNet().eval()
    x = torch.randn(2, 16000) * 0.1
    f = m.features(x)
    assert f.shape == (2, 40, 98)
    # reference: torch.stft power spectrum, same window, no centering
    spec = torch.stft(x, 400, 160, window=torch.hann_window(400), center=False, return_complex=True)
    power = spec.real ** 2 + spec.imag ** 2
    ref = torch.log(torch.einsum("mf,bft->bmt", m.mel_fb, power) + 1e-6)
    assert torch.allclose(f, ref, atol=1e-3, rtol=1e-3)


def test_trainable_gradients_flow():
    m = WakeNet()
    out = m(torch.randn(2, 16000) * 0.1)
    out.sum().backward()
    assert all(p.grad is not None for p in m.parameters())


def test_temporal_receptive_field_ge_80_frames():
    from wakeword.model import receptive_field_frames
    assert receptive_field_frames(WakeNet()) >= 80  # >= 800 ms at the 10 ms mel rate
