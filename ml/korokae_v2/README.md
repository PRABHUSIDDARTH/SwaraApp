# Korokae v2 — training and export

Our own band-split transformer vocal remover (spec: `docs/superpowers/specs/2026-11-09-korokae-v2-design.md`).
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
- `korokae_v2/model.py` — KorokaeNet v2 band-split transformer (default 4 blocks x d 96, 23 bands, ~3.2M params)
- `korokae_v2/export.py` — ONNX export (opset 17, input `spec`, output `mask`) and KRP2 parity fixture; `python -m korokae_v2.export --untrained --blocks 4 --dim 96 --out-dir ../../app/src/androidTest/assets`
- `configs/base.yaml` — the model size and runtime settings chosen by the speed gate

## S21+ speed gate (4-minute song; limits 60 s, 500 MB peak)

Untrained weights, float32, ONNX Runtime 1.31.1 CPU, run via `adb install -r` + `am instrument`.

| Model | Params | ONNX | Threads | Arena | Time | Peak | Result |
|---|---|---|---|---|---|---|---|
| BS-RNN 8 blocks x 256 hidden, 39 bands | 21.0M | 84.3 MB | 4 | on | 594.8 s | 1408 MB | fail |
| BS-RNN 6 blocks x 128 hidden, 39 bands | 10.9M | 43.8 MB | 4 | on | 186.2 s | 912 MB | fail |
| BS-transformer 4 blocks x d 96, 23 bands | 3.2M | 13.0 MB | 4 | on | 43.0 s | 545 MB | fail (memory) |
| **BS-transformer 4 blocks x d 96, 23 bands** | **3.2M** | **13.0 MB** | **4** | **off** | **51.1 s** | **378 MB** | **pass** |
| BS-transformer 4 blocks x d 96, 23 bands | 3.2M | 13.0 MB | 6 | off | 58.0 s | 378 MB | pass (slower) |

The phone sustains roughly 10-20 GMAC/s, so the network has to stay near 1 MMAC per
(band, frame) cell; the LSTM designs were 3-10x over that. Time attention runs at half the frame
rate (strided conv down, transposed conv up) to stay inside it.
