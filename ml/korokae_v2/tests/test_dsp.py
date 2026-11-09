import torch

from korokae_v2.dsp import BINS, band_edges, istft, separate, stft


def test_band_edges_cover_all_bins_contiguously():
    e = band_edges()
    assert e[0][0] == 0 and e[-1][1] == BINS
    assert all(a[1] == b[0] for a, b in zip(e, e[1:]))
    assert all(hi > lo for lo, hi in e)
    assert len(e) == 39  # 10 + 12 + 8 + 8 + 1


def test_stft_istft_round_trip():
    x = torch.randn(2, 44100 * 3 + 123)
    y = istft(stft(x), x.shape[1])
    assert torch.max(torch.abs(x - y)) < 1e-4


def test_separate_zero_mask_returns_input_across_windows():
    x = torch.randn(2, int(44100 * 17.3))  # 3 windows, ragged end
    y = separate(x, lambda s: torch.zeros_like(s))
    assert y.shape == x.shape and torch.max(torch.abs(x - y)) < 1e-4


def test_separate_identity_vocal_mask_gives_silence():
    x = torch.randn(2, 44100 * 10)
    ones = lambda s: torch.cat([torch.ones_like(s[:, :1]), torch.zeros_like(s[:, :1])] * 2, 1)
    assert torch.max(torch.abs(separate(x, ones))) < 1e-4


def test_separate_short_input():
    x = torch.randn(2, 13000)
    assert separate(x, lambda s: torch.zeros_like(s)).shape == x.shape
