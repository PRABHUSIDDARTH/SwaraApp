from gate import check

V1 = {"acc_sdr_median": 8.95, "vocal_attenuation_median_db": 5.7, "passthrough_sdr_median": 16.4}
STD = {"acc_sdr_median": 10.6, "vocal_attenuation_median_db": 9.0, "passthrough_sdr_median": 17.0}
HIGH = {"acc_sdr_median": 11.5, "vocal_attenuation_median_db": 11.0, "passthrough_sdr_median": 18.0}


def test_gate_passes_when_all_rules_met():
    assert check(V1, STD, HIGH) == []


def test_gate_fails_when_sdr_gain_below_1_5():
    failures = check(V1, {**STD, "acc_sdr_median": 10.4}, HIGH)
    assert len(failures) == 1 and "1.5" in failures[0]


def test_gate_fails_when_passthrough_regresses():
    failures = check(V1, {**STD, "passthrough_sdr_median": 15.0}, HIGH)
    assert len(failures) == 1 and "passthrough" in failures[0]


def test_gate_fails_when_high_not_better_than_standard():
    failures = check(V1, STD, {**HIGH, "acc_sdr_median": 10.0, "vocal_attenuation_median_db": 8.0})
    assert len(failures) == 2
