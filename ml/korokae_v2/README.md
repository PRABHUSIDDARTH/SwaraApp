# Korokae v2 — training and export

Our own band-split RNN vocal remover (spec: `docs/superpowers/specs/2026-11-09-korokae-v2-design.md`).
No third-party models, weights or separation code. Training data must be CC0 / CC BY / own.

## Setup

```bash
cd ml/korokae_v2
python3 -m venv .venv
.venv/bin/pip install --index-url https://download.pytorch.org/whl/cpu torch
.venv/bin/pip install -r requirements.txt
.venv/bin/pytest -q
```

## Layout

- `korokae_v2/dsp.py` — band layout, STFT/iSTFT and chunked separation (the Java engine mirrors it)
- `korokae_v2/model.py` — KorokaeNet v2 band-split RNN (default 8 blocks x 256 hidden, ~21.0M params)
