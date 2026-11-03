import torch

from metrics import passthrough_sdr, sdr, vocal_attenuation_db


def _noise(seed=0, n=44100):
    g = torch.Generator().manual_seed(seed)
    return torch.randn(2, n, generator=g)


def test_sdr_perfect_estimate_is_high():
    x = _noise()
    assert sdr(x, x) > 60


def test_attenuation_zero_for_identity():
    v = _noise(1)
    assert abs(vocal_attenuation_db(v, v)) < 0.01


def test_attenuation_large_for_zero_leak():
    v = _noise(2)
    assert vocal_attenuation_db(torch.zeros_like(v), v) > 60


def test_passthrough_identity():
    x = _noise(3)
    assert passthrough_sdr(x, x) > 60


def test_evaluate_identity_mask_is_neutral():
    from evaluate import evaluate
    from model import BINS, N_FFT, stft

    tracks = [(_noise(4, 44100 * 3) * 0.1, _noise(5, 44100 * 3) * 0.1)]

    def ones(mix):
        F = stft(mix[:1]).shape[-1]
        return torch.ones(1, BINS, F)

    r = evaluate(ones, tracks, "cpu")
    assert r["n_tracks"] == 1
    assert abs(r["vocal_attenuation_median_db"]) < 0.1
    assert r["passthrough_sdr_median"] > 40
