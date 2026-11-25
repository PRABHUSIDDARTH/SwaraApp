"""Record "hey swara" from the owner / friends (16 kHz mono WAV) with explicit consent.

Needs `pip install sounddevice` (imported lazily). Nothing is written unless the speaker types
exactly "y" at the consent prompt; rows then carry licence "own", source "recording", consent "yes".

CLI: python -m wakeword.data.record --speaker alice --out data/recorded [--count 30] [--split train|val|test]
"""
from __future__ import annotations

import argparse
import os
import re
from datetime import datetime, timezone

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

from .manifest import MANIFEST_NAME, SPLITS, Row, append_rows, read_manifest

PHRASE = "hey swara"
SR = 16000
CONSENT_PROMPT = (
    f'Your voice saying "{PHRASE}" will be stored and used to train the open Swara wake-word model, '
    "which ships in the Swara app (free, on Google Play). Type y to consent, anything else to cancel: ")


def sounddevice_capture(seconds: float, sr: int) -> np.ndarray:
    import sounddevice as sd  # lazy: pip install sounddevice
    data = sd.rec(int(seconds * sr), samplerate=sr, channels=1, dtype="float32")
    sd.wait()
    return data


def _to_16k_mono(x: np.ndarray, sr: int) -> np.ndarray:
    x = np.asarray(x, dtype=np.float32)
    if x.ndim == 2:
        x = x.mean(axis=1)
    if sr != SR:
        g = np.gcd(SR, sr)
        x = resample_poly(x, SR // g, sr // g).astype(np.float32)
    return x


SPEAKER_RE = re.compile(r"^[a-z0-9_-]+$")


def _next_index(out_dir: str, speaker: str, existing: list[Row]) -> int:
    """Continue numbering after any existing takes (files or manifest rows) for this speaker."""
    pat = re.compile(rf"^rec_{re.escape(speaker)}_(\d+)\.wav$")
    used = [-1]
    for r in existing:
        m = pat.match(os.path.basename(r.path))
        if m:
            used.append(int(m.group(1)))
    pdir = os.path.join(out_dir, "positive")
    if os.path.isdir(pdir):
        used += [int(m.group(1)) for n in os.listdir(pdir) if (m := pat.match(n))]
    return max(used) + 1


def record_session(speaker: str, count: int, out_dir: str, *, capture=sounddevice_capture,
                   capture_sr: int = SR, seconds: float = 2.0, input_fn=input,
                   split: str = "train", now=None) -> list[Row]:
    """Prompt, record `count` takes and append a manifest row after each saved take. Returns []
    (writing nothing) unless the speaker typed "y". `speaker` must match [a-z0-9_-]+ and `split`
    must be train/val/test (a speaker lives in one split only)."""
    if not SPEAKER_RE.match(speaker or ""):
        raise ValueError(f"speaker id {speaker!r} must match [a-z0-9_-]+")
    if split not in SPLITS:
        raise ValueError(f"split {split!r} must be one of {SPLITS}")
    manifest_path = os.path.join(out_dir, MANIFEST_NAME)
    existing = read_manifest(manifest_path) if os.path.isfile(manifest_path) else []
    key = f"recording:{speaker}"
    if any(r.speaker == key and r.split != split for r in existing):
        raise ValueError(f"speaker {speaker!r} already recorded in a different split")
    if input_fn(CONSENT_PROMPT).strip() != "y":
        return []
    stamp = (now or (lambda: datetime.now(timezone.utc)))().strftime("%Y-%m-%dT%H:%M:%SZ")
    os.makedirs(os.path.join(out_dir, "positive"), exist_ok=True)
    first = _next_index(out_dir, speaker, existing)
    rows = []
    for i in range(count):
        input_fn(f'[{i + 1}/{count}] press Enter, then say "{PHRASE}": ')
        wav = _to_16k_mono(capture(seconds, capture_sr), capture_sr)
        rel = f"positive/rec_{speaker}_{first + i:03d}.wav"
        row = Row(path=rel, label="positive", source="recording", source_url="",
                  author=speaker, licence="own", attribution=f"{speaker} (own recording)",
                  speaker=key, split=split, consent="yes", consent_ts=stamp)
        sf.write(os.path.join(out_dir, rel), wav, SR, subtype="PCM_16")
        append_rows([row], out_dir)  # row lands right after its WAV, so a crash leaves no orphans
        rows.append(row)
    return rows


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--speaker", required=True)
    ap.add_argument("--out", default="data/recorded")
    ap.add_argument("--count", type=int, default=30)
    ap.add_argument("--split", choices=SPLITS, default="train",
                    help="hold real speakers out for val/test (owner + >=2 other speakers)")
    args = ap.parse_args(argv)
    rows = record_session(args.speaker, args.count, args.out, split=args.split)
    print(f"recorded {len(rows)} takes" if rows else "no consent given: nothing recorded")


if __name__ == "__main__":
    main()
