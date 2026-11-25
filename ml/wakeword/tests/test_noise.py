import numpy as np
import soundfile as sf
import yaml

from wakeword.data.manifest import audit
from wakeword.data.noise import generate, render_noise


def test_render_noise_rows_and_splits(tmp_path):
    with open("configs/base.yaml") as f:
        cfg = yaml.safe_load(f)
    rows = render_noise(cfg, str(tmp_path))
    assert len(rows) == 5 * 4
    assert all(r.licence == "own" and r.source == "synthetic-noise" and r.label == "background"
               and r.consent == "n/a" for r in rows)
    assert {r.split for r in rows} == {"train", "val", "test"}
    data, sr = sf.read(str(tmp_path / rows[0].path))
    assert sr == 16000 and data.ndim == 1 and len(data) == 80000
    assert audit(rows, str(tmp_path)) == []


def test_colours_have_expected_spectral_tilt():
    rng = np.random.default_rng(0)
    def tilt(k):
        x = generate(k, 4.0, rng)
        s = np.abs(np.fft.rfft(x)) ** 2
        return s[:len(s) // 8].sum() / s[len(s) // 2:].sum()
    assert tilt("white") < tilt("pink") < tilt("brown")
    assert abs(np.sqrt(np.mean(generate("hum", 1.0, rng) ** 2)) - 0.1) < 0.02
