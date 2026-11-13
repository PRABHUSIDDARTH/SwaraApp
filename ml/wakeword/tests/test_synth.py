import numpy as np
import pytest
import soundfile as sf
import yaml

from wakeword.data.manifest import audit, read_manifest
from wakeword.data.synth import check_near_misses, render_all, trim_silence

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
    assert {r.speaker for r in rows} == {"kokoro-82m:af_heart", "kokoro-82m:bm_george"}
    assert all(0.8 <= s <= 1.25 for _, _, s in fake.calls)
    assert audit(rows, str(tmp_path)) == []
    assert len(read_manifest(str(tmp_path / "manifest.csv"))) == len(rows)


def test_uses_language_code_from_voice_prefix(tmp_path):
    langs = []
    render_all(CFG, str(tmp_path), pipeline_factory=lambda lang: (langs.append(lang), FakeKokoro())[1])
    assert sorted(set(langs)) == ["a", "b"]


def test_configured_voices_give_nonempty_splits_and_all_english_voices(tmp_path):
    with open("configs/base.yaml") as f:
        cfg = yaml.safe_load(f)
    cfg["synth"]["variants_per_voice_text"] = 1
    assert len(cfg["synth"]["voices"]) == 28
    rows = render_all(cfg, str(tmp_path), pipeline_factory=lambda lang: FakeKokoro())
    assert {r.split for r in rows} == {"train", "val", "test"}
    for spk in {r.speaker for r in rows}:
        assert len({r.split for r in rows if r.speaker == spk}) == 1
    assert render_all(cfg, str(tmp_path / "again"), pipeline_factory=lambda lang: FakeKokoro())[0].split == rows[0].split


def test_trim_silence_removes_padding():
    x = np.concatenate([np.zeros(8000), 0.5 * np.ones(4000), np.zeros(8000)]).astype(np.float32)
    y = trim_silence(x)
    assert 4000 <= len(y) <= 4000 + 2 * 320 and np.max(np.abs(y)) == 0.5


def test_rendered_clips_are_trimmed(tmp_path):
    class Padded(FakeKokoro):
        def __call__(self, text, voice, speed):
            yield text, "", np.concatenate([np.zeros(24000), 0.3 * np.ones(6000), np.zeros(24000)]).astype(np.float32)
    rows = render_all(CFG, str(tmp_path), pipeline_factory=lambda lang: Padded())
    data, _ = sf.read(str(tmp_path / rows[0].path))
    assert len(data) < 6000 * 16 / 24 * 1.5 + 700


@pytest.mark.parametrize("bad", ["hey swara", "swara play", "ok hey swara now"])
def test_near_miss_guard_rejects_wake_word(bad):
    with pytest.raises(ValueError):
        check_near_misses({"positives": ["hey swara"], "near_misses": ["hey sara", bad]})


def test_near_miss_guard_allows_configured_list():
    with open("configs/base.yaml") as f:
        check_near_misses(yaml.safe_load(f)["synth"])
