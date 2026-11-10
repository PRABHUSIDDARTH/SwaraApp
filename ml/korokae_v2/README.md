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
| BS-transformer 4 blocks x d 96, 23 bands | 3.2M | 13.0 MB | 4 | off | 51.1 s | 378 MB (sampled) | pass by the sampled measure |
| BS-transformer 4 blocks x d 96, 23 bands | 3.2M | 13.0 MB | 6 | off | 58.0 s | 378 MB | pass (slower) |

The rows above sampled the heap every 250 ms, which misses short ORT peaks. From the final review
on, peak = the kernel's VmHWM for the whole test process (baseline 171 MB, plus 166 MB holding the
4-minute song in and out as float arrays):

| Model | Threads | Time | Peak (VmHWM) | Result |
|---|---|---|---|---|
| BS-transformer 4x96, all heads at once | 4 | 52.3 s | 741 MB | fail (memory) |
| **BS-transformer 4x96, one head at a time** | **4** | **48.2 s** | **613 MB** | **fail (memory)** |
| same + pinned ORT output buffers (reverted) | 4 | 57.9 s | 685 MB | fail |

The phone sustains roughly 10-20 GMAC/s, so the network has to stay near 1 MMAC per
(band, frame) cell; the LSTM designs were 3-10x over that. Time attention runs at half the frame
rate (strided conv down, transposed conv up) to stay inside it.

## Data (Plan 2)

| Source | Role | Licence (checked on the host at download) | Host |
|---|---|---|---|
| VocalSet v1.1 | vocal | CC BY 4.0 | zenodo.org/records/1203819 |
| vocadito | vocal | CC BY 4.0 | zenodo.org/records/5578807 |
| JaCRC (Jingju a Cappella Recordings Collection) | vocal; jinghu tracks as accompaniment | CC BY 4.0. Rows whose metadata source is `SVAD` (another dataset's licence) or `commercial` are excluded, as is any file the metadata doesn't list | zenodo.org/records/6536490 |
| AdoVoc Pro (flamenco) | vocal | CC BY 4.0. Only the `*_mono` solo takes; `*_true` are cuts of the same takes, and the performances have guitar | zenodo.org/records/3383118 |
| AlbumDB ("Extraordinary", 10 songs) | vocal and accompaniment | CC BY 4.0. Per song, the `vox_*` stems are summed into one vocal and the other stems into one accompaniment, with one shared gain (peak ≤ 0.99, never boosted) | zenodo.org/records/19683001 |
| ccMixter a cappellas | vocal | per upload: CC BY 3.0/4.0 or CC0 only; uploads tagged `AI_generated`, `AI_assisted` or `ia_stems_generated` are rejected | ccmixter.org query API |
| Slakh2100 redux (`mix.flac` only) | accompaniment | CC BY 4.0 | zenodo.org/records/4599666 (streamed, never stored whole) |

- `python -m korokae_v2.data.build --sources ... --out DIR [--dry-run]` downloads, converts and
  splits the data, and writes `manifest.csv`.
- `python -m korokae_v2.data.manifest audit --root DIR` re-checks every file and licence.
- `python -m korokae_v2.data.credits --manifest M --out F` writes the in-app credits text.

Live dry run (2026-11-10):
- **Zenodo:** the record pages of VocalSet, vocadito and Slakh all show `cc-by-4.0`.
- **ccMixter:** 5984 a cappella records. 1414 were accepted, from 288 singers. 4544 were rejected for their licence (NC/SA/ND) and 10 for AI tags. 16 records could not be fetched.

Checked and rejected (2026-11-10): Dagstuhl ChoirSet, ESMUC Choir, Cantoría, Choral Singing
Dataset and Jingju part 1 (all CC BY-NC); MedleyVox (cut from MedleyDB, which is NC); TONAS
(restricted access); URSing (CC BY, but students covering commercial songs over prepared backing
tracks: left out by decision). Freesound's API terms make commercial use subject to agreement with
UPF.

JaCRC on the real metadata: 186 newly recorded vocals and 75 student takes are kept (261 vocals),
plus 76 jinghu accompaniments; exactly the 53 SVAD files are dropped.

Vocals prep at 13e4ec8 (Kaggle, 2026-11-10): 4092 manifest rows. VocalSet 3613 files / 20 singers,
vocadito 40 / 29, JaCRC 337 / 19 (261 vocals, 76 jinghu), AdoVoc Pro 82 / 4 (alba, cris, joaquin,
plus 7 phrase takes with no singer in the name, grouped as `adovoc-unknown`), AlbumDB 20 / 10
songs (one vocal and one accompaniment each), ccMixter 0 (403).

ccMixter answers 403 to scripted downloads (seen from the laptop and from Kaggle). The fetcher
skips a failed download and gives up after 10 in a row. Access has been requested from the
ccMixter admins; until they answer, the vocals come from the Zenodo sources only.

ccMixter quirks: the API ignores its `license=` filter, so licences are checked per record. Pages
larger than a few records sometimes come back malformed, so the fetcher reads 4 records at a time
and falls back to single records.

## Training on Kaggle (Plan 2)

Code reaches Kaggle by `git fetch` of a pinned commit from the public GitHub repo. Data never
leaves Kaggle: the prep notebooks' outputs are mounted straight into the training notebook. Only
the ~40 MB checkpoint goes through a private dataset (`korokae-ckpt`).

One-time setup:
1. Save the Kaggle API token to `~/.kaggle/access_token` (mode 600).
2. Phone-verify the Kaggle account; GPU and internet in notebooks need it.
3. Run `.venv/bin/pip install -r requirements.txt`. It includes the Kaggle CLI, which needs `six`.
4. Run `kaggle/kaggle_cycle.sh init`, which creates the private `korokae-ckpt` dataset.

Data prep (once, on Kaggle CPU):
1. Run `kaggle/kaggle_cycle.sh prep <pushed commit>`. It runs `korokae-prep-vocals` (VocalSet,
   vocadito, JaCRC, AdoVoc Pro, AlbumDB, ccMixter; ≤ 25 h each) and `korokae-prep-accomp` (Slakh: 40 h train, 5 h val, 5 h test).
2. Check both with `kaggle/kaggle_cycle.sh status`. Each output holds `data/manifest.csv`, which
   passed the audit.

Pilot (2026-11-10, Kaggle Tesla T4, commit 5ca1482, 30-minute session): setup 4.7 min, then
1034 steps in 25.3 min. Median **1.215 s/step** (range 1.206-1.230 over 19 log windows), batch 8 x
accum 2 of 6 s stereo. Loss fell from 1.68 (step 50) to 0.84 (step 1000). So
`total_steps = floor(0.9 x 70 h x 3600 / 1.2151) = 186651`, which is about 34,000 steps per
690-minute session, or 6 sessions in all.

Two Kaggle issues the pilot found, both fixed:
- **GPU memory:** a cuFFT plan for every random augmentation length filled the GPU. FFT lengths are
  now 7-smooth: about 167 cached plans, 0.4 GB, flat.
- **Status message:** CLI 2.2.5 reports a never-created notebook as
  "Permission 'kernels.get' was denied".

The main run starts with `--fresh` once the data is final; the pilot's checkpoints are not reused.

Each training session (about 2–3 a week within the free GPU quota):
1. Start the first session with `kaggle/kaggle_cycle.sh train <pushed commit> --fresh`.
   Add `--minutes 30` for the pilot.
2. After that, after every session, run `kaggle/kaggle_cycle.sh train <pushed commit>`.
   - It only continues when the previous session is COMPLETE.
   - It saves that session's checkpoints as a new `korokae-ckpt` version and then pushes the next
     session.
   - A session never restarts silently: without `--fresh` it refuses to start if no checkpoints
     are mounted.
3. Each session renders the same fixed val (64 × 10 s, seed 1001) and test (200 × 10 s, seed 2002)
   mixes and trains until 690 min after the notebook started. It writes a checkpoint every 30 min.
4. Progress lands in `ckpt/log.jsonl`: `loss`, `lr` and `sec_per_step` every 50 steps, plus
   `val_vocal_sdr` and `val_inst_sdr` every 2000 steps.
5. Fine-tune (spec 5, stage 4): run `kaggle/kaggle_cycle.sh init ft` once, then
   `kaggle/kaggle_cycle.sh finetune <commit> --fresh`. It starts from `korokae-ckpt`'s `best.pt`
   and keeps its own checkpoints in `korokae-ckpt-ft`.

After training:
- `python -m korokae_v2.evaluate --eval eval --checkpoint best.pt` runs the v1-vs-v2 gate.
  It must reach +5 dB instrumental SDR over v1.
- `python -m korokae_v2.export --checkpoint best.pt --out-dir DIR` writes `korokae_v2.onnx`.
- `python -m korokae_v2.listening_test prepare ...` and then `... score ...` run the blind test.
