"""Ship gate: a v2 model ships only if it beats v1 on every metric.

Usage: python3 gate.py   (reads results/v1.json, v2_standard.json, v2_high.json; exits 1 on failure)
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent
MIN_SDR_GAIN_DB = 1.5


def check(v1: dict, standard: dict, high: dict) -> list:
    """Returns failure messages; an empty list means the gate passes."""
    fails = []
    gain = standard["acc_sdr_median"] - v1["acc_sdr_median"]
    if gain < MIN_SDR_GAIN_DB:
        fails.append(f"standard SDR gain over v1 is {gain:.2f} dB, needs at least {MIN_SDR_GAIN_DB} dB")
    if standard["vocal_attenuation_median_db"] <= v1["vocal_attenuation_median_db"]:
        fails.append("standard vocal attenuation does not beat v1")
    if standard["passthrough_sdr_median"] < v1["passthrough_sdr_median"]:
        fails.append("standard passthrough SDR is worse than v1")
    if high["acc_sdr_median"] <= standard["acc_sdr_median"]:
        fails.append("high SDR does not beat standard")
    if high["vocal_attenuation_median_db"] <= standard["vocal_attenuation_median_db"]:
        fails.append("high vocal attenuation does not beat standard")
    return fails


def main() -> int:
    r = ROOT / "results"
    load = lambda n: json.loads((r / n).read_text())  # noqa: E731
    fails = check(load("v1.json"), load("v2_standard.json"), load("v2_high.json"))
    for f in fails:
        print("GATE FAIL:", f)
    print("GATE PASS" if not fails else f"{len(fails)} rule(s) failed")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
