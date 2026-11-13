import os

import numpy as np
import pytest
import torch
import yaml

from tests.conftest import make_root
from wakeword.data.manifest import ManifestError
from wakeword.model import WakeNet
from wakeword.train import Batcher, load_rows, mine_hard_negatives, train


def _cfg(roots):
    with open("configs/base.yaml") as f:
        cfg = yaml.safe_load(f)
    cfg["train"]["roots"] = roots
    cfg["train"].update(batch_size=6, epochs=2, steps_per_epoch=2, val_examples=6, hard_neg_per_epoch=4,
                        patience=5)
    return cfg


def test_load_rows_merges_roots_and_validates(tmp_path):
    a, b = str(tmp_path / "a"), str(tmp_path / "b")
    make_root(a, seed=1)
    make_root(b, seed=2)
    rows = load_rows([a, b, str(tmp_path / "missing")])  # absent roots are skipped
    assert len(rows) == 2 * 3 * 3 * 3  # 2 roots x 3 splits x 3 items x (pos, bg, neg)
    assert all(os.path.isabs(r.path) for r in rows)


def test_load_rows_catches_speaker_in_two_splits_across_roots(tmp_path):
    a, b = str(tmp_path / "a"), str(tmp_path / "b")
    make_root(a, splits=("train",), per_split=1)
    make_root(b, splits=("val",), per_split=1)
    # make the b speakers collide with a's
    from wakeword.data.manifest import read_manifest, write_manifest
    ra = read_manifest(os.path.join(a, "manifest.csv"))
    rb = read_manifest(os.path.join(b, "manifest.csv"))
    import dataclasses
    rb = [dataclasses.replace(r, speaker=ra[i].speaker) for i, r in enumerate(rb)]
    write_manifest(rb, os.path.join(b, "manifest.csv"), validate=False)
    with pytest.raises(ManifestError):
        load_rows([a, b])


def test_batcher_third_positive_and_snr_draw(tiny_root, monkeypatch):
    from wakeword.data.mix import MixConfig
    cfg = _cfg([tiny_root])
    rows = load_rows([tiny_root])
    b = Batcher(rows, "train", cfg, seed=0)
    snr_calls = []
    real = MixConfig.sample_snr
    monkeypatch.setattr(MixConfig, "sample_snr", lambda self, rng: (snr_calls.append(1), real(self, rng))[1])
    x, y = b.batch(9)
    assert x.shape == (9, 16000) and y.shape == (9,)
    assert int(y.sum()) == 3  # exactly one third positives
    assert len(snr_calls) >= 3  # drawn per mixed example (positives always; negatives when a clip is mixed)
    assert float(x.abs().max()) <= 1.0


def test_hard_negative_mining_returns_top_scoring(tiny_root):
    rows = load_rows([tiny_root])
    b = Batcher(rows, "train", _cfg([tiny_root]), seed=0)
    torch.manual_seed(0)
    m = WakeNet().eval()
    hard = mine_hard_negatives(m, b, n_keep=4, n_candidates=12)
    assert len(hard) == 4 and all(w.shape == (16000,) for w in hard)
    with torch.no_grad():
        scores = [float(m(torch.from_numpy(w)[None])[0]) for w in hard]
    assert scores == sorted(scores, reverse=True)
    b.add_hard(hard)
    assert len(b.hard) == 4


def test_train_smoke(tiny_root, tmp_path):
    cfg = _cfg([tiny_root])
    out = str(tmp_path / "ckpt")
    best = train(cfg, out, device="cpu", seed=0)
    assert os.path.isfile(best)
    ck = torch.load(best, map_location="cpu", weights_only=False)
    m = WakeNet()
    m.load_state_dict(ck["state_dict"])
    assert "val_loss" in ck and np.isfinite(ck["val_loss"])
