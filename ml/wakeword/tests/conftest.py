"""Tiny synthetic datasets for the model / train / evaluate tests (no real data, no TTS)."""
import os

import numpy as np
import pytest
import soundfile as sf

from wakeword.data.manifest import Row, write_manifest

SR = 16000


def burst(rng, f0, seconds=0.5):
    """A sine burst with a decaying envelope: the stand-in 'wake word'."""
    t = np.arange(int(seconds * SR)) / SR
    return (0.5 * np.sin(2 * np.pi * f0 * t) * np.hanning(len(t))).astype(np.float32)


def make_root(root, splits=("train", "val", "test"), per_split=3, seed=0, with_neg=True,
              labels=("positive", "background", "negative"), tag=None):
    """Write a data root with positives (sine bursts), negatives (low-passed noise) and background."""
    rng = np.random.default_rng(seed)
    os.makedirs(root, exist_ok=True)
    rows = []
    tag = tag or os.path.basename(str(root))
    for sp in splits:
        for i in range(per_split):
            specs = [("positive", "pos", lambda: burst(rng, 800 + 100 * rng.random())),
                     ("background", "bg", lambda: (0.05 * rng.standard_normal(3 * SR)).astype(np.float32))]
            if with_neg:
                specs.append(("negative", "neg", lambda: (0.2 * rng.standard_normal(SR // 2)).astype(np.float32)))
            for label, short, fn in specs:
                if label not in labels:
                    continue
                rel = f"{short}_{sp}_{i}.wav"
                sf.write(os.path.join(root, rel), fn(), SR, subtype="FLOAT")
                rows.append(Row(path=rel, label=label, source="synthetic-noise", source_url="",
                                author="t", licence="own", attribution="t",
                                speaker=f"{tag}:{short}:{sp}:{i}", split=sp))
    write_manifest(rows, os.path.join(root, "manifest.csv"))
    return rows


@pytest.fixture
def tiny_root(tmp_path):
    root = tmp_path / "synth"
    make_root(str(root))
    return str(root)
