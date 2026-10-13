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
