"""Decode MUSDB18 .stem.mp4 files into float32 numpy arrays.

Stream layout (MUSDB18): 0=mixture, 1=drums, 2=bass, 3=other, 4=vocals.
The accompaniment is drums+bass+other, and the training mixture is rebuilt as
accompaniment+vocals so targets are sample-consistent with the input.
Output: data/npy/<split>/<name>.npz with arrays acc[2,T] and voc[2,T] at 44.1 kHz.
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


def main() -> int:
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
