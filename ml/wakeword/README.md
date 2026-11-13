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

## Model, training, gate, export (Task 11)

`WakeNet` (`wakeword/model.py`): input `audio` float32 [B,16000] (16 kHz, [-1,1]), output `prob` float32 [B].
Log-mel front end inside the module (n_fft 400, hop 160, 40 mels, `log(x+1e-6)`), 5 depthwise-separable blocks, GAP.
Install torch from the CPU index first: `pip install --index-url https://download.pytorch.org/whl/cpu torch`.

Run later, with the owner (real data; not run in CI):

    # 1. data (see above), then train: merges data/external, data/synth, data/recorded, data/noise
    python -m wakeword.train --config configs/base.yaml --out checkpoints      # writes checkpoints/best.pt
    # 2. gate: test-split positives + held-out long negatives (streaming, 80 ms hop, 2-consecutive, 2 s refractory)
    python -m wakeword.evaluate --ckpt checkpoints/best.pt                     # exit 0 only when passed: true
    #    owner recordings later: a dir with manifest.csv (all rows used, as recorded)
    python -m wakeword.evaluate --ckpt checkpoints/best.pt --eval data/eval
    # 3. export (refuses when the gate fails; report the numbers to the owner instead)
    python -m wakeword.export --ckpt checkpoints/best.pt --out ../../app/src/main/assets/voice/wakeword/

The gate threshold is the lowest value with <= 0.1 false triggers/hour; it passes at detection >= 0.95.
`WakeGate` in `wakeword/evaluate.py` is the reference for the Android WakeGate (Task 12): trigger on the
2nd consecutive hop with prob >= threshold; a sub-threshold hop resets the counter; after a trigger, the next
2000 ms (25 hops) are ignored and the counter restarts at 0.
