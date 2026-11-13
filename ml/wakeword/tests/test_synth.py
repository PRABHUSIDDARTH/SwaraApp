import numpy as np
import soundfile as sf

from wakeword.data.manifest import audit, read_manifest
from wakeword.data.synth import render_all

CFG = {
    "sample_rate": 16000,
    "synth": {"voices": ["af_heart", "bm_george"], "positives": ["hey swara"],
              "near_misses": ["hey sara", "sweater"], "speeds": [0.8, 1.0, 1.25],
              "semitones": [-2, 0, 2], "variants_per_voice_text": 2, "seed": 0},
}


class FakeKokoro:
    """Mimics kokoro.KPipeline: yields (graphemes, phonemes, 24 kHz float audio) chunks."""
    def __init__(self):
        self.calls = []

    def __call__(self, text, voice, speed):
        self.calls.append((text, voice, speed))
        t = np.arange(int(24000 * 0.6 / speed)) / 24000
        half = len(t) // 2
        yield text, "", (0.3 * np.sin(2 * np.pi * 220 * t[:half])).astype(np.float32)
        yield text, "", (0.3 * np.sin(2 * np.pi * 220 * t[half:])).astype(np.float32)


def test_renders_16k_mono(tmp_path):
    fake = FakeKokoro()
    rows = render_all(CFG, str(tmp_path), pipeline_factory=lambda lang: fake)
    assert len(rows) == 2 * 3 * 2          # voices x texts x variants
    for r in rows:
        data, sr = sf.read(str(tmp_path / r.path))
        assert sr == 16000 and data.ndim == 1 and len(data) > 1000
        assert r.licence == "own" and r.consent == "n/a" and r.source == "kokoro-82m"
    assert {r.label for r in rows} == {"positive", "near_miss"}
    assert {r.speaker for r in rows} == {"af_heart", "bm_george"}
    assert all(0.8 <= s <= 1.25 for _, _, s in fake.calls)
    assert audit(rows, str(tmp_path)) == []
    assert len(read_manifest(str(tmp_path / "manifest.csv"))) == len(rows)


def test_uses_language_code_from_voice_prefix(tmp_path):
    langs = []
    render_all(CFG, str(tmp_path), pipeline_factory=lambda lang: (langs.append(lang), FakeKokoro())[1])
    assert sorted(set(langs)) == ["a", "b"]
