# Swara wake-word data pipeline ("hey swara")

Licence-audited data only: CC0, CC BY 3.0/4.0, or our own audio (commercial Play Store release).

    pip install -r requirements.txt
    python -m pytest -q
    python -m wakeword.data.fetch --dry-run          # licence gate + plan, no download
    python -m wakeword.data.synth --out data/synth   # needs: pip install kokoro soundfile (+ espeak-ng, training machine only)
    python -m wakeword.data.record --speaker alice   # needs: pip install sounddevice; consent ("y") required
    python -m wakeword.data.noise --out data/noise   # synthetic room-noise backgrounds
    # record: --split val/test holds a real speaker out of training
    python -m wakeword.data.manifest audit --root data/synth

`mix.make_example(clip, background, snr_db, rng)` builds the 1.0 s training window (clip ends in the last 200 ms).
Outputs under `data/`, `out/`, `checkpoints/` are git-ignored.
