"""Convert any source audio to 44.1 kHz 16-bit FLAC, keeping its channel count (spec 3.1)."""
from __future__ import annotations

import os
import subprocess

import soundfile as sf


class PrepError(RuntimeError):
    pass


def to_flac(src: str, dst: str) -> float:
    """Write dst atomically (.part then rename); return the duration in seconds."""
    part = dst + ".part"
    os.makedirs(os.path.dirname(os.path.abspath(dst)), exist_ok=True)
    cmd = ["ffmpeg", "-nostdin", "-v", "error", "-y", "-i", src, "-map", "0:a:0", "-vn",
           "-ar", "44100", "-sample_fmt", "s16", "-c:a", "flac", "-f", "flac", part]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        if os.path.exists(part):
            os.remove(part)
        raise PrepError(f"ffmpeg failed for {src}: {proc.stderr.strip()[:300]}")
    try:
        duration = sf.info(part).duration
    except RuntimeError as e:
        os.remove(part)
        raise PrepError(f"unreadable output for {src}: {e}") from e
    os.replace(part, dst)
    return duration
