import numpy as np
import pytest
import torch

from tests.conftest import make_root
from wakeword.evaluate import GateResult, WakeGate, fa_per_hour, gate, stream_triggers
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


def test_gate_runs_and_threshold_rule(tmp_path):
    root = str(tmp_path / "eval")
    make_root(root, splits=("test",), per_split=4)
    torch.manual_seed(0)
    res = gate(WakeNet().eval(), [root], split="test", seed=0)
    assert isinstance(res, GateResult)
    assert 0.0 <= res.detection_rate <= 1.0 and 0.0 <= res.threshold <= 1.0
    assert res.fa_per_hour <= 0.1  # lowest threshold meeting the FA budget is chosen
    assert res.passed == (res.detection_rate >= 0.95)


def test_gate_external_eval_dir_uses_all_rows(tmp_path):
    root = str(tmp_path / "owner")
    make_root(root, splits=("train",), per_split=2)  # an owner-recording dir: split is ignored
    res = gate(WakeNet().eval(), [root], split=None, mix_positives=False, seed=0)
    assert isinstance(res, GateResult)
