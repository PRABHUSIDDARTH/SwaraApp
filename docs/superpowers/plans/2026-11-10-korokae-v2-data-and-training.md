# Korokae v2 Data and Training Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A licence-audited data pipeline, a resumable Kaggle training loop, and the evaluation
tools (synthetic SDR gate against v1, blind listening test) that turn the untrained KorokaeNet v2
from Plan 1 into a trained `korokae_v2.onnx`.

**Architecture:**
- **Data prep (Kaggle CPU notebooks, internet on).** Downloads each source straight from its
  official host, filters it by licence, and converts it to 44.1 kHz FLAC. Writes `manifest.csv`.
  The output is published as two private Kaggle datasets: `korokae-vocals` and `korokae-accomp`.
- **Training (Kaggle GPU notebook).** Reads random raw segments, then mixes and augments them on
  the GPU. Trains the Plan 1 model through the Plan 1 DSP. Writes a checkpoint every 30 min and
  exits cleanly before the session limit.
- **Between sessions (laptop).** `kaggle_cycle.sh` round-trips the ~40 MB checkpoint through a
  private dataset.
- **Evaluation (laptop).** Scores v1 and v2 on stored, fixed-seed test mixes.

**Tech Stack:** Python 3.14, PyTorch 2.15 (CPU on laptop, CUDA on Kaggle), soundfile, numpy,
ffmpeg (CLI), Kaggle CLI, ONNX Runtime 1.31 (export check).

**Spec:** `docs/superpowers/specs/2026-11-09-korokae-v2-design.md` (sections 3, 5, 7.2, 7.3; section 4 for the model).

## Scope and decisions taken in this plan

- **Swara-Synth (spec 3.2) is Plan 2b.** It is a separate subsystem (procedural raga/tala
  composer, FluidSynth rendering, SoundFont and sample-pack licence audit). Main training (spec 5,
  stage 3) starts on the sources below. Swara-Synth joins at the fine-tune stage (stage 4) via a
  new manifest row set, with no code change here.
- **FMA is deferred.** The spec requires every FMA "instrumental" track to be checked by ear for
  hidden vocals. Slakh2100 is vocal-free by construction (rendered from MIDI), so Plan 2 does not
  need FMA. It can be added later as manifest rows plus a review page.
- **ccMixter needs extra filters (found while writing this plan).**
  - The API ignores its `license=` parameter, so the fetcher filters on each record's
    `license_url` itself.
  - Some uploads are tagged `AI_generated`, `AI_assisted` or `ia_stems_generated`. Stems
    separated by someone else's model would make our model learn a third-party separator,
    which breaks spec constraint 1, so those uploads are rejected.
  - The yield of usable a cappellas is unknown until Task 3 measures it.
- **Pitch and time augmentation.** Spec 3.3 asks for pitch ±2 semitones and time ±10%, applied
  per stem. This plan implements them as one per-stem varispeed (resampling, ±2 semitones, which
  moves tempo ±12%), with no phase vocoder: robust and artifact-free. The two stems still move
  independently, which is what the model needs to learn.
- **The code reaches Kaggle by `git clone` at a pinned commit.** The repo is public, so no
  secrets are needed. Data and checkpoints travel as private Kaggle datasets.

## Global Constraints

- Ours end to end: no third-party model, pretrained weights, separation API or separation code; runtimes/tools only.
- Licences kept: `CC0-1.0`, `CC-BY-3.0`, `CC-BY-4.0`, `own`. Everything else is rejected. MUSDB18, MoisesDB, MedleyDB and any NC / ND / SA licence are excluded.
- Each source's licence is re-checked against its official page at download time, and that page's URL is stored in the manifest.
- The owner's songs are a private test set only: never trained on, never uploaded, never shipped.
- 44.1 kHz; STFT, model I/O and chunking come only from `korokae_v2.dsp` / `korokae_v2.model` (Plan 1).
- Training: mixed precision on T4/P100, 6 s segments (264600 samples), effective batch 16 via accumulation, AdamW with cosine schedule, checkpoint every ~30 min.
- Loss: waveform L1 + multi-resolution STFT magnitude L1 (FFT 512, 1024, 2048), on vocals and instrumental.
- Splits by source recording and singer. Test mixes are rendered once with a fixed seed and stored.
- Gate: median instrumental SDR v2 ≥ v1 + 5 dB on the stored test mixes.
- Python package root: `ml/korokae_v2/`; tests run with `.venv/bin/pytest -q` from there.

## Review Focus

1. Silent targets (a vocal-free segment or test clip): SDR and losses stay finite, with no NaN poisoning the median or the optimiser (Task 6, `test_sdr_silent_target_is_finite`, `test_loss_finite_on_silent_vocals`).
2. Sources that are mono, 48 kHz, MP3 or 24-bit come out as 44.1 kHz 16-bit FLAC with their channel count kept (Task 2, `test_prepare_converts_rate_and_keeps_channels`).
3. A Kaggle session killed mid-save: the checkpoint is written atomically, and resume picks the newest complete file (Task 8, `test_partial_checkpoint_is_ignored`).
4. Mastering, gain and limiting in augmentation keep `vocals + accompaniment == mix` exactly, so the targets match the input (Task 5, `test_mix_equals_sum_of_targets`).
5. Licence strings arrive in many spellings and URL forms. Every allowed form normalises, and anything unknown, NC, SA or ND is rejected. An AI-tagged ccMixter upload is rejected (Task 1, `test_normalise_licence_forms`; Task 3, `test_ccmixter_rejects_ai_and_nc`).

---

### Task 1: Manifest, licence audit and credits

**Files:**
- Create: `ml/korokae_v2/korokae_v2/data/__init__.py`, `ml/korokae_v2/korokae_v2/data/manifest.py`, `ml/korokae_v2/korokae_v2/data/credits.py`
- Test: `ml/korokae_v2/tests/test_manifest.py`

**Interfaces:**
- Produces:
  - `ALLOWED_LICENCES = ("CC0-1.0", "CC-BY-3.0", "CC-BY-4.0", "own")`.
  - `@dataclass(frozen=True) class Row`, with fields `path: str` (relative to the data root), `role: str` (`"vocal"` | `"accompaniment"`), `source`, `source_url`, `author`, `licence`, `attribution`, `group` (singer or recording id, used for splitting) and `split` (`"train"` | `"val"` | `"test"`).
  - `normalise_licence(text: str) -> str | None`.
  - `write_manifest(rows: list[Row], path: str) -> None`.
  - `read_manifest(path: str) -> list[Row]`.
  - `audit(rows: list[Row], data_root: str) -> list[str]`, which returns problems; an empty list means it passes.
  - `credits_text(rows: list[Row]) -> str`.
  - CLI: `python -m korokae_v2.data.manifest audit --root DIR` exits 1 when there are problems.
  - CLI: `python -m korokae_v2.data.credits --manifest M --out F`.

- [ ] **Step 1: Write the failing tests**

```python
@pytest.mark.parametrize("text,want", [
    ("CC0 1.0", "CC0-1.0"), ("https://creativecommons.org/publicdomain/zero/1.0/", "CC0-1.0"),
    ("Creative Commons Attribution 4.0 International", "CC-BY-4.0"), ("CC BY 4.0", "CC-BY-4.0"),
    ("https://creativecommons.org/licenses/by/3.0/", "CC-BY-3.0"), ("Attribution (4.0)", "CC-BY-4.0"),
    ("own", "own")])
def test_normalise_licence_forms(text, want): assert normalise_licence(text) == want

@pytest.mark.parametrize("text", ["CC BY-NC 4.0", "https://creativecommons.org/licenses/by-sa/4.0/",
    "CC BY-ND 3.0", "https://creativecommons.org/licenses/by-nc-sa/3.0/", "All rights reserved", ""])
def test_normalise_licence_rejects(text): assert normalise_licence(text) is None

def test_manifest_round_trip(tmp_path): ...  # write 2 rows, read back equal (order kept)
def test_audit_flags_bad_licence_missing_file_and_unlisted_file(tmp_path): ...
    # rows: one ok (file exists), one with licence "CC-BY-NC-4.0", one whose file is missing;
    # plus an audio file on disk that no row lists -> audit returns exactly 3 problems naming each path
def test_credits_lists_each_cc_by_author_once_and_skips_cc0_and_own(): ...
```

- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_manifest.py -q`. Expected: FAIL with `ModuleNotFoundError: korokae_v2.data`.
- [ ] **Step 3: Implement** `manifest.py` and `credits.py`.
  - Normalise by lowercasing, then matching CC URL paths (`/licenses/by/X/`, `/publicdomain/zero/1.0/`) and the words `attribution`/`by` plus a version.
  - Any `nc`, `nd` or `sa` token means reject.
  - The manifest is CSV with the `Row` field order as its header.
  - `audit` walks `*.flac` under the root.
  - Credits list one line per (source, author): `"<attribution> — <licence> — <source_url>"`, sorted by source and then author.
- [ ] **Step 4: Run** `.venv/bin/pytest tests/test_manifest.py -q`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(korokae-v2): licence-audited manifest and credits`

### Task 2: Audio preparation and splits

**Files:**
- Create: `ml/korokae_v2/korokae_v2/data/prep.py`, `ml/korokae_v2/korokae_v2/data/splits.py`
- Modify: `ml/korokae_v2/requirements.txt` (add `soundfile>=0.13`)
- Test: `ml/korokae_v2/tests/test_prep.py`

**Interfaces:**
- Produces:
  - `to_flac(src: str, dst: str) -> float`. It converts to 44.1 kHz, 16-bit FLAC with the channel count kept, writes `dst` atomically (a `.part` file, then rename) and returns the duration in seconds. It raises `PrepError` on ffmpeg failure.
  - `assign_splits(groups: list[str], test_frac=0.1, val_frac=0.1) -> dict[str, str]`.

- [ ] **Step 1: Write the failing tests**

```python
def test_prepare_converts_rate_and_keeps_channels(tmp_path):
    # make a 48 kHz mono 24-bit WAV and a 44.1 kHz stereo MP3 with ffmpeg (sine), convert both
    # -> soundfile.info: samplerate 44100, subtype PCM_16, channels 1 and 2; duration within 10 ms
def test_failed_conversion_raises_and_leaves_no_file(tmp_path): ...  # src is a text file -> PrepError, no dst/.part
def test_splits_deterministic_nonempty_and_disjoint():
    g = [f"singer{i}" for i in range(20)]
    a, b = assign_splits(g), assign_splits(list(reversed(g)))
    assert a == b and {"train", "val", "test"} == set(a.values())
def test_splits_tiny_groups(): ...  # 1 group -> train; 2 -> train+test; 3 -> one of each
```

- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_prep.py -q`. Expected: FAIL with an import error.
- [ ] **Step 3: Implement.**
  - `to_flac` runs `ffmpeg -nostdin -v error -i src -ar 44100 -sample_fmt s16 -c:a flac dst.part`.
  - `assign_splits`: sort groups by `sha1(group)`, then take the first `max(1, round(test_frac*n))` as test and the next `max(1, round(val_frac*n))` as val (n ≥ 3); the rest are train.
- [ ] **Step 4: Run** `.venv/bin/pytest tests/test_prep.py -q`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(korokae-v2): audio prep to 44.1 kHz FLAC and group splits`

### Task 3: Source fetchers

**Files:**
- Create: `ml/korokae_v2/korokae_v2/data/sources.py`, `ml/korokae_v2/korokae_v2/data/build.py`
- Test: `ml/korokae_v2/tests/test_sources.py`

**Interfaces:**
- Consumes: `Row`, `normalise_licence`, `write_manifest`, `audit` (Task 1); `to_flac`, `assign_splits` (Task 2).
- Produces:
  - One generator per source, `fetch_<source>(work: str, out: str, http: Http, budget_s: float) -> Iterator[Row]`, for `vocalset`, `vocadito`, `slakh` and `ccmixter`.
  - `Http` is a protocol with `get_json(url) -> Any` and `download(url, dst) -> None`, plus `stream(url) -> BinaryIO` for Slakh. Tests pass a fake.
  - CLI: `python -m korokae_v2.data.build --sources vocalset,vocadito,ccmixter --out DIR --budget-hours H`. It writes `DIR/audio/<source>/...flac` and `DIR/manifest.csv`, then runs `audit`. `--dry-run`
  lists and licence-filters records, printing accepted/rejected counts by reason and distinct
  groups, without downloading audio.

Source rules (the licence page URL is stored in every row):

| Source | Official host | Licence check at download | group | Budget |
|---|---|---|---|---|
| VocalSet v1.1 | `https://zenodo.org/records/1203819` (Zenodo API `/api/records/1203819`) | record `metadata.license.id` must normalise to CC-BY-4.0 | singer folder (`female1` … `male11`) | all (~10 h) |
| vocadito | Zenodo `/api/records/5578807` | same | singer id from its metadata CSV | all |
| ccMixter a cappellas | `https://ccmixter.org/api/query?f=json&tags=acappella&limit=100&offset=N` | per record: `license_url` normalises to an allowed licence; reject if `upload_tags` contains `AI_generated`, `AI_assisted` or `ia_stems_generated`; prefer the `.flac` file, else `.mp3` | `user_name` | ≤ 10 GB |
| Slakh2100 redux | Zenodo `/api/records/4599666`, the `slakh2100_flac_redux.tar.gz` | record licence CC-BY-4.0 | track id, using Slakh's own train/validation/test folders as the split | train ≤ 70 h, val ≤ 5 h, test ≤ 5 h, `mix.flac` only, streamed with `tarfile` mode `r|gz` (never stored whole) |

Politeness: ccMixter pages are fetched 1 s apart with 3 retries and exponential backoff. An empty
page ends paging only after 3 consecutive empties. This plan observed rate limiting on large
`limit` values.

- [ ] **Step 1: Write the failing tests** (a fake `Http` serves fixture JSON and tiny generated audio)

```python
def test_vocalset_rows_have_cc_by_singer_groups_and_record_url(tmp_path): ...
def test_zenodo_record_with_non_allowed_licence_raises(tmp_path): ...   # metadata.license.id "cc-by-nc-4.0"
def test_ccmixter_rejects_ai_and_nc(tmp_path):
    # 4 records: CC BY 4.0 ok; CC BY-NC; CC BY tagged ",ia_stems_generated,"; CC0 tagged ",AI_assisted,"
    # -> exactly 1 Row, licence "CC-BY-4.0", author = user_name, source_url = license_url
def test_ccmixter_paging_survives_empty_pages(tmp_path): ...  # empty, empty, data, empty x3 -> rows from the data page
def test_slakh_stream_keeps_only_mix_and_respects_budget(tmp_path): ...  # fake tar.gz with 3 tracks x (mix + stems)
def test_build_writes_manifest_that_passes_audit(tmp_path): ...
```

- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_sources.py -q`. Expected: FAIL with an import error.
- [ ] **Step 3: Implement the fetchers and `build.py`.** The real `Http` uses `urllib.request` with User-Agent `SwaraKorokaeDataPrep/1.0`. `build` assigns splits per source with `assign_splits` over that source's groups (Slakh keeps its own split).
- [ ] **Step 4: Run** `.venv/bin/pytest tests/test_sources.py -q`. Expected: all pass.
- [ ] **Step 5: Live probe, read-only, on the laptop:**
  `.venv/bin/python -m korokae_v2.data.build --sources ccmixter --out /tmp/probe --budget-hours 0 --dry-run`.
  Expected: it prints the counts of accepted and rejected records (by reason) and the distinct singers, and downloads no audio. Record the numbers in `ml/korokae_v2/README.md` under "Data". If fewer than 200 a cappellas are accepted, note it, because vocal variety then rests on VocalSet + vocadito until owner recordings arrive.
- [ ] **Step 6: Commit** `feat(korokae-v2): licence-checked fetchers for VocalSet, vocadito, ccMixter, Slakh`

### Task 4: Segment loader

**Files:**
- Create: `ml/korokae_v2/korokae_v2/data/segments.py`
- Test: `ml/korokae_v2/tests/test_segments.py`

**Interfaces:**
- Consumes: `read_manifest`, `Row` (Task 1).
- Produces:
  - `class StemPool(rows: list[Row], root: str, role: str, split: str)`, with `sample(rng: np.random.Generator, length: int, min_rms_db: float = -50.0, tries: int = 10) -> np.ndarray`. It returns float32 `[2, length]`, duplicating mono to both channels, and is duration-weighted across files.
  - `class PairDataset(torch.utils.data.IterableDataset)`, built as `PairDataset(manifest, root, split, length=264600, seed)`. It yields `(vocal [2,L], accomp [2,L])` raw, and each worker uses `seed + worker_id`.

- [ ] **Step 1: Write the failing tests**

```python
def test_sample_shape_dtype_and_mono_duplication(tmp_path): ...
def test_sample_prefers_active_audio(tmp_path): ...       # file = 5 s silence + 5 s tone; 50 draws of 2 s -> >= 45 have rms > -50 dB
def test_sample_pads_files_shorter_than_length(tmp_path): ...  # 1 s file, length 3 s -> zeros after 1 s
def test_pool_is_reproducible_for_a_seed(tmp_path): ...
def test_pair_dataset_only_uses_requested_split(tmp_path): ...
```

- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_segments.py -q`. Expected: FAIL with an import error.
- [ ] **Step 3: Implement.** Use `soundfile.SoundFile` to seek to a random frame and read `length` frames. A file shorter than `length` is zero-padded at the end. Retry up to `tries` times until RMS > `min_rms_db`, then return the last draw.
- [ ] **Step 4: Run** `.venv/bin/pytest tests/test_segments.py -q`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(korokae-v2): random stem segment loader`

### Task 5: Mixing and augmentation

**Files:**
- Create: `ml/korokae_v2/korokae_v2/augment.py`
- Test: `ml/korokae_v2/tests/test_augment.py`

**Interfaces:**
- Produces:
  - `@dataclass AugmentConfig`, with the spec 3.3 defaults:
    - `level_db=(-10, 10)`
    - `varispeed_semitones=2.0`, `p_varispeed=0.5`
    - `p_reverb=0.5`, `rt60_s=(0.2, 2.0)`
    - `eq_db=6.0`, `p_eq=0.7`
    - `p_master=0.8`, `comp_ratio=(1.5, 6.0)`, `limit_ceiling_db=(-1.0, -0.1)`
    - `width=(0.0, 1.0)`, `pan=(-0.5, 0.5)`
  - `mix_batch(vocal: Tensor[B,2,L], accomp: Tensor[B,2,L], cfg: AugmentConfig, gen: torch.Generator) -> tuple[mix, vocal_t, accomp_t]`, all `[B,2,L]` float32 on the inputs' device.
  - Building blocks, each with its own test:
    - `varispeed(x, semitones)`: FFT resample to `round(L / 2**(s/12))` samples, then crop or pad back to L.
    - `reverb(x, ir)`: FFT convolution, cropped to L.
    - `make_ir(rt60, gen) -> Tensor[2, n]`: stereo exponentially decaying noise plus 3–8 sparse early reflections, normalised to unit energy; our own generator.
    - `random_eq(x, gen)`: a smooth random gain curve over log-frequency within ±`eq_db`, applied on an STFT.
    - `pan_width(x, pan, width)`.
    - `master_gain(mix) -> Tensor[B,1,L]`: a time-varying gain from a block-RMS compressor (10 ms blocks, smoothed with avg-pool) followed by a peak limiter gain. It is applied identically to both stems, so the stems still sum to the mix.

- [ ] **Step 1: Write the failing tests**

```python
def test_mix_equals_sum_of_targets():
    v, a = torch.randn(4, 2, 44100), torch.randn(4, 2, 44100) * 3   # loud: forces limiting
    m, vt, at = mix_batch(v, a, AugmentConfig(p_master=1.0), torch.Generator().manual_seed(0))
    assert torch.allclose(m, vt + at, atol=1e-6) and m.abs().max() <= 1.0 + 1e-6
def test_varispeed_shifts_pitch(): ...        # 440 Hz sine, +2 st -> spectral peak 493.9 Hz +- 3 Hz, length unchanged
def test_reverb_extends_tail_and_ir_is_unit_energy(): ...
def test_eq_bounded(): ...                    # white noise -> per-band energy change within +-(eq_db + 1) dB
def test_level_range_respected(): ...         # p_* = 0 -> 10*log10(E_v/E_a) - (input ratio) within [-10, 10]
def test_mono_vocal_gets_stereo_image(): ...  # identical L/R vocal, width>0 or pan!=0 -> L != R
def test_deterministic_for_generator_seed(): ...
```

- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_augment.py -q`. Expected: FAIL with an import error.
- [ ] **Step 3: Implement.**
  - Per stem: varispeed, then EQ, then pan/width. Reverb is applied to the vocal only.
  - Scale the vocal to the random level relative to the accompaniment.
  - Then apply `g = master_gain(v + a)` with probability `p_master`.
  - Finally normalise so `|mix|max ≤ 1` (the same scalar on both stems).
- [ ] **Step 4: Run** `.venv/bin/pytest tests/test_augment.py -q`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(korokae-v2): on-GPU mixing and augmentation that keeps stems summing to the mix`

### Task 6: Batched separation, SDR and loss

**Files:**
- Create: `ml/korokae_v2/korokae_v2/forward.py`, `ml/korokae_v2/korokae_v2/metrics.py`, `ml/korokae_v2/korokae_v2/losses.py`
- Test: `ml/korokae_v2/tests/test_forward_metrics_losses.py`

**Interfaces:**
- Consumes: `dsp.stft`, `dsp.istft`, `dsp.BINS` (Plan 1); `KorokaeNetV2` (Plan 1).
- Produces:
  - `separate_batch(model, mix: Tensor[B,2,L]) -> tuple[vocals, instrumental]`, both `[B,2,L]`. It uses the same `stft`, `[B,4,BINS,T]` layout and `X − M·X` as `dsp.separate`, without chunking. Callers pass L ≤ 8 s.
  - `sdr(est: Tensor[...,L], ref: Tensor[...,L], eps=1e-8) -> Tensor[...]`, computed as `10·log10((‖ref‖² + eps) / (‖ref − est‖² + eps))` over the channel and time axes. This is our own implementation.
  - `separation_loss(vocals_hat, inst_hat, vocals, inst) -> Tensor`: the sum of waveform L1 and multi-resolution STFT magnitude L1 (FFT 512/1024/2048, hop = FFT/4, Hann window), on both stems, with equal weights.

- [ ] **Step 1: Write the failing tests**

```python
def test_separate_batch_matches_dsp_separate_for_one_window():
    # model = KorokaeNetV2(n_blocks=1, d=16, heads=2), x [2, 352800] -> separate_batch(model, x[None])[1][0]
    # equals dsp.separate(x, model) within 1e-5
def test_vocals_plus_instrumental_equals_mix(): ...
def test_sdr_known_value(): ...                # est = ref + noise at exactly -20 dB -> 20.0 +- 0.01
def test_sdr_silent_target_is_finite(): ...    # ref zeros, est small noise -> finite, < 0
def test_loss_zero_for_perfect_estimate_and_positive_otherwise(): ...
def test_loss_finite_on_silent_vocals(): ...   # vocals = zeros -> finite and gradients finite
```

- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_forward_metrics_losses.py -q`. Expected: FAIL with an import error.
- [ ] **Step 3: Implement.** In `losses.py`, the STFT magnitude uses `sqrt(re² + im² + 1e-8)`, so the gradient stays finite at zero.
- [ ] **Step 4: Run** `.venv/bin/pytest tests/test_forward_metrics_losses.py -q`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(korokae-v2): batched separation, own SDR, multi-resolution loss`

### Task 7: Fixed validation and test mixes

**Files:**
- Create: `ml/korokae_v2/korokae_v2/render_eval.py`
- Test: `ml/korokae_v2/tests/test_render_eval.py`

**Interfaces:**
- Consumes: `StemPool` (Task 4), `mix_batch`, `AugmentConfig` (Task 5), `read_manifest` (Task 1).
- Produces:
  - `render(manifest, root, split, n_clips, seconds, seed, out_dir) -> None`. It writes `out_dir/<split>/<i:04d>_{mix,vocals,accomp}.flac` (PCM_24) plus `meta.json` with `{seed, n_clips, seconds, manifest_sha1, sources per clip}`.
  - `load_clips(out_dir, split) -> Iterator[(mix, vocals, accomp)]`, each as Tensor[2, L].
  - CLI: `python -m korokae_v2.render_eval --split val --n 64 --seconds 10 --seed 1001` and `--split test --n 200 --seconds 10 --seed 2002`.

- [ ] **Step 1: Write the failing tests**

```python
def test_render_is_bit_identical_for_same_seed(tmp_path): ...   # render twice -> identical file bytes (except meta timestamps: none written)
def test_rendered_mix_equals_vocals_plus_accomp(tmp_path): ...  # within PCM_24 quantisation (1e-6)
def test_meta_records_manifest_hash_and_seed(tmp_path): ...
def test_render_uses_only_requested_split(tmp_path): ...
```

- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_render_eval.py -q`. Expected: FAIL with an import error.
- [ ] **Step 3: Implement.** Use CPU only and the default `AugmentConfig`. The generator is seeded from `seed` and the clip index, so clip i never depends on how many clips are rendered.
- [ ] **Step 4: Run** `.venv/bin/pytest tests/test_render_eval.py -q`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(korokae-v2): fixed-seed validation and test mix renderer`

### Task 8: Resumable training loop

**Files:**
- Create: `ml/korokae_v2/korokae_v2/train.py`, `ml/korokae_v2/configs/train_main.yaml`, `ml/korokae_v2/configs/train_finetune.yaml`, `ml/korokae_v2/configs/smoke.yaml`
- Test: `ml/korokae_v2/tests/test_train.py`

**Interfaces:**
- Consumes: `KorokaeNetV2` and `configs/base.yaml` (`model:` keys) from Plan 1; `PairDataset` (Task 4); `mix_batch` (Task 5); `separate_batch`, `sdr`, `separation_loss` (Task 6); `load_clips` (Task 7).
- Produces:
  - `train(cfg: dict, data_root: str, eval_root: str, ckpt_dir: str, max_minutes: float) -> dict`. It returns `{"step", "best_val_inst_sdr", "stopped": "done" | "time"}`.
  - Checkpoints: `ckpt_dir/step_<n:08d>.pt` and `ckpt_dir/best.pt`. Each holds `{model, optimizer, scheduler, scaler, step, rng: {torch, cuda, numpy, python}, cfg, git_hash, best}`.
  - Log: `ckpt_dir/log.jsonl`, one JSON line per log event with `{step, loss, lr, sec_per_step}`, plus `{step, val_vocal_sdr, val_inst_sdr}` at each validation.
  - CLI: `python -m korokae_v2.train --config configs/train_main.yaml --data D --eval E --ckpt C --max-minutes 690`.

Config keys (values for `train_main.yaml`; `smoke.yaml` uses the tiny model, CPU and 20 steps):

| Key | Value |
|---|---|
| `base` | `configs/base.yaml` (model) |
| `segment` | 264600 |
| `batch` × `accum` | 8 × 2 (effective 16) |
| `optimizer` | AdamW, `lr` 3e-4, betas (0.9, 0.999), `weight_decay` 0.01, grad clip 5.0 |
| `schedule` | 2000 warm-up steps, then cosine to 1e-6 at `total_steps` |
| `total_steps` | set in Task 11 from the measured `sec_per_step` (target ~70 GPU-h) |
| `amp` | `true` (fp16 autocast around the model only; STFT/iSTFT in fp32) |
| `ckpt_every_minutes` | 30 |
| `keep_last` | 3 |
| `val_every` | 2000 |
| `workers` | 4 |

`train_finetune.yaml` loads `best.pt` weights only, with `lr` 5e-5, 500 warm-up steps and harder
augmentation (`level_db (-12, 6)`, `p_master 1.0`, `p_reverb 0.7`).

- [ ] **Step 1: Write the failing tests** (tiny model, tiny generated dataset, CPU)

```python
def test_smoke_training_reduces_loss(tmp_path): ...            # 30 steps: mean loss of last 5 < first 5
def test_resume_is_exact(tmp_path):
    # run A: 10 steps straight. run B: 6 steps, stop, resume to 10 -> identical final weights (atol 0)
def test_time_budget_stops_with_checkpoint(tmp_path): ...       # max_minutes tiny -> stopped == "time", a step_*.pt exists
def test_partial_checkpoint_is_ignored(tmp_path): ...           # newest file truncated / *.tmp present -> resumes from previous good one
def test_best_tracks_val_inst_sdr_and_log_has_git_hash(tmp_path): ...
def test_keep_last_prunes_old_checkpoints(tmp_path): ...
```

- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_train.py -q`. Expected: FAIL with an import error.
- [ ] **Step 3: Implement `train.py`.**
  - Checkpoints are written with `torch.save` to `*.tmp`, then `os.replace`.
  - Resume loads the newest `step_*.pt` that loads without error.
  - The mixing generator is seeded from `(seed, step)`, so resume is exact.
  - Validation runs `separate_batch` over `load_clips(eval_root, "val")` and takes the median `sdr`.
  - On CUDA, use `torch.backends.cudnn.benchmark = True`.
- [ ] **Step 4: Run** `.venv/bin/pytest tests/test_train.py -q`. Expected: all pass.
- [ ] **Step 5: Laptop smoke (spec stage 1):** `.venv/bin/python -m korokae_v2.train --config configs/smoke.yaml ...` on a 2-file generated dataset. Expected: finishes in < 5 min and prints `stopped=done`.
- [ ] **Step 6: Commit** `feat(korokae-v2): resumable mixed-precision training loop with validation`

### Task 9: Evaluation against v1

**Files:**
- Create: `ml/korokae_v2/korokae_v2/v1_baseline.py`, `ml/korokae_v2/korokae_v2/evaluate.py`
- Test: `ml/korokae_v2/tests/test_evaluate.py`

**Interfaces:**
- Consumes: `load_clips` (Task 7), `sdr` (Task 6), `dsp.separate` (Plan 1); `ml/korokae/model.py` (v1 `KorokaeNet`, `stft`, `istft`, `HOP`, `N_FFT`), loaded by file path with `importlib` so its top-level module name `model` cannot clash.
- Produces:
  - `load_v1(path="app/src/main/assets/models/korokae_v1.bin") -> KorokaeNet`, which reads the `KRK1` layout written by `ml/korokae/export.py:export_weights`.
  - `v1_instrumental(net, x: Tensor[2,L]) -> Tensor[2,L]`: v1's offline equivalent (`streaming_reference`), cropped to `[HOP : HOP+L]`.
  - `evaluate(eval_root, checkpoint_or_None, split="test") -> dict`. It returns `{"v1": {"vocal", "inst"}, "v2": {...}, "gate_pass": bool, "n": int}`, with medians in dB. v2's vocals are `mix − instrumental`, using `dsp.separate` (the phone's chunking).
  - CLI: `python -m korokae_v2.evaluate --eval E --checkpoint best.pt` prints a table and writes `eval_<git7>.json`.

- [ ] **Step 1: Write the failing tests**

```python
def test_load_v1_round_trips_export_format(tmp_path, monkeypatch):
    # random v1 KorokaeNet -> v1 export.export_weights with ASSET monkeypatched to tmp -> load_v1 -> equal state_dict
def test_load_v1_rejects_bad_magic(tmp_path): ...
def test_v1_instrumental_keeps_length(): ...
def test_gate_uses_plus_5_db_on_instrumental(tmp_path): ...  # fake scores: v2 = v1 + 4.9 -> False; + 5.0 -> True
def test_evaluate_runs_end_to_end_on_tiny_rendered_set(tmp_path): ...
```

- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_evaluate.py -q`. Expected: FAIL with an import error.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `.venv/bin/pytest tests/test_evaluate.py -q`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(korokae-v2): synthetic-set evaluation with v1 baseline and +5 dB gate`

### Task 10: Export a trained checkpoint

**Files:**
- Modify: `ml/korokae_v2/korokae_v2/export.py` (add `--checkpoint`; keep `--untrained`)
- Test: `ml/korokae_v2/tests/test_export.py`

**Interfaces:**
- Consumes: the checkpoint format from Task 8.
- Produces: `python -m korokae_v2.export --checkpoint best.pt --out-dir DIR`, which writes `DIR/korokae_v2.onnx` and `DIR/korokae_v2_parity.bin` (the spec 5 names) and prints the params, size and the git hash stored in the checkpoint.

- [ ] **Step 1: Write the failing test** `test_export_from_checkpoint_matches_torch(tmp_path)`. Train a tiny model for 2 steps (Task 8 smoke), export it, and check that ORT and torch agree within 1e-4 on T=37 and that the parity fixture header is KRP2.
- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_export.py -q`. Expected: the new test FAILS with an argparse error on `--checkpoint`.
- [ ] **Step 3: Implement.** Build the model from `cfg["model"]` stored in the checkpoint.
- [ ] **Step 4: Run** `.venv/bin/pytest -q` (the whole suite). Expected: all pass.
- [ ] **Step 5: Commit** `feat(korokae-v2): export trained checkpoints to korokae_v2.onnx`

### Task 11: Kaggle notebooks and the session cycle

**Files:**
- Create:
  - `ml/korokae_v2/kaggle/prep_vocals/{kernel-metadata.json,run.py}` and `ml/korokae_v2/kaggle/prep_accomp/{kernel-metadata.json,run.py}` (CPU, internet on)
  - `ml/korokae_v2/kaggle/train/{kernel-metadata.json,run.py}` (GPU T4, internet on)
  - `ml/korokae_v2/kaggle/kaggle_cycle.sh`
  - `ml/korokae_v2/tests/test_kaggle_files.py`
- Modify: `ml/korokae_v2/README.md` (runbook)

**Interfaces:**
- Consumes: the `build` CLI (Task 3), the `render_eval` CLI (Task 7), the `train` CLI (Task 8).
- Produces:
  - **Data notebooks.** Each `run.py` clones `https://github.com/PRABHUSIDDARTH/SwaraApp` at the commit in `run.py`'s `COMMIT` constant, installs requirements, and runs `build`.
    - `prep_vocals`: VocalSet + vocadito + ccMixter.
    - `prep_accomp`: Slakh.
    - Each writes to `/kaggle/working` (which must stay ≤ 20 GB).
    - `prep_accomp` also runs `render_eval` for val and test, so the fixed mixes live next to the data.
  - **Train notebook.** Mounts `korokae-vocals`, `korokae-accomp` and `korokae-ckpt` (private datasets). Copies the checkpoints into `/kaggle/working/ckpt` and runs `train --max-minutes 690`.
  - **`kaggle_cycle.sh <commit>`.** It must not change anything if any one of these steps fails:
    1. Check the previous run's status. Download its output (`kaggle kernels output`).
    2. Publish it as a new version of `korokae-ckpt` (`kaggle datasets version`).
    3. Stamp the commit into `train/run.py`.
    4. `kaggle kernels push`.
    5. Print the last validation line of `log.jsonl`.

- [ ] **Step 1: Write the failing test** `test_kaggle_metadata_is_valid`:
  - Every `kernel-metadata.json` parses.
  - The prep kernels have `enable_gpu false` and `enable_internet true`.
  - The train kernel has `enable_gpu true` and lists the 3 dataset sources.
  - Every `run.py` pins a 40-hex `COMMIT`.
  - `kaggle_cycle.sh` passes `bash -n` and uses `set -euo pipefail`.
- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_kaggle_files.py -q`. Expected: FAIL (files missing).
- [ ] **Step 3: Implement** the files and the README runbook: one-time Kaggle setup, data prep, the weekly training cycle and reading `log.jsonl`.
- [ ] **Step 4: Run** `.venv/bin/pytest -q`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(korokae-v2): Kaggle data-prep and training notebooks with session cycle`
- [x] **Step 6: Owner step.**
  - The Kaggle API token (new `KGAT_` format) is stored at `~/.kaggle/access_token` (mode 600).
  - Kaggle CLI 2.2.5 is installed in `ml/korokae_v2/.venv`. It needs `six`, which it doesn't declare, so add `kaggle` and `six` to `requirements.txt` in Step 3.
  - The account is `prabhusiddarthav`, which is the owner of the private datasets and kernels.
  - Still open: phone verification for GPU and internet. Confirm it on the account page before Step 7.
- [ ] **Step 7: Push the code and run data prep.**
  1. Push the branch so the commit is cloneable, then push both prep kernels.
  2. Expected: both complete.
     - `korokae-vocals` and `korokae-accomp` are created from their outputs.
     - `manifest audit` passes in each.
     - The README gets each source's hours, file count, and accepted and rejected counts.
- [ ] **Step 8: Pilot session.**
  1. Push the train kernel with `total_steps: 1000000` and `--max-minutes 30`.
  2. Expected: it completes with a checkpoint and `sec_per_step` in `log.jsonl`.
  3. Set `total_steps = floor(0.9 × 70 h × 3600 / sec_per_step)` in `train_main.yaml` and record the measurement in the README.
- [ ] **Step 9: Commit** `chore(korokae-v2): data prep results and total_steps from pilot`. Then open a PR. Main training is operational from here: run `kaggle_cycle.sh` once per session, about 2–3 sessions a week within the free quota.

### Task 12: Blind listening test

**Files:**
- Create: `ml/korokae_v2/korokae_v2/listening_test.py`
- Test: `ml/korokae_v2/tests/test_listening_test.py`

**Interfaces:**
- Consumes: `load_v1`, `v1_instrumental` (Task 9); `dsp.separate`, the v2 checkpoint or ONNX (Task 10).
- Produces:
  - `python -m korokae_v2.listening_test prepare --songs songs.csv --v2 korokae_v2.onnx --out DIR`.
    - `songs.csv` has the columns `path,start_s`, where `start_s` is optional. When it is empty, the clip is the 30 s window with the highest RMS (1 s hop), a chorus heuristic.
    - Writes `DIR/clips/<i>_{A,B}.m4a` with A/B randomised per clip (seed stored in `DIR/key.json`) and `DIR/index.html`. The page has two 1–5 ratings per clip ("voice gone", "music clean") for each of A and B, plus "which is better", and an Export button that downloads `answers.json`.
  - `python -m korokae_v2.listening_test score --dir DIR --answers answers.json` prints the spec section 1 criteria and PASS/FAIL. The criteria are: v2 preferred on ≥ 90% of clips, mean v2 "music clean" ≥ 4 and mean v2 "voice gone" ≥ 4.
  - Privacy: everything stays under `DIR` on the laptop. The page loads nothing from the network, and the module never uploads.

- [ ] **Step 1: Write the failing tests**

```python
def test_chorus_heuristic_picks_loudest_30s(tmp_path): ...    # 60 s file: quiet, loud 20-50 s, quiet -> start 20 +- 1
def test_ab_order_is_random_but_recorded(tmp_path): ...       # 20 clips: both orders occur; key.json maps each clip
def test_html_is_self_contained(tmp_path): ...                # no "http://" / "https://" in index.html
def test_score_criteria(): ...                                # synthetic answers at 89% vs 90% preference, means 3.9 vs 4.0
def test_score_unblinds_with_key(): ...                       # a rating given to "A" counts for the model A was
```

- [ ] **Step 2: Run** `.venv/bin/pytest tests/test_listening_test.py -q`. Expected: FAIL with an import error.
- [ ] **Step 3: Implement.** Decode the songs with ffmpeg to 44.1 kHz stereo (mono is duplicated). Encode the clips to AAC with ffmpeg. The HTML is inline (no external CSS/JS).
- [ ] **Step 4: Run** `.venv/bin/pytest -q`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(korokae-v2): local blind A/B listening test`

---

## After this plan

- **Main training** (spec 5, stage 3) runs through `kaggle_cycle.sh`. At each session the curves and validation SDR are reviewed against the previous best.
- **When validation SDR plateaus:**
  1. Run `evaluate` on the test mixes; the +5 dB gate decides.
  2. Fine-tune (`train_finetune.yaml`, with Swara-Synth once Plan 2b lands).
  3. Export the result.
  4. Run the listening test with the owner's 20–30 songs.
- **Plan 2b:** Swara-Synth. **Plan 3:** app integration, including the 500 MB streaming memory gate.
