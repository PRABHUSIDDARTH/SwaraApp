# Korokae real-time vocal removal model

Small streaming mask network that removes vocals on-device, inside the Media3
audio pipeline (`com.psthetech.swara.korokae`). No files are rendered; Korokae
works instantly on any 16-bit stereo 44.1/48 kHz track.

## Model

- STFT: sqrt-Hann window, N_FFT 1024, hop 512 (~11.6 ms hop at 44.1 kHz).
- Input: `(log1p|mid| - mean) / std` over 513 bins of the mid channel.
- Net: Linear 513->256 + ReLU, 2-layer unidirectional GRU(256), Linear->513 + sigmoid.
- Output: accompaniment mask applied to both L and R spectra, then overlap-add.
- Causal, so it streams with a fixed latency of `N_FFT - 1` samples (~23 ms).

## Reproduce

Requires Python 3 with `torch` (CUDA optional), `numpy`, and `ffmpeg`.

```bash
cd ml/korokae
# 1. Put MUSDB18 *.stem.mp4 files in data/train and data/test (git-ignored)
python3 prepare_data.py          # decodes stems to data/npy/*.npz
python3 train.py --steps 12000   # writes checkpoints/korokae_best.pt + train.log
python3 export.py                # writes the app asset + Java parity fixture
```

`export.py` produces:

- `app/src/main/assets/models/korokae_v1.bin` ("KRK1" header + little-endian float32 weights), shipped in the APK.
- `app/src/test/resources/korokae_parity.bin`, used by `KorokaeEngineParityTest` to verify the Java engine matches PyTorch.

Datasets, decoded audio, and checkpoints are git-ignored (see the root `.gitignore`).
MUSDB18 is licensed for research use; check its terms before distributing a model trained on it commercially.

## v2 experiment results (2026-11-03)

Median over the 50 MUSDB18 test tracks (`evaluate.py`; one 12,000-step run per config, seed 0).

| Model | Accompaniment SDR | Vocal attenuation | Passthrough SDR |
| --- | --- | --- | --- |
| v1 (shipped) | 8.95 dB | 5.73 dB | 16.37 dB |
| v2 Standard | 9.08 dB | 7.18 dB | 16.61 dB |
| v2 High | 8.51 dB | 7.26 dB | 16.58 dB |

`gate.py` **fails**: Standard gains 0.13 dB SDR over v1 where the spec requires at least 1.5 dB, and High does not beat Standard on SDR.
v2 Standard is better than v1 on all three metrics, but modestly. v1 stays the shipped model and `KRK2` export (plan Task 7) has not been done.
A/B excerpts for listening: `python3 render_ab.py` writes `results/ab/` (git-ignored).

Train vs test (25 evenly spaced tracks each, median accompaniment SDR): v1 11.32 vs 8.07 dB, v2 Standard 11.58 vs 8.71 dB, v2 High 11.22 vs 8.45 dB.
All three models show a gap of about 3 dB, so the 94 training tracks look like the shared limit. High is below Standard even on training tracks,
so it is not overfitting more; at this step count it is under-trained or optimisation-limited rather than capacity-limited.
Untested levers: more data (licence check first), longer or differently scheduled training for High, and the vocal-residual loss weight.
