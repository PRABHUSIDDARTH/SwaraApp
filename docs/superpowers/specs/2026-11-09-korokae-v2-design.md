# Korokae v2 — High-Accuracy Vocal Removal — Design

Date: 2026-11-09
Status: Approved in brainstorming, awaiting spec review
Sub-project 2 of the "own our models" effort (sub-project 1 was the Vibe recommender,
`2026-10-17-vibe-recommender-design.md`).

## 1. Goal and constraints

Replace Korokae v1 with a vocal-removal model that sounds clean. v1 is a ~1M-parameter causal
GRU that applies one real-valued mask, computed from the mono mid channel at N_FFT 1024, to both
stereo channels. It turns whole frequency bands down instead of separating voice from
instruments, so the result sounds muffled ("an old speaker full of dust").

Constraints, all decided with the product owner:

1. **Ours end to end.** The architecture is written from scratch from published research ideas.
   No third-party model, pretrained weights, separation API or separation code. Open-source
   *runtimes and tools* (PyTorch for training, ONNX Runtime on the phone) are allowed because
   they only execute our model.
2. **Commercially safe data.** Every training file is CC0, CC BY, or created by us. MUSDB18,
   MoisesDB, MedleyDB and anything NC / ND / SA are excluded. v1's MUSDB18-trained weights are
   removed when v2 ships.
3. **Process first, not real-time.** When Korokae is turned on, the whole song is separated once
   and the instrumental is cached; accuracy is prioritised over instant start.
4. **Training on Kaggle free GPUs** (~30 GPU-h/week, 12 h sessions). The development laptop
   (i5-1135G7, Iris Xe, 7 GB RAM) runs only smoke tests.
5. **ONNX Runtime on the phone** (MIT licence).
6. **The owner's phone songs are a private test set only.** They are copyrighted, so they are
   never used for training, never uploaded and never shipped.

### Success criteria (definition of done)

All five must pass:

1. **Synthetic test set:** v2 instrumental SDR ≥ v1 instrumental SDR + 5 dB on the same held-out
   clips (v1 is measured first as the baseline).
2. **Blind listening test** on 20–30 of the owner's songs (30 s chorus clips, v1 and v2 in random
   A/B order, each rated 1–5 for "voice gone" and "music clean"): v2 preferred on ≥ 90% of clips;
   mean "music clean" ≥ 4; mean "voice gone" ≥ 4.
3. **On device (Galaxy S21+, Exynos 2100):** a 4-minute song processes in ≤ 60 s; peak memory
   ≤ 500 MB; no crash across the test songs; switching to and from the instrumental is seamless.
4. **Correctness tests** (section 6.3) pass.
5. **Licence:** the manifest audit passes, the in-app credits page exists, and v1 code plus
   `korokae_v1.bin` are deleted.

## 2. Architecture overview

```
 ┌──────────── Training (ml/korokae_v2, Kaggle) ────────────┐
 │ licence manifest ─▶ vocals + accompaniment (+ Swara-Synth)│
 │        └─▶ on-the-fly mixing & augmentation ─▶ BS-RNN     │
 │                        └─▶ export.py ─▶ korokae_v2.onnx    │
 └───────────────────────────────────────────────────────────┘
 ┌──────────── App (com.psthetech.swara.korokae) ───────────┐
 │ Korokae toggle ─▶ matcher hit? ─▶ play library karaoke    │
 │               └▶ cache hit?   ─▶ play cached instrumental │
 │               └▶ SeparationWorker: decode ─▶ STFT ─▶ ONNX │
 │                  ─▶ iSTFT ─▶ AAC ─▶ cache ─▶ crossfade in │
 └───────────────────────────────────────────────────────────┘
```

## 3. Data

### 3.1 Licence-audited manifest

`ml/korokae_v2/data/manifest.csv`, one row per file: `path, role (vocal|accompaniment),
source, source_url, author, licence, attribution`. `build_manifest.py` rejects every licence
except CC0-1.0, CC-BY-3.0, CC-BY-4.0 and `own`, and fails the build on any rejected file found in
the data folders. `make_credits.py` generates the in-app credits text from the manifest.

| Source | Role | Licence kept |
|---|---|---|
| VocalSet | vocal | CC BY 4.0 |
| vocadito | vocal | CC BY 4.0 |
| ccMixter a cappellas | vocal | per-track: CC BY / CC0 only |
| Slakh2100 | accompaniment | CC BY 4.0 |
| FMA tracks tagged instrumental | accompaniment | per-track: CC BY / CC0 only; each spot-checked by ear for hidden vocals |
| Swara-Synth (3.2) | accompaniment | own |
| Optional: owner / friends singing | vocal | own (signed consent note) |

Each source's licence is re-checked against its official page when it is downloaded, and the
URL of that page is stored in the manifest.

### 3.2 Swara-Synth: original Indian-style accompaniment

`ml/korokae_v2/synth/` composes original music procedurally (never transcribed from existing
songs): raga-based melodies (e.g. Mohanam, Kalyani, Shankarabharanam, Hindolam, Kharaharapriya),
tala-based rhythm patterns, chord progressions and intro / verse / chorus arrangement at
70–150 BPM. It renders with FluidSynth using permissively licensed SoundFonts plus CC0 percussion
samples (tabla, mridangam, dholak); every instrument pack goes through the manifest audit.
Target: ≥ 100 hours, generated on Kaggle CPU sessions.

### 3.3 On-the-fly mixing and augmentation

Each training example pairs a random vocal with a random accompaniment segment, at 44.1 kHz
stereo:

- vocal-to-accompaniment level −10 … +10 dB
- pitch shift ±2 semitones, time stretch ±10% (applied per stem)
- synthetic reverb (our own generated impulse responses)
- EQ, then compression and limiting to imitate loud film-song mastering
- stereo width and panning; mono vocals are panned into the stereo field

### 3.4 Splits

Train / validation / test are split by source recording and by singer, so test clips contain
voices and songs the model never heard. Test-set mixes are rendered once with a fixed seed and
stored, so v1 and v2 are scored on identical audio.

### 3.5 The owner's songs

20–30 songs chosen with the owner (male, female, duets, dense and sparse arrangements). Used only
for the blind listening test (section 6.2). They stay on the laptop and phone.

## 4. Model: KorokaeNet v2 (band-split transformer)

> Revised 2026-11-10 after the S21+ speed gate: the band-split RNN took 595 s (8x256) and 186 s
> (6x128) for a 4-minute song against the 60 s limit. The owner chose attention; the network is
> sized to the phone's measured compute budget (~1 MMAC per band-frame cell).

- **Input:** stereo 44.1 kHz. STFT with a Hann window, N_FFT 2048, hop 512. Per frame and band,
  the real and imaginary parts of L and R form the band's input.
- **Band split:** 23 bands, 100 Hz wide below 1 kHz, 500 Hz up to 4 kHz, 1 kHz up to 8 kHz,
  4 kHz up to 16 kHz, then the remainder as one band. Each band: LayerNorm then Linear to D = 96,
  plus a learned band embedding.
- **Sequence modelling:** 4 blocks. Each block runs pre-norm self-attention with rotary position
  embedding across time (per band) at half the frame rate (strided conv down, transposed conv
  up, residual), then self-attention across bands (per frame); 4 heads, feed-forward width 2D.
  About 3.2M parameters (13 MB float32); the size is fixed by the speed check (section 7).
- **Mask estimation:** a per-band MLP (expansion 2) outputs a complex ratio mask for L and R. Vocals =
  mask × mixture STFT; instrumental = mixture − vocals, so the stems always sum to the mix.
- **Loss:** waveform L1 plus multi-resolution STFT magnitude L1 (FFT sizes 512, 1024, 2048), on
  both the vocals and the instrumental.
- **Inference on a full song:** 8 s windows with 1 s overlap and a linear crossfade, so memory is
  constant for any song length.
- **Export:** the STFT and iSTFT run in Java on the phone. The ONNX graph contains only the
  network (complex spectrogram in, complex mask out), opset 17, float32 weights (~13 MB).

## 5. Training on Kaggle

- **Code:** `ml/korokae_v2/` with `data/`, `synth/`, `model.py`, `train.py`, `evaluate.py`,
  `export.py`, and `configs/*.yaml`. The Kaggle notebook checks out a fixed git commit.
- **Data:** prepared FLAC audio plus `manifest.csv`, uploaded as private Kaggle datasets.
- **Resumable sessions:** a checkpoint every ~30 min with weights, optimiser, scheduler, RNG
  states and step. Each session resumes from the latest checkpoint, so a timeout loses at most
  ~30 min.
- **Training setup:** mixed precision on T4 / P100; 6 s segments; effective batch ~16 through
  gradient accumulation; AdamW with a cosine schedule.
- **Stages:**
  1. laptop smoke test (tiny model, CPU, minutes)
  2. Swara-Synth generation (Kaggle CPU)
  3. main training (~60–80 GPU-h, about 2–3 weeks of free quota), curves reviewed every session
  4. fine-tune with a lower learning rate and harder augmentation
- **Validation:** vocal and instrumental SDR on the validation set every few thousand steps,
  computed by our own SDR implementation. The best checkpoint is kept. Every run logs its config,
  git hash and scores.
- **Export:** `export.py` writes `korokae_v2.onnx` and `korokae_v2_parity.bin` (input and
  expected output for the Java parity test).

## 6. App integration

### 6.1 Flow

Turning Korokae on (Now Playing pill or widget):

1. **Library karaoke:** if `KorokaeMatcher` finds a karaoke or instrumental file for the song,
   play it (existing behaviour).
2. **Cache hit:** otherwise, if the v2 cache has the song, switch to the cached instrumental at
   the current position.
3. **Separate:** otherwise, start the separation job. The original keeps playing and the pill
   shows `KorokaeState.processing(…, percent)`. On completion, if the same song is still playing,
   crossfade to the instrumental at the current position (`KorokaeState.active(…, path)`).

Turning it off switches back to the original at the current position.

### 6.2 Separation job

- `KorokaeSeparationWorker` (WorkManager, foreground, with a progress notification). It survives
  backgrounding and resumes after process death.
- Steps:
  1. Decode with the existing `PcmDecoder` to 44.1 kHz stereo; resample 48 kHz input; duplicate
     mono to both channels.
  2. `KorokaeV2Engine` (STFT, ONNX Runtime session with XNNPACK, iSTFT) processes the 8 s
     overlapping windows and reports progress.
  3. Encode the instrumental to AAC 256 kbps (M4A) with MediaCodec + MediaMuxer, writing to a
     temporary file that is renamed only when complete.
- The job is cancelled cleanly if Korokae is turned off, the song changes or the file is deleted.

### 6.3 Cache

- **Directory:** `files/korokae_v2/`, one M4A per song.
- **Key:** song id + file size + last-modified time + model version, so an edited file or a new
  model is never served a stale result.
- **Limit:** LRU eviction, 1 GB by default. Settings gets "Clear Korokae cache".

### 6.4 Failures

Unsupported format, low storage, an ONNX error or a decode error show `KorokaeState.failed(…)`
with a short message while the original keeps playing. No partial file is ever cached.

### 6.5 Delivery and v1 removal

- ONNX Runtime (Android AAR) and `korokae_v2.onnx` are bundled in the APK, so Korokae works
  offline. Moving the model to on-demand download is a later option if APK size becomes a
  problem.
- Removed: `KorokaeEngine`, `KorokaeModel`, `KorokaeModelProvider`, `KorokaeAudioProcessor`
  (real-time path), `korokae_v1.bin`, `ml/korokae/` and their tests. `KorokaeMatcher` and
  `KorokaeState` stay.
- A "Korokae model credits" screen under Settings › About shows the CC BY attributions.

## 7. Testing and evaluation

### 7.1 Speed check (first milestone, before any real training)

Export an untrained KorokaeNet v2 at the target size, run it through the full app engine on the
S21+, and time a 4-minute song. If it takes over 60 s, reduce the number of blocks or the hidden
size until it passes, then fix that size for training.

### 7.2 Synthetic metrics

`evaluate.py` scores v1 (through its exported weights in PyTorch) and every v2 checkpoint on the
fixed test mixes: median vocal SDR and median instrumental SDR. Gate: v2 ≥ v1 + 5 dB on the
instrumental before the listening test.

### 7.3 Blind listening test

`listening_test.py` (laptop only):

1. Cut a 30 s chorus clip from each chosen song.
2. Run v1 and v2 on each clip.
3. Write a local HTML page with A/B in random order and two 1–5 ratings per clip.
4. Save the answers to JSON and compute the pass criteria (section 1).

### 7.4 Unit and device tests

- **Parity:** Java STFT + ONNX Runtime vs PyTorch on `korokae_v2_parity.bin`, max abs error ≤ 1e-3.
- **Overlap-add:** an all-ones vocal mask reconstructs the input exactly (instrumental = 0) and an
  all-zeros mask returns the input as the instrumental.
- **Cache:** key changes with size, mtime or model version; LRU evicts oldest first and respects
  the limit.
- **State machine:** off → processing → active; cancel while processing; fail keeps the original
  playing; a library karaoke match skips separation.
- **Device:** run the 20–30 test songs through the app on the S21+, recording time and peak
  memory per song.

## 8. Out of scope

- Real-time or instant-start separation (a hybrid "v2-lite" can be designed later).
- A "guide vocal" level slider (needs the vocal stem cached as well).
- Preparing favourites in the background while charging.
- Separating more stems (drums, bass, etc.).
- Training on any copyrighted song, including the owner's library.
