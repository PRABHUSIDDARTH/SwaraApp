"""Record "hey swara" from the owner / friends (16 kHz mono WAV) with explicit consent.

Needs `pip install sounddevice` (imported lazily). Nothing is written unless the speaker types
exactly "y" at the consent prompt; rows then carry licence "own", source "recording", consent "yes".

CLI: python -m wakeword.data.record --speaker alice --out data/recorded [--count 30]
"""
from __future__ import annotations

import argparse
import os

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

from .manifest import Row, append_rows

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


def record_session(speaker: str, count: int, out_dir: str, *, capture=sounddevice_capture,
                   capture_sr: int = SR, seconds: float = 2.0, input_fn=input,
                   split: str = "train") -> list[Row]:
    """Prompt, record `count` takes and append manifest rows. Returns [] (writing nothing)
    unless the speaker typed "y"."""
    if input_fn(CONSENT_PROMPT).strip() != "y":
        return []
    spk_dir = os.path.join(out_dir, "positive")
    os.makedirs(spk_dir, exist_ok=True)
    rows = []
    for i in range(count):
        input_fn(f'[{i + 1}/{count}] press Enter, then say "{PHRASE}": ')
        wav = _to_16k_mono(capture(seconds, capture_sr), capture_sr)
        rel = f"positive/rec_{speaker}_{i:03d}.wav"
        sf.write(os.path.join(out_dir, rel), wav, SR, subtype="PCM_16")
        rows.append(Row(path=rel, label="positive", source="recording", source_url="",
                        author=speaker, licence="own", attribution=f"{speaker} (own recording)",
                        speaker=speaker, split=split, consent="yes"))
    append_rows(rows, out_dir)
    return rows


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--speaker", required=True)
    ap.add_argument("--out", default="data/recorded")
    ap.add_argument("--count", type=int, default=30)
    args = ap.parse_args(argv)
    rows = record_session(args.speaker, args.count, args.out)
    print(f"recorded {len(rows)} takes" if rows else "no consent given: nothing recorded")


if __name__ == "__main__":
    main()
