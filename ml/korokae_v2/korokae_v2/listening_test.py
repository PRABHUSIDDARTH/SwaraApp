"""Local blind A/B listening test, v1 vs v2 (spec 7.3). Laptop only: nothing is uploaded, and
the page loads nothing from the network.

  python -m korokae_v2.listening_test prepare --songs songs.csv --v2 korokae_v2.onnx --out DIR
  python -m korokae_v2.listening_test score --dir DIR --answers answers.json

songs.csv has columns path,start_s; an empty start_s picks the loudest 30 s (chorus heuristic).
"""
from __future__ import annotations

import argparse
import csv
import html
import json
import os
import random
import subprocess
import tempfile

import numpy as np
import onnxruntime
import soundfile as sf
import torch

from . import dsp
from .v1_baseline import load_v1, v1_instrumental

SR = 44100
PASS_PREFERRED = 0.9
PASS_MEAN = 4.0


def decode(path: str) -> np.ndarray:
    """Any audio file -> float32 [2, L] at 44.1 kHz (mono duplicated)."""
    raw = subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-i", path, "-f", "f32le", "-ac", "2",
                          "-ar", str(SR), "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype="<f4").reshape(-1, 2).T.copy()


def loudest_window(x: np.ndarray, sr: int, seconds: float, hop_s: float = 1.0) -> float:
    """Start (s) of the highest-RMS window of `seconds`, on a hop_s grid; 0 if the song is shorter."""
    n, hop = int(seconds * sr), int(hop_s * sr)
    if x.shape[1] <= n:
        return 0.0
    energy = np.concatenate([[0.0], np.cumsum((x.astype(np.float64) ** 2).sum(axis=0))])
    starts = np.arange(0, x.shape[1] - n + 1, hop)
    return float(starts[np.argmax(energy[starts + n] - energy[starts])] / sr)


def assign_ab(n: int, seed: int) -> list[str]:
    """Which model plays as "A" for each clip."""
    rng = random.Random(seed)
    return [rng.choice(["v1", "v2"]) for _ in range(n)]


def _encode(x: np.ndarray, dst: str) -> None:
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        sf.write(tmp.name, np.clip(x, -1, 1).T, SR, subtype="FLOAT")
        subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", "-i", tmp.name, "-c:a", "aac",
                        "-b:a", "256k", dst], check=True)


def prepare(songs_csv: str, v2_onnx: str, out: str, clip_seconds: float = 30.0,
            seed: int | None = None) -> None:
    seed = random.SystemRandom().randrange(1 << 30) if seed is None else seed
    os.makedirs(os.path.join(out, "clips"), exist_ok=True)
    with open(songs_csv, newline="", encoding="utf-8") as f:
        songs = list(csv.DictReader(f))
    v1 = load_v1()
    sess = onnxruntime.InferenceSession(v2_onnx)

    def v2_mask(spec: torch.Tensor) -> torch.Tensor:
        return torch.from_numpy(sess.run(["mask"], {"spec": spec.numpy()})[0])

    sides = assign_ab(len(songs), seed)
    clips = []
    for i, song in enumerate(songs):
        x = decode(song["path"])
        start = (float(song["start_s"]) if (song.get("start_s") or "").strip()
                 else loudest_window(x, SR, clip_seconds))
        clip = torch.from_numpy(x[:, int(start * SR):int(start * SR) + int(clip_seconds * SR)])
        inst = {"v1": v1_instrumental(v1, clip).numpy(), "v2": dsp.separate(clip, v2_mask).numpy()}
        a, b = sides[i], "v1" if sides[i] == "v2" else "v2"
        _encode(clip.numpy(), os.path.join(out, "clips", f"{i}_orig.m4a"))
        _encode(inst[a], os.path.join(out, "clips", f"{i}_A.m4a"))
        _encode(inst[b], os.path.join(out, "clips", f"{i}_B.m4a"))
        clips.append({"index": i, "song": song["path"], "start_s": start, "A": a, "B": b})
    with open(os.path.join(out, "key.json"), "w", encoding="utf-8") as f:
        json.dump({"seed": seed, "clip_seconds": clip_seconds, "clips": clips}, f, indent=1)
    with open(os.path.join(out, "index.html"), "w", encoding="utf-8") as f:
        f.write(_page(len(clips)))


def _page(n: int) -> str:
    def rating(i: int, side: str, what: str, label: str) -> str:
        opts = "".join(f'<label><input type="radio" name="{i}_{side}_{what}" value="{v}">{v}</label>'
                       for v in range(1, 6))
        return f"<div class=r><span>{html.escape(label)}</span>{opts}</div>"

    rows = []
    for i in range(n):
        sides = "".join(
            f"<div class=side><h3>{s}</h3><audio controls preload=none src='clips/{i}_{s}.m4a'></audio>"
            f"{rating(i, s, 'voice_gone', 'Voice gone')}{rating(i, s, 'music_clean', 'Music clean')}</div>"
            for s in ("A", "B"))
        better = "".join(f'<label><input type="radio" name="{i}_better" value="{s}">{s}</label>' for s in "AB")
        rows.append(f"<section><h2>Clip {i + 1}</h2><p>Original: <audio controls preload=none "
                    f"src='clips/{i}_orig.m4a'></audio></p><div class=pair>{sides}</div>"
                    f"<div class=r><span>Which is better?</span>{better}</div></section>")
    return f"""<!doctype html><html><head><meta charset="utf-8"><title>Korokae listening test</title>
<style>body{{font-family:system-ui,sans-serif;max-width:900px;margin:auto;padding:16px;background:#fafafa}}
section{{background:#fff;border:1px solid #ddd;border-radius:8px;padding:12px;margin:12px 0}}
.pair{{display:flex;gap:16px;flex-wrap:wrap}}.side{{flex:1;min-width:260px}}
.r{{margin:6px 0}}.r span{{display:inline-block;width:130px}}label{{margin-right:8px}}
button{{font-size:16px;padding:8px 16px}}</style></head><body>
<h1>Korokae listening test</h1>
<p>For each clip, listen to A and B (instrumentals of the original). Rate 1-5 how fully the voice
is gone and how clean the music sounds, then pick the better one. Export saves answers.json.</p>
{''.join(rows)}
<button onclick="exportAnswers()">Export answers.json</button>
<script>
function val(n){{const e=document.querySelector('input[name="'+n+'"]:checked');return e?e.value:null}}
function exportAnswers(){{
  const clips=[];
  for(let i=0;i<{n};i++){{
    const c={{index:i,better:val(i+'_better')}};
    for(const s of ['A','B']) c[s]={{voice_gone:+val(i+'_'+s+'_voice_gone')||null,
                                     music_clean:+val(i+'_'+s+'_music_clean')||null}};
    clips.push(c);
  }}
  const blob=new Blob([JSON.stringify({{clips}},null,1)],{{type:'application/json'}});
  const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='answers.json';a.click();
}}
</script></body></html>"""


def score(key: dict, answers: dict) -> dict:
    """Unblind answers with the key and apply the spec section 1 criteria."""
    sides = {c["index"]: c for c in key["clips"]}
    pref, ratings = [], {"v1": {"voice_gone": [], "music_clean": []},
                         "v2": {"voice_gone": [], "music_clean": []}}
    for a in answers["clips"]:
        k = sides[a["index"]]
        if a.get("better") in ("A", "B"):
            pref.append(k[a["better"]] == "v2")
        for side in ("A", "B"):
            for what in ("voice_gone", "music_clean"):
                v = (a.get(side) or {}).get(what)
                if v:
                    ratings[k[side]][what].append(float(v))
    mean = lambda xs: float(np.mean(xs)) if xs else float("nan")
    out = {"clips": len(answers["clips"]), "v2_preferred": mean(pref)}
    for who in ("v1", "v2"):
        for what in ("voice_gone", "music_clean"):
            out[f"{who}_{what}"] = mean(ratings[who][what])
    out["pass"] = bool(out["v2_preferred"] >= PASS_PREFERRED
                       and out["v2_music_clean"] >= PASS_MEAN and out["v2_voice_gone"] >= PASS_MEAN)
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("prepare")
    p.add_argument("--songs", required=True)
    p.add_argument("--v2", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--clip-seconds", type=float, default=30.0)
    s = sub.add_parser("score")
    s.add_argument("--dir", required=True)
    s.add_argument("--answers", required=True)
    a = ap.parse_args()
    if a.cmd == "prepare":
        prepare(a.songs, a.v2, a.out, a.clip_seconds)
        print(f"open {os.path.join(a.out, 'index.html')} in a browser")
    else:
        with open(os.path.join(a.dir, "key.json"), encoding="utf-8") as f:
            key = json.load(f)
        with open(a.answers, encoding="utf-8") as f:
            res = score(key, json.load(f))
        for k, v in res.items():
            print(f"{k}: {v}")
        print("PASS" if res["pass"] else "FAIL")


if __name__ == "__main__":
    main()
