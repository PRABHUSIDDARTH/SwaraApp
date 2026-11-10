import json

import numpy as np
import soundfile as sf
import torch

from korokae_v2.export import export_onnx
from korokae_v2.listening_test import assign_ab, loudest_window, prepare, score
from korokae_v2.model import KorokaeNetV2

SR = 44100


def test_chorus_heuristic_picks_loudest_30s():
    t = np.arange(60 * SR) / SR
    x = 0.01 * np.sin(2 * np.pi * 220 * t)
    x[20 * SR:50 * SR] *= 50
    assert abs(loudest_window(np.stack([x, x]), SR, 30.0) - 20.0) <= 1.0


def test_short_song_uses_whole_song():
    x = np.zeros((2, 10 * SR))
    assert loudest_window(x, SR, 30.0) == 0.0


def test_ab_order_is_random_but_recorded():
    a = assign_ab(20, seed=5)
    assert set(a) == {"v1", "v2"} and assign_ab(20, seed=5) == a


def _songs(tmp_path):
    rows = ["path,start_s"]
    for i in range(3):
        p = tmp_path / f"song{i}.wav"
        t = np.arange(4 * SR) / SR
        sf.write(str(p), 0.2 * np.sin(2 * np.pi * (200 + 50 * i) * t), SR)
        rows.append(f"{p},{'1.0' if i == 0 else ''}")
    (tmp_path / "songs.csv").write_text("\n".join(rows) + "\n")
    torch.manual_seed(0)
    export_onnx(KorokaeNetV2(n_blocks=1, d=16, heads=2), str(tmp_path / "v2.onnx"))
    return str(tmp_path / "songs.csv"), str(tmp_path / "v2.onnx")


def test_prepare_writes_clips_key_and_self_contained_html(tmp_path):
    songs, onnx = _songs(tmp_path)
    out = tmp_path / "lt"
    prepare(songs, onnx, str(out), clip_seconds=2.0, seed=11)
    key = json.loads((out / "key.json").read_text())
    assert len(key["clips"]) == 3 and key["clips"][0]["start_s"] == 1.0
    for c in key["clips"]:
        assert {c["A"], c["B"]} == {"v1", "v2"}
        for side in ("A", "B", "orig"):
            assert (out / "clips" / f"{c['index']}_{side}.m4a").stat().st_size > 0
    html = (out / "index.html").read_text()
    assert "http://" not in html and "https://" not in html and "answers.json" in html


def _key(n, a_is):
    return {"clips": [{"index": i, "A": a_is[i], "B": "v1" if a_is[i] == "v2" else "v2"} for i in range(n)]}


def _answers(n, v2_preferred, v2_clean, v2_gone, key):
    clips = []
    for i in range(n):
        c = key["clips"][i]
        v2_side = "A" if c["A"] == "v2" else "B"
        v1_side = "B" if v2_side == "A" else "A"
        clips.append({"index": i, v2_side: {"voice_gone": v2_gone[i], "music_clean": v2_clean[i]},
                      v1_side: {"voice_gone": 2, "music_clean": 2},
                      "better": v2_side if i < v2_preferred else v1_side})
    return {"clips": clips}


def test_score_criteria():
    key = _key(100, ["v2" if i % 2 else "v1" for i in range(100)])
    fours = [4] * 100
    assert score(key, _answers(100, 90, fours, fours, key))["pass"]
    assert not score(key, _answers(100, 89, fours, fours, key))["pass"]
    clean = [4] * 90 + [3] * 10  # mean 3.9
    assert not score(key, _answers(100, 100, clean, fours, key))["pass"]
    assert not score(key, _answers(100, 100, fours, clean, key))["pass"]


def test_score_unblinds_with_key():
    key = _key(2, ["v2", "v1"])
    ans = _answers(2, 2, [5, 5], [5, 5], key)
    s = score(key, ans)
    assert s["v2_preferred"] == 1.0 and s["v2_music_clean"] == 5.0 and s["v1_music_clean"] == 2.0
