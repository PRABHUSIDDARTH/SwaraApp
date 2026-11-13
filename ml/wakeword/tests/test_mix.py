import numpy as np
import yaml

from wakeword.data.mix import MixConfig, make_example

SR = 16000
NOFX = {"reverb_p": 0.0, "cheap_mic_p": 0.0, "gain_db": [0.0, 0.0]}


def _clip(n=8000, seed=1):
    r = np.random.default_rng(seed)
    return (0.3 * r.standard_normal(n)).astype(np.float32)


def _bg(seed=2):
    r = np.random.default_rng(seed)
    return (0.1 * r.standard_normal(SR * 3)).astype(np.float32)


def test_output_length_16000():
    out = make_example(_clip(), _bg(), 5.0, np.random.default_rng(0))
    assert out.shape == (16000,) and out.dtype == np.float32
    assert np.max(np.abs(out)) <= 1.0


def test_snr_within_0p5_db():
    for snr in (-5.0, 0.0, 7.5, 15.0):
        for seed in range(5):
            c, b, span = make_example(_clip(seed=seed), _bg(), snr, np.random.default_rng(seed),
                                      NOFX, return_parts=True)
            pc = np.mean(c[span] ** 2)
            pb = np.mean(b[span] ** 2)
            assert abs(10 * np.log10(pc / pb) - snr) < 0.5


def test_snr_holds_with_reverb_and_cheap_mic():
    c, b, span = make_example(_clip(), _bg(), 3.0, np.random.default_rng(4),
                              {"reverb_p": 1.0, "cheap_mic_p": 1.0}, return_parts=True)
    assert abs(10 * np.log10(np.mean(c[span] ** 2) / np.mean(b[span] ** 2)) - 3.0) < 0.5


def test_clip_ends_in_last_200ms():
    for seed in range(30):
        _, _, span = make_example(_clip(4000), _bg(), 5.0, np.random.default_rng(seed),
                                  NOFX, return_parts=True)
        assert SR - 3200 <= span.stop <= SR


def test_long_clip_is_truncated_to_window():
    out = make_example(_clip(40000), _bg(), 5.0, np.random.default_rng(0))
    assert out.shape == (16000,)


def test_short_background_is_tiled():
    out = make_example(_clip(), _bg()[:500], 5.0, np.random.default_rng(0))
    assert out.shape == (16000,)


def test_cheap_mic_band_limits():
    r = np.random.default_rng(3)
    clip = r.standard_normal(12000).astype(np.float32)
    c, _, span = make_example(clip, np.zeros(SR, np.float32), 5.0, np.random.default_rng(0),
                              {"reverb_p": 0.0, "cheap_mic_p": 1.0}, return_parts=True)
    spec = np.abs(np.fft.rfft(c[span])) ** 2
    f = np.fft.rfftfreq(len(c[span]), 1 / SR)
    assert spec[f > 5000].sum() < 0.02 * spec[(f > 500) & (f < 3000)].sum()


def test_clips_that_fit_are_preserved_in_full():
    for n in (14000, 16000):
        x = _clip(n) / 3
        for seed in range(20):
            c, _, span = make_example(x, _bg(), 5.0, np.random.default_rng(seed), NOFX, return_parts=True)
            assert span.stop - span.start == n
            assert span.stop >= SR - 3200
            np.testing.assert_allclose(c[span], x, atol=1e-6)


def test_overlong_clip_keeps_its_tail():
    x = _clip(20000) / 3
    c, _, span = make_example(x, _bg(), 5.0, np.random.default_rng(0), NOFX, return_parts=True)
    assert span == slice(0, SR)
    np.testing.assert_allclose(c, x[-SR:], atol=1e-6)


def test_mixconfig_defaults_match_yaml():
    with open("configs/base.yaml") as f:
        assert MixConfig.from_dict(yaml.safe_load(f)["mix"]) == MixConfig()


def test_config_changes_behaviour():
    cfg = MixConfig.from_dict({"gain_db": [-20.0, -20.0], "reverb_p": 0, "cheap_mic_p": 0})
    out = make_example(_clip() / 3, _bg(), 5.0, np.random.default_rng(0), cfg)
    assert np.max(np.abs(out)) < 0.2
