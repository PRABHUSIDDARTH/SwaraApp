"""Write 5 short LibriSpeech test clips (CC BY 4.0) for MoonshineSttInstrumentedTest.

Source: Hugging Face dataset hf-internal-testing/librispeech_asr_dummy (a 73-utterance
slice of LibriSpeech validation-clean), parquet pinned to one dataset revision + SHA-256.
Writes 16 kHz mono 16-bit WAVs to app/src/androidTest/assets/voice/clips/.

Run from the repo root:  python -I ml/voice_assets/fetch_clips.py
"""
import hashlib
import io
import os
import sys
import tempfile
import urllib.request

import numpy as np
import pyarrow.parquet as pq
import soundfile as sf

REV = "5be91486e11a2d616f4ec5db8d3fd248585ac07a"
URL = ("https://huggingface.co/datasets/hf-internal-testing/librispeech_asr_dummy/resolve/"
       + REV + "/clean/validation-00000-of-00001.parquet")
SHA = "4e69a06fa5edc90921e5e7e39a7084881f8b3ed9c805c574f4f39c6fde27c603"
CLIPS = ["1272-135031-0011", "1272-135031-0008", "1272-135031-0004",
         "1272-141231-0000", "1272-128104-0008"]
REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEST = os.path.join(REPO, "app", "src", "androidTest", "assets", "voice", "clips")


def main():
    with tempfile.TemporaryDirectory() as stage:
        path = os.path.join(stage, "librispeech.parquet")
        with urllib.request.urlopen(URL) as r, open(path, "wb") as f:
            f.write(r.read())
        got = hashlib.sha256(open(path, "rb").read()).hexdigest()
        if got != SHA:
            sys.exit("SHA-256 mismatch: got " + got)
        table = pq.read_table(path).to_pylist()
    print("parquet sha256 " + got)
    os.makedirs(DEST, exist_ok=True)
    for row in table:
        audio, sr = sf.read(io.BytesIO(row["audio"]["bytes"]), dtype="float32")
        secs = len(audio) / sr
        if not CLIPS:
            print("%-22s %5.2fs %s" % (row["id"], secs, row["text"]))
            continue
        if row["id"] not in CLIPS:
            continue
        assert sr == 16000 and audio.ndim == 1 and secs < 8, (row["id"], sr, secs)
        out = os.path.join(DEST, row["id"] + ".wav")
        sf.write(out, np.clip(audio, -1, 1), 16000, subtype="PCM_16")
        print("wrote %s  %.2fs  %s" % (os.path.basename(out), secs, row["text"]))


if __name__ == "__main__":
    main()
