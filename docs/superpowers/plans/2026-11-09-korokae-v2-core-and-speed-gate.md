# Korokae v2 — Plan 1: Model Core and Speed Gate

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build KorokaeNet v2 (band-split RNN) in PyTorch, export it to ONNX, build the Java
separation engine (STFT, chunking, ONNX Runtime, iSTFT), prove Java matches PyTorch on the
phone, and fix the model size that separates a 4-minute song in ≤ 60 s on the Galaxy S21+.

**Architecture:** One Python package (`ml/korokae_v2/korokae_v2/`) is the source of truth for
the DSP and the network. `export.py` writes an ONNX graph holding only the network (complex
spectrogram in, complex vocal mask out) plus a parity fixture. The Java engine
(`com.psthetech.swara.korokae.v2`) mirrors the Python DSP exactly and runs the ONNX graph
through a `MaskModel` interface, so the engine logic is unit-tested on the JVM with fake models
and checked against PyTorch on the device. Weights here are untrained: this plan measures speed
and correctness, not separation quality.

**Tech Stack:** Python 3 + PyTorch (CPU) + onnx + onnxruntime + pytest; Java 17 Android app;
ONNX Runtime Android `com.microsoft.onnxruntime:onnxruntime-android:1.20.0`; JUnit 4;
AndroidJUnitRunner instrumented tests.

**Spec:** `docs/superpowers/specs/2026-11-09-korokae-v2-design.md` (sections 4 and 7.1, 7.4).

**Roadmap:** Plan 2 (data, Swara-Synth, training, evaluation on Kaggle) and Plan 3 (app
integration, cache, v1 removal, credits) are written after Task 7 fixes the model size, because
both depend on it.

## Global Constraints

- No third-party model, pretrained weights or separation code; only our code on PyTorch / ONNX Runtime.
- Sample rate 44100 Hz, stereo. N_FFT 2048, hop 512.
- Band edges: 100 Hz steps below 1 kHz, 250 Hz up to 4 kHz, 500 Hz up to 8 kHz, 1 kHz up to 16 kHz, remainder as one band.
- Band feature size D = 128; block = residual BiLSTM across time then residual BiLSTM across bands; hidden 256; 8–12 blocks; 15–25M parameters at the default size.
- Mask is complex, per channel; vocals = mask × mixture STFT; instrumental = mixture − vocals.
- Inference windows: 8 s with 1 s overlap, linear crossfade.
- ONNX opset 17. STFT / iSTFT are not in the graph; they run in Java.
- Speed target: 4-minute song in ≤ 60 s on the S21+ (Exynos 2100); peak memory ≤ 500 MB.
- Parity target: Java + ONNX Runtime vs PyTorch, max abs error ≤ 1e-3.
- Generated models and fixtures are git-ignored (never commit binaries over 1 MB).
- **Deviation to confirm with the owner:** the spec says float16 weights. ONNX Runtime's CPU LSTM has no float16 kernel, so this plan exports float32 and records the file size; weight compression (float16 storage or int8 dynamic quantisation) is decided in Plan 2's export task.

## Review Focus

1. A song shorter than one window, or shorter than N_FFT (e.g. a 0.3 s jingle): the engine returns an instrumental of exactly the input length without crashing (Task 5, `shortInputKeepsLength`).
2. A length that is not a multiple of hop or window: output length equals input length exactly (Task 5, `oddLengthKeepsLength`).
3. Digital silence: the network and engine produce finite output (no NaN from normalisation) (Task 2, `test_zero_input_is_finite`).
4. Full-scale or clipped input (a ±1.0 square wave): no overflow, and a zero mask returns the input unchanged (Task 5, `zeroMaskReturnsInput` uses a full-scale square).
5. Cancellation mid-song (user turns Korokae off): the engine stops before the next window and throws `java.util.concurrent.CancellationException` (Task 5, `cancellationStopsBetweenWindows`).

---

## Shared definitions (source of truth for Python and Java)

**STFT.** `win[n] = sqrt(0.5 − 0.5·cos(2πn/2048))` (square root of the periodic Hann), used for
both analysis and synthesis. For a signal of `L` samples: left-pad with `PAD = 2048 − 512 =
1536` zeros, right-pad with zeros so the padded length `Lp` satisfies `(Lp − 2048) % 512 == 0`
and `Lp ≥ 1536 + L + 1536`. Frames `t = 0 … T−1`, `T = (Lp − 2048)/512 + 1`, take
`xpad[t·512 : t·512 + 2048] · win`, then a forward FFT (sign e^{−i}) keeping bins `0 … 1024`
(F = 1025). The inverse does an inverse real FFT per frame, multiplies by `win`, overlap-adds at
hop 512, divides by `2.0` (the sum of the Hann at 75% overlap), and returns
`ypad[1536 : 1536 + L]`.

**Model tensors.** Input `spec`: float32 `[1, 4, 1025, T]`, channel order
`(L.re, L.im, R.re, R.im)`. Output `mask`: float32 `[1, 4, 1025, T]`, same order. For channel
`c`: `V_c = M_c · X_c` (complex multiply), `I_c = X_c − V_c`.

**Chunking.** `W = 8·44100 = 352800`, `O = 44100`, `S = W − O`. Window starts are
`0, S, 2S, …` while `start < L` (always at least one window). Each window takes
`x[start : start + W]`, zero-padded to `W` if it runs past the end, and is separated
independently (STFT → mask → iSTFT on its `W` samples). Output weight for sample `i` of a
window: `fadeIn(i) · fadeOut(i)`, where `fadeIn(i) = min(1, (i + 1)/(O + 1))` except
`fadeIn ≡ 1` for the first window, and `fadeOut(i) = min(1, (W − i)/(O + 1))` except
`fadeOut ≡ 1` for the last window. Output = Σ weight · window-instrumental, accumulated at
`start + i` for `start + i < L`, divided by Σ weight at each sample.

**Parity fixture `KRP2`** (little-endian): bytes `"KRP2"`, int32 version = 1, int32 sampleRate,
int32 frames, float32[frames·2] input (interleaved L, R), float32[frames·2] expected
instrumental (interleaved).

---

### Task 1: Python package, DSP and band layout

**Files:**
- Create: `ml/korokae_v2/requirements.txt`, `ml/korokae_v2/README.md`,
  `ml/korokae_v2/korokae_v2/__init__.py`, `ml/korokae_v2/korokae_v2/dsp.py`
- Test: `ml/korokae_v2/tests/test_dsp.py`
- Modify: `.gitignore`

**Interfaces:**
- Produces (`korokae_v2.dsp`): constants `SR=44100, N_FFT=2048, HOP=512, PAD=1536, BINS=1025,
  WIN_SAMPLES=352800, OVERLAP=44100`; `band_edges() -> list[tuple[int, int]]` (half-open bin
  ranges); `window() -> torch.Tensor[2048]`; `stft(x: Tensor[C, L]) -> Tensor[C, 1025, T]`
  (complex64); `istft(X: Tensor[C, 1025, T], length: int) -> Tensor[C, length]`;
  `separate(x: Tensor[2, L], mask_fn: Callable[[Tensor[1,4,1025,T]], Tensor[1,4,1025,T]]) -> Tensor[2, L]`
  (instrumental, using the chunking rule).

- [ ] **Step 1: Create the environment.** `requirements.txt` pins `torch`, `numpy`, `onnx`,
  `onnxruntime==1.20.*`, `pytest`, `pyyaml`. Create `ml/korokae_v2/.venv` with
  `python3 -m venv`; if no torch wheel exists for the system Python (3.14), create the venv
  with `uv venv --python 3.12` instead. Install the CPU torch wheel
  (`--index-url https://download.pytorch.org/whl/cpu`). Add to `.gitignore`:
  `ml/korokae_v2/.venv/`, `ml/korokae_v2/data/`, `ml/korokae_v2/checkpoints/`,
  `ml/korokae_v2/out/`, `app/src/androidTest/assets/korokae_v2_*`.
  Check: `ml/korokae_v2/.venv/bin/python -c "import torch, onnx, onnxruntime"` exits 0.

- [ ] **Step 2: Write the failing tests** in `tests/test_dsp.py`:

```python
def test_band_edges_cover_all_bins_contiguously():
    e = band_edges()
    assert e[0][0] == 0 and e[-1][1] == BINS
    assert all(a[1] == b[0] for a, b in zip(e, e[1:]))
    assert all(hi > lo for lo, hi in e)
    assert len(e) == 39  # 10 + 12 + 8 + 8 + 1

def test_stft_istft_round_trip():
    x = torch.randn(2, 44100 * 3 + 123)
    y = istft(stft(x), x.shape[1])
    assert torch.max(torch.abs(x - y)) < 1e-4

def test_separate_zero_mask_returns_input_across_windows():
    x = torch.randn(2, int(44100 * 17.3))  # 3 windows, ragged end
    y = separate(x, lambda s: torch.zeros_like(s))
    assert y.shape == x.shape and torch.max(torch.abs(x - y)) < 1e-4

def test_separate_identity_vocal_mask_gives_silence():
    x = torch.randn(2, 44100 * 10)
    ones = lambda s: torch.cat([torch.ones_like(s[:, :1]), torch.zeros_like(s[:, :1])] * 2, 1)
    assert torch.max(torch.abs(separate(x, ones))) < 1e-4

def test_separate_short_input():
    x = torch.randn(2, 13000)
    assert separate(x, lambda s: torch.zeros_like(s)).shape == x.shape
```

- [ ] **Step 3: Run and confirm failure.** `cd ml/korokae_v2 && .venv/bin/pytest tests/test_dsp.py -q`
  → fails with `ImportError` / `ModuleNotFoundError: korokae_v2.dsp`.

- [ ] **Step 4: Implement `dsp.py`** following "Shared definitions". Band edges convert Hz to
  bins with `round(hz · N_FFT / SR)`. The forward FFT is `torch.fft.rfft` on the windowed frames
  (implement framing with `Tensor.unfold`, not `torch.stft`, so the convention matches Java
  exactly); the inverse is `torch.fft.irfft(n=2048)` plus overlap-add with `torch.nn.functional.fold`.

- [ ] **Step 5: Run and confirm pass.** Same command → `5 passed`.

- [ ] **Step 6: Commit** (`ml/korokae_v2/`, `.gitignore`):
  `feat(korokae-v2): DSP, band layout and chunked separation reference`

### Task 2: KorokaeNet v2 network

**Files:**
- Create: `ml/korokae_v2/korokae_v2/model.py`
- Test: `ml/korokae_v2/tests/test_model.py`

**Interfaces:**
- Consumes: `band_edges()`, `BINS` from Task 1.
- Produces: `class KorokaeNetV2(torch.nn.Module)` with
  `__init__(self, n_blocks: int = 8, hidden: int = 256, d: int = 128)` and
  `forward(self, spec: Tensor[B, 4, 1025, T]) -> Tensor[B, 4, 1025, T]` (the mask);
  `count_params(model) -> int`.

- [ ] **Step 1: Write the failing tests:**

```python
def test_forward_shape():
    m = KorokaeNetV2(n_blocks=2, hidden=64, d=32).eval()
    x = torch.randn(1, 4, 1025, 50)
    assert m(x).shape == (1, 4, 1025, 50)

def test_default_size_in_spec_range():
    assert 15_000_000 <= count_params(KorokaeNetV2()) <= 25_000_000

def test_zero_input_is_finite():
    m = KorokaeNetV2(n_blocks=2, hidden=64, d=32).eval()
    assert torch.isfinite(m(torch.zeros(1, 4, 1025, 20))).all()

def test_time_axis_is_bidirectional():
    # changing a late frame must change an early frame's mask (look-ahead exists)
    torch.manual_seed(0)
    m = KorokaeNetV2(n_blocks=1, hidden=32, d=16).eval()
    x = torch.randn(1, 4, 1025, 30); y = x.clone(); y[..., 29] += 1.0
    assert not torch.allclose(m(x)[..., 0], m(y)[..., 0])
```

- [ ] **Step 2: Run and confirm failure** (`.venv/bin/pytest tests/test_model.py -q` → ImportError).

- [ ] **Step 3: Implement `model.py`.**
  - Band split: for each band `b` with `n_b` bins, flatten its `4 · n_b` values per frame →
    `LayerNorm(4·n_b)` → `Linear(4·n_b, d)`; stack to `[B, K=39, T, d]`.
  - Block: (a) for each band, `LayerNorm(d)` → `LSTM(d, hidden, bidirectional)` over T →
    `Linear(2·hidden, d)`, add the residual; (b) the same across the K bands for each frame.
  - Mask head per band: `LayerNorm(d)` → `Linear(d, 4d)` → `Tanh` → `Linear(4d, 2·4·n_b)` →
    `GLU(dim=-1)` → reshape to `[B, 4, n_b, T]`; concatenate the bands along the bin axis.
  - If the default size falls outside 15–25M, adjust only the mask-head expansion factor
    (4d → 2d or 6d) and record it in the docstring.

- [ ] **Step 4: Run and confirm pass** → `4 passed`.

- [ ] **Step 5: Commit:** `feat(korokae-v2): band-split RNN network`

### Task 3: ONNX export and parity fixture

**Files:**
- Create: `ml/korokae_v2/korokae_v2/export.py`
- Test: `ml/korokae_v2/tests/test_export.py`

**Interfaces:**
- Consumes: `KorokaeNetV2`, `separate`, `stft` from Tasks 1–2.
- Produces: `export_onnx(model, path: str) -> None` (input name `spec`, output name `mask`,
  dynamic axis T, opset 17); `write_parity_fixture(model, path: str, seconds: float = 10.0,
  seed: int = 1234) -> None` (format KRP2); CLI
  `python -m korokae_v2.export --untrained --blocks N --hidden H --out-dir DIR`, which writes
  `DIR/korokae_v2_N_H.onnx` and `DIR/korokae_v2_N_H_parity.bin` and prints the parameter count
  and file size.

- [ ] **Step 1: Write the failing tests:**

```python
def test_onnx_matches_torch(tmp_path):
    torch.manual_seed(0)
    m = KorokaeNetV2(n_blocks=2, hidden=64, d=32).eval()
    p = tmp_path / "m.onnx"; export_onnx(m, str(p))
    x = torch.randn(1, 4, 1025, 37)
    sess = onnxruntime.InferenceSession(str(p))
    got = sess.run(["mask"], {"spec": x.numpy()})[0]
    assert np.max(np.abs(got - m(x).detach().numpy())) < 1e-4

def test_parity_fixture_layout(tmp_path):
    m = KorokaeNetV2(n_blocks=1, hidden=32, d=16).eval()
    p = tmp_path / "f.bin"; write_parity_fixture(m, str(p), seconds=2.0)
    b = p.read_bytes()
    assert b[:4] == b"KRP2"
    version, sr, frames = struct.unpack("<iii", b[4:16])
    assert (version, sr, frames) == (1, 44100, 88200)
    assert len(b) == 16 + 2 * frames * 2 * 4
```

- [ ] **Step 2: Run and confirm failure** → ImportError.

- [ ] **Step 3: Implement `export.py`.** The fixture input is a deterministic stereo signal
  (seeded noise at −20 dBFS plus 220 Hz / 330 Hz tones, different per channel), and the expected
  output is `separate(x, model)` from Task 1, so the fixture covers chunking too (10 s = 2 windows).

- [ ] **Step 4: Run and confirm pass** → `2 passed`.

- [ ] **Step 5: Generate the default artefacts** into the Android test assets:
  `.venv/bin/python -m korokae_v2.export --untrained --blocks 8 --hidden 256 --out-dir ../../app/src/androidTest/assets`
  Check: two files `korokae_v2_8_256.onnx` and `korokae_v2_8_256_parity.bin` exist, and
  `git status` does not list them (git-ignored).

- [ ] **Step 6: Commit:** `feat(korokae-v2): ONNX export and KRP2 parity fixture`

### Task 4: Java STFT

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/korokae/v2/Stft.java`
- Test: `app/src/test/java/com/psthetech/swara/korokae/v2/StftTest.java`

**Interfaces:**
- Consumes: `com.psthetech.swara.vibe.dsp.Fft.transform(double[] re, double[] im)` (in-place, forward).
- Produces: `final class Stft` with constants `N_FFT = 2048, HOP = 512, BINS = 1025, PAD = 1536`;
  `static int frameCount(int length)`;
  `static void forward(float[] mono, int length, float[] outRe, float[] outIm)` (output laid out
  `[bin * T + t]`, T = `frameCount(length)`);
  `static void inverse(float[] re, float[] im, int frames, int length, float[] outMono)`.

- [ ] **Step 1: Write the failing tests** (`StftTest`):
  - `frameCountMatchesPython()`: `frameCount(88200) == 176`, `frameCount(352800) == 693`, `frameCount(1) == 4`.
    (These values follow from "Shared definitions"; if Python's `stft` disagrees, Python is the reference and the test values change.)
  - `roundTripReconstructs()`: random 3.2 s signal, `forward` then `inverse` → max abs error < 1e-4.
  - `sineLandsInExpectedBin()`: a 1076.66 Hz sine (bin 50 exactly) puts the largest magnitude of a middle frame at bin 50.

- [ ] **Step 2: Run and confirm failure:** `./gradlew testDebugUnitTest --tests '*korokae.v2.StftTest'` → compile error.

- [ ] **Step 3: Implement `Stft`** per "Shared definitions", using `Fft.transform` on doubles;
  the inverse uses the conjugate trick (conjugate, forward transform, conjugate, divide by N),
  filling bins 1025–2047 by Hermitian symmetry.

- [ ] **Step 4: Run and confirm pass** → 3 tests pass.

- [ ] **Step 5: Commit:** `feat(korokae-v2): Java STFT matching the Python reference`

### Task 5: Java separation engine (chunking, masks, cancellation, progress)

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/korokae/v2/MaskModel.java`,
  `app/src/main/java/com/psthetech/swara/korokae/v2/KorokaeV2Engine.java`
- Test: `app/src/test/java/com/psthetech/swara/korokae/v2/KorokaeV2EngineTest.java`

**Interfaces:**
- Consumes: `Stft` (Task 4).
- Produces:
  - `interface MaskModel extends AutoCloseable { float[] run(float[] spec, int frames) throws Exception; }`:
    `spec` and the return are `[4][1025][frames]` flattened in channel-major order
    (`(c * 1025 + bin) * frames + t`), channels `(L.re, L.im, R.re, R.im)`.
  - `final class KorokaeV2Engine` with `KorokaeV2Engine(MaskModel model)` and
    `float[] separate(float[] interleavedStereo, int frames, ProgressListener progress, java.util.function.BooleanSupplier cancelled) throws Exception`,
    returning the interleaved instrumental of exactly `frames` frames, plus
    `interface ProgressListener { void onProgress(int percent); }`.
    It throws `java.util.concurrent.CancellationException` when `cancelled` is true at a window boundary.

- [ ] **Step 1: Write the failing tests** (`KorokaeV2EngineTest`, with fake `MaskModel`s):
  - `zeroMaskReturnsInput()`: a full-scale ±1.0 square wave at 100 Hz, 17.3 s; a model returning zeros → output equals input within 1e-4.
  - `identityVocalMaskGivesSilence()`: a model returning 1 for the real channels and 0 for the imaginary ones → max abs output < 1e-4.
  - `shortInputKeepsLength()`: 0.3 s input → output length `frames * 2`.
  - `oddLengthKeepsLength()`: `frames = 352800 * 2 + 7` → output length `frames * 2`.
  - `progressIsMonotonicAndEndsAt100()`: records the callbacks for a 30 s input; non-decreasing, last == 100.
  - `cancellationStopsBetweenWindows()`: `cancelled` becomes true after the first `run` call → throws `CancellationException`, with `run` called exactly once.
  - `matchesPythonChunking()`: a fake model whose mask is 0.5 real everywhere → instrumental = 0.5 · input within 1e-4 across window seams (catches crossfade-weight bugs).

- [ ] **Step 2: Run and confirm failure** → compile error.

- [ ] **Step 3: Implement the engine** following "Chunking": per window, de-interleave, run
  `Stft.forward` on L and R, pack `spec`, call `model.run`, apply `V = M·X`, `I = X − V`, run
  `Stft.inverse` per channel, accumulate weighted samples and weights, then normalise and
  re-interleave. Progress = windows done / total windows · 100.

- [ ] **Step 4: Run and confirm pass** → 7 tests pass. Then run the full unit suite
  `./gradlew testDebugUnitTest` → 0 failures.

- [ ] **Step 5: Commit:** `feat(korokae-v2): chunked separation engine with fake-model tests`

### Task 6: ONNX Runtime model and on-device parity

**Files:**
- Modify: `app/build.gradle` (dependency, `androidResources { noCompress 'onnx' }`)
- Create: `app/src/main/java/com/psthetech/swara/korokae/v2/OnnxMaskModel.java`
- Test: `app/src/androidTest/java/com/psthetech/swara/korokae/v2/KorokaeV2ParityTest.java`,
  `app/src/androidTest/java/com/psthetech/swara/korokae/v2/TestAssets.java`

**Interfaces:**
- Consumes: `MaskModel`, `KorokaeV2Engine` (Task 5); the artefacts from Task 3, step 5.
- Produces: `final class OnnxMaskModel implements MaskModel` with
  `static OnnxMaskModel open(java.io.File modelFile, int threads) throws ai.onnxruntime.OrtException`
  (CPU execution provider, `setIntraOpNumThreads(threads)`, graph optimisation `ALL_OPT`);
  `TestAssets.copyToCache(Context, String assetName) -> File`;
  `TestAssets.readParity(Context, String assetName) -> float[][]` (`{input, expected}`, with frames derivable as `input.length / 2`).

- [ ] **Step 1: Add the dependency** `implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.20.0'`
  (if Maven Central lists a newer 1.x stable, use it and update this plan's header).
  Check: `./gradlew assembleDebug` succeeds; note the APK size change in the commit message.

- [ ] **Step 2: Write the failing instrumented test:**
  `parityWithPyTorch()`: copy `korokae_v2_8_256.onnx` to the cache, read
  `korokae_v2_8_256_parity.bin`, run `KorokaeV2Engine` with `OnnxMaskModel.open(file, 4)` →
  max abs error between output and expected ≤ 1e-3; log the max error with tag `KorokaeV2`.

- [ ] **Step 3: Run and confirm failure** (with the phone connected):
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.psthetech.swara.korokae.v2.KorokaeV2ParityTest`
  → compile error (`OnnxMaskModel` missing). `connectedDebugAndroidTest` installs the debug app
  in place (signed with the same debug key), so the owner's data is kept; never uninstall.

- [ ] **Step 4: Implement `OnnxMaskModel`.** `run` wraps `spec` in an `OnnxTensor` of shape
  `[1, 4, 1025, frames]` via `FloatBuffer.wrap`, runs the session, and copies the `mask` output
  to a new `float[]`; `close()` releases the session.

- [ ] **Step 5: Run and confirm pass** → 1 test passes, and logcat shows the max error.

- [ ] **Step 6: Commit:** `feat(korokae-v2): ONNX Runtime mask model with on-device parity test`

> **Revision 2026-11-10 (Task 7 outcome):** every BS-RNN rung failed the speed gate (see
> `ml/korokae_v2/README.md`); the owner chose attention. Task 2's model is replaced by the
> band-split transformer (spec section 4), exported as `korokae_v2_4_96.onnx`, run with the
> ONNX Runtime CPU arena off. `configs/base.yaml` records `n_blocks`, `d`, `heads`, `ffn`,
> `head_expansion`, `threads` instead of `hidden`. Device tests run via `adb install -r` and
> `am instrument`, because `connectedDebugAndroidTest` uninstalls the app (wiping user data).

### Task 7: Speed gate and model size decision

**Files:**
- Test: `app/src/androidTest/java/com/psthetech/swara/korokae/v2/KorokaeV2SpeedTest.java`
- Modify: `ml/korokae_v2/README.md` (results table), create `ml/korokae_v2/configs/base.yaml`

**Interfaces:**
- Consumes: `OnnxMaskModel`, `KorokaeV2Engine`, `TestAssets`, the export CLI (Task 3).
- Produces: `configs/base.yaml` with `n_blocks`, `hidden`, `d`, `threads` (read by Plans 2 and 3).

- [ ] **Step 1: Write the instrumented test** `fourMinuteSongUnder60s()`: the model asset name
  comes from instrumentation argument `korokaeModel` (default `korokae_v2_8_256.onnx`) and the
  thread count from `korokaeThreads` (default 4). It builds 240 s of synthetic stereo audio
  (tones plus noise), times `separate(...)` with `SystemClock.elapsedRealtime()`, samples peak
  memory (`Debug.getNativeHeapAllocatedSize()` plus the Java heap in use) from a background thread
  every 250 ms, logs `model, threads, ms, peakMB`, then asserts `ms <= 60_000` and `peakMB <= 500`.

- [ ] **Step 2: Run on the S21+:**
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.psthetech.swara.korokae.v2.KorokaeV2SpeedTest`
  Record the result (pass or fail, ms, peak MB) in the README table.

- [ ] **Step 3: If it failed, shrink and re-run** in this order, stopping at the first pass
  (each needs an export via the Task 3 CLI into the androidTest assets, then step 2 with
  `-Pandroid.testInstrumentationRunnerArguments.korokaeModel=korokae_v2_N_H.onnx`):
  `(12,256)` first only if 8/256 passed with ≥ 40% headroom, otherwise `(8,256)` → threads 6 →
  `(8,192)` → `(6,192)` → `(6,128)`. Pick the largest configuration that passes; prefer more
  blocks over more hidden units when two pass. If nothing passes, stop and report to the owner
  with the table (the spec's 60 s target needs revisiting).

- [ ] **Step 4: Write `configs/base.yaml`** with the chosen `n_blocks`, `hidden`, `d: 128` and
  `threads`, plus the parameter count, the ONNX size in MB, and the measured ms and peak MB as comments.

- [ ] **Step 5: Run the full JVM suite** `./gradlew testDebugUnitTest` → 0 failures; the
  Python suite `.venv/bin/pytest -q` → all pass.

- [ ] **Step 6: Commit:** `test(korokae-v2): S21+ speed gate; fix model size N blocks x H hidden`
  then push the branch and open a PR summarising the speed table and the chosen size.
