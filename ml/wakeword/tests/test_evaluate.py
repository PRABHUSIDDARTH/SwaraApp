import numpy as np
import pytest
import torch

from tests.conftest import make_root
from wakeword.evaluate import (GateResult, WakeGate, _neg_chunks, count_triggers, fa_per_hour, gate,
                               lowest_threshold, stream_probs, stream_probs_chunks, stream_triggers)
from wakeword.model import WakeNet


def test_two_consecutive_required():
    # a single isolated spike never fires; two adjacent hops do (on the second)
    probs = [0, 0.9, 0, 0, 0.9, 0.9, 0, 0]
    assert stream_triggers(probs, 0.5) == [5]


def test_counter_resets_below_threshold():
    assert stream_triggers([0.9, 0.1, 0.9, 0.1, 0.9], 0.5) == []
    assert stream_triggers([0.9, 0.9, 0.9], 0.5) == [1]


def test_refractory_merges_triggers():
    # 2000 ms at 80 ms hop = 25 hops: pairs inside the window are merged, one after it fires again
    probs = np.zeros(80)
    for i in (2, 3, 10, 11, 40, 41):
        probs[i] = 0.9
    assert stream_triggers(probs, 0.5) == [3, 41]


def test_refractory_boundary_and_counter_after():
    g = WakeGate(threshold=0.5)
    fired = [g.step(0.9) for _ in range(2)]
    assert fired == [False, True]
    # next 25 hops (2000 ms) are suppressed even when high
    assert not any(g.step(0.9) for _ in range(25))
    # refractory over: counter restarted from zero, so two fresh hops are needed
    assert [g.step(0.9) for _ in range(2)] == [False, True]


def test_fa_per_hour_known_spikes():
    # 1 hour of hops (45000), 3 well separated double-spikes -> 3 false alarms/hour
    probs = np.zeros(45000)
    for i in (1000, 20000, 40000):
        probs[i:i + 2] = 0.9
    assert fa_per_hour(probs, 0.5) == pytest.approx(3.0)
    assert fa_per_hour(probs, 0.95) == 0.0


def test_count_triggers_matches_wakegate_reference():
    rng = np.random.default_rng(0)
    for _ in range(20):
        probs = np.clip(rng.random(3000) * 1.3 - 0.1, 0, 1)
        for t in (0.3, 0.6, 0.8):
            assert count_triggers(probs, t) == len(stream_triggers(probs, t))


def test_threshold_search_one_hour_is_fast():
    import time
    rng = np.random.default_rng(1)
    probs = rng.random(45_000) ** 3  # ~1 h of hops
    t0 = time.time()
    thr = lowest_threshold(probs)
    assert time.time() - t0 < 20
    assert fa_per_hour(probs, thr) <= 0.1
    # lowest: the next lower candidate violates the budget (or this is the smallest value)
    lower = np.unique(probs)[np.unique(probs) < thr]
    if len(lower):
        assert fa_per_hour(probs, float(lower[-1])) > 0.1


def test_stream_probs_chunks_equals_concatenated_stream():
    torch.manual_seed(0)
    m = WakeNet().eval()
    rng = np.random.default_rng(0)
    parts = [(0.1 * rng.standard_normal(n)).astype(np.float32) for n in (9000, 30000, 700, 25000)]
    whole = stream_probs(m, np.concatenate(parts))
    chunked, total = stream_probs_chunks(m, parts)
    assert total == 64700 and chunked.shape == whole.shape
    assert np.allclose(chunked, whole, atol=1e-6)


def _cfgs(tmp_path):
    root = str(tmp_path / "synth")
    make_root(root, per_split=3)
    return root


def test_gate_calibrates_on_val_reports_on_test(tmp_path):
    root = _cfgs(tmp_path)
    torch.manual_seed(0)
    m = WakeNet().eval()
    res = gate(m, [root], min_negative_hours=0.0, seed=0)
    assert isinstance(res, GateResult)
    from wakeword.train import load_rows
    val_rows = [r for r in load_rows([root]) if r.split == "val"]
    val_probs, _ = stream_probs_chunks(m, _neg_chunks(val_rows))
    assert res.threshold == pytest.approx(min(lowest_threshold(val_probs), 1.0))
    assert res.negative_hours > 0
    assert res.passed == (res.detection_rate >= 0.95 and res.fa_per_hour <= 0.1)


def test_gate_fails_when_negative_stream_too_short(tmp_path):
    root = _cfgs(tmp_path)
    res = gate(WakeNet().eval(), [root], min_negative_hours=10.0, seed=0)
    assert res.passed is False and res.negative_hours < 10.0
    assert "negative stream" in res.reason and "10" in res.reason


def test_gate_owner_positives_only_dir_adds_rows(tmp_path):
    root = _cfgs(tmp_path)
    owner = str(tmp_path / "owner")
    make_root(owner, splits=("train",), per_split=2, labels=("positive",), tag="owner")
    m = WakeNet().eval()
    base = gate(m, [root], min_negative_hours=0.0, seed=0)
    res = gate(m, [root], [owner], min_negative_hours=0.0, seed=0)
    assert res.negative_hours == pytest.approx(base.negative_hours)  # negatives still from the roots
    assert res.threshold == base.threshold
    assert res.detection_rate * 5 == pytest.approx(round(res.detection_rate * 5), abs=1e-6)  # 3 + 2 positives


def test_gate_owner_negatives_extend_negative_stream(tmp_path):
    root = _cfgs(tmp_path)
    owner = str(tmp_path / "owner")
    make_root(owner, splits=("train",), per_split=2, labels=("positive", "negative"), tag="owner")
    m = WakeNet().eval()
    base = gate(m, [root], min_negative_hours=0.0, seed=0)
    res = gate(m, [root], [owner], min_negative_hours=0.0, seed=0)
    assert res.negative_hours > base.negative_hours
