"""Decode MUSDB18 .stem.mp4 files into float32 numpy arrays.

Stream layout (MUSDB18): 0=mixture, 1=drums, 2=bass, 3=other, 4=vocals.
The accompaniment is drums+bass+other, and the training mixture is rebuilt as
accompaniment+vocals so targets are sample-consistent with the input.
Output: data/npy/<split>/<name>.npz with arrays acc[2,T] and voc[2,T] at 44.1 kHz.

`--codec` additionally writes data/npy_codec/<split>/<name>.npz: both stems of a track
round-tripped through the same lossy codec (MP3 128 kbps or AAC 96 kbps, chosen per track),
so real-world compression artifacts are in the training mixtures. Codecs add a small delay,
so a codec track's stems are only ever used together, never mixed with the clean set.
"""
import pathlib
import subprocess
import sys

import numpy as np

SR = 44100
ROOT = pathlib.Path(__file__).resolve().parent / "data"


def decode(path: pathlib.Path, stream: int) -> np.ndarray:
    cmd = ["ffmpeg", "-v", "error", "-i", str(path), "-map", f"0:{stream}",
           "-f", "f32le", "-ac", "2", "-ar", str(SR), "-"]
    raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).T.copy()


def codec_roundtrip(x: np.ndarray, codec: str) -> np.ndarray:
    """Encode x [2, T] float32 with `codec` (mp3 or aac) and decode it back to [2, T']."""
    enc = {"mp3": ["-c:a", "libmp3lame", "-b:a", "128k", "-f", "mp3"],
           "aac": ["-c:a", "aac", "-b:a", "96k", "-f", "adts"]}[codec]
    fmt = {"mp3": "mp3", "aac": "aac"}[codec]
    raw = np.ascontiguousarray(x.T, dtype="<f4").tobytes()
    packed = subprocess.run(
        ["ffmpeg", "-v", "error", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "pipe:0"] + enc + ["pipe:1"],
        input=raw, check=True, capture_output=True).stdout
    out = subprocess.run(
        ["ffmpeg", "-v", "error", "-f", fmt, "-i", "pipe:0", "-f", "f32le", "-ac", "2", "-ar", str(SR), "pipe:1"],
        input=packed, check=True, capture_output=True).stdout
    return np.frombuffer(out, dtype=np.float32).reshape(-1, 2).T.copy()


def prepare_codec() -> int:
    count = 0
    for split in ("train", "test"):
        src_dir = ROOT / "npy" / split
        out_dir = ROOT / "npy_codec" / split
        out_dir.mkdir(parents=True, exist_ok=True)
        for i, src in enumerate(sorted(src_dir.glob("*.npz"))):
            target = out_dir / src.name
            if target.exists():
                count += 1
                continue
            d = np.load(src)
            codec = "mp3" if i % 2 == 0 else "aac"
            acc, voc = codec_roundtrip(d["acc"], codec), codec_roundtrip(d["voc"], codec)
            n = min(acc.shape[1], voc.shape[1])
            np.savez(target, acc=acc[:, :n], voc=voc[:, :n])
            count += 1
    print(f"prepared {count} codec tracks")
    return 0


def main() -> int:
    if "--codec" in sys.argv[1:]:
        return prepare_codec()
    count = 0
    for split in ("train", "test"):
        out_dir = ROOT / "npy" / split
        out_dir.mkdir(parents=True, exist_ok=True)
        for stem in sorted((ROOT / split).glob("*.stem.mp4")):
            target = out_dir / (stem.name.replace(".stem.mp4", "") + ".npz")
            if target.exists():
                count += 1
                continue
            parts = [decode(stem, i) for i in (1, 2, 3, 4)]
            n = min(p.shape[1] for p in parts)
            acc = sum(p[:, :n] for p in parts[:3]).astype(np.float32)
            voc = parts[3][:, :n].astype(np.float32)
            np.savez(target, acc=acc, voc=voc)
            count += 1
    print(f"prepared {count} tracks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
