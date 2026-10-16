# Vibe Mode & On-Device Recommender — Design

Date: 2026-10-17
Status: Approved in brainstorming, awaiting spec review
Sub-project 1 of the "own our models" effort (sub-project 2 is Korokae v2, designed separately).

## 1. Goal and constraints

Swara will ship on the Play Store, free and ad-free, aiming for broad adoption, while keeping
commercial options open later. Every model must therefore be trained on data we own or that is
licensed for commercial use, or must need no training data at all.

This sub-project replaces the lost `vibe_v1.bin` with a recommender that:

- runs entirely on the device and sends no library or listening data anywhere;
- ships no weights trained on third-party audio (it learns per user, on the device);
- powers three features: **Vibe Mode**, **Autoplay** when the queue ends, and **"For you"** mixes on Home.

The user's downloaded film songs are used only for on-device testing, never for training a shipped model.

The recovered `app/src/main/assets/models/vibe_v1.bin` is not used by this design and should be removed
before release.

### Success criteria

1. Starting Vibe Mode on a low-valence song produces a session whose valence rises smoothly to the
   goal, with no single step above the per-song cap. Covered by unit tests.
2. On the developer's own library, a spot check of the mood debug list puts clearly sad songs in the
   bottom third of valence and clearly upbeat songs in the top third.
3. No network permission or network calls are added.
4. User-facing copy makes no health claims ("lifts your mood gently" is fine; "treats depression" is not).

## 2. Architecture

Three units, each with one job, communicating through small interfaces:

```
 Audio files ─▶ [1] Mood analysis ─▶ song_mood (Room)
                                            │
 Player events ─▶ [2] Taste profile ─▶ listen_events (Room)
                                            │
                                            ▼
                              [3] Recommender (scoring)
                     ┌──────────────┼───────────────┐
                Vibe Mode        Autoplay       "For you" mixes
```

### 2.1 Mood analysis

- `MoodScorer` interface: `MoodResult score(Uri song)`, where `MoodResult` holds `valence` (0–1),
  `energy` (0–1), `tempoBpm`, a raw feature vector and a `scorerVersion`.
- `AudioFeatureScorer` is the first implementation:
  1. Decode 30 s from the middle of the track (skipping intro and outro) with `MediaExtractor` and
     `MediaCodec`, downmix to mono and resample to 22.05 kHz.
  2. Compute features: RMS loudness, onset rate (spectral flux), tempo (onset autocorrelation),
     spectral centroid (brightness), and major/minor mode (chroma correlated against
     Krumhansl-Schmuckler key profiles, 24 keys).
  3. Raw energy is a weighted combination of loudness, onset rate and tempo. Raw valence is a
     weighted combination of mode, brightness and tempo. The weights are constants in one class,
     tuned using the debug list (section 6).
  4. Normalize per library: stored valence and energy are the song's percentile rank of the raw
     values within the user's analyzed library. This absorbs genre and mastering loudness; film
     songs are mostly mastered loud. Percentiles are recomputed after each analysis batch.
- No third-party DSP libraries are used. Essentia and TarsosDSP are rejected because their
  AGPL/GPL licenses conflict with keeping commercial options open. Plain Java FFT only.
- A later trained model plugs in as another `MoodScorer`. Bumping `scorerVersion` triggers
  background re-analysis.
- `MoodAnalysisWorker` (WorkManager, new dependency `androidx.work:work-runtime`) analyzes songs
  that are unanalyzed or out of date in batches. It prefers running while charging and idle; it
  also runs on library changes and on first launch after permission is granted.
- On-demand path: the recommender may synchronously request analysis of at most 5 top candidates,
  on the I/O executor and never on the main thread.
- Analysis decodes files independently and never touches the playback or Korokae pipeline.

### 2.2 Taste profile

- `PlaybackEventRecorder` lives in `SwaraPlaybackService`, alongside the existing history
  recording. It writes `listen_events`:
  - `PLAY` when an item starts;
  - `SKIP` when left before 30 s or before 40% of its duration, recording `positionMs`;
  - `FINISH` on automatic transition at the end of an item;
  - `REPEAT` when the same item restarts via repeat-one or a manual replay.
- `TasteProfile` computes affinities on demand from `listen_events` and favorites:
  `songAffinity`, `artistAffinity`, `albumAffinity` (finishes and repeats count up; early skips
  count down; favorites add a fixed bonus), and `hourOfDayAffinity` per song.
- The table is trimmed to the newest 5,000 rows.

### 2.3 Recommender

A deterministic scoring function plus seeded randomness. No neural network.

```
score = 0.50 * moodFit(candidate, targetMood)
      + 0.25 * taste(candidate)
      + 0.15 * transition(current, candidate)
      + 0.10 * freshness(candidate)
      + jitter (uniform, ±0.03, session-seeded)
```

- `moodFit` is 1 minus the Euclidean distance in (valence, energy), clamped to [0, 1].
- `transition` penalizes energy jumps and tempo jumps over 25 BPM. Half or double tempo counts
  as compatible.
- `freshness` is 0 if played within 2 h and rises to 1 at 7 days.
- Hard filters: not played within 2 h; at most 2 consecutive songs by the same canonical artist;
  not the current song.
- Songs without a mood score are scored on taste, transition and freshness only, with `moodFit`
  set to a neutral 0.5.

## 3. Features

### 3.1 Vibe Mode

Runs in `SwaraPlaybackService` (`VibeSession`), so it works with the UI closed.

- **Start.** `m0 = (v0, e0)` is the mood of the current song (analyzed on demand if needed).
- **Hold.** The first 2 picks target `m0` exactly.
- **Goal.** `vGoal = max(v0, 0.70)`; `eGoal` = `e0` clamped into [0.55, 0.65].
- **Ramp.** Over N = 8 picks after the hold, the target is `m0 + (goal − m0) * smoothstep(k / N)`.
  Valence rises by at most 0.08 per pick. After the ramp the target stays at the goal.
- **Skip handling.**
  - A skip within 30 s does not advance the ramp, and halves the next step size.
  - Two consecutive early skips move the target back one step toward `m0`.
  - The skipped song is excluded for the rest of the session.
- **Queue.** On enable, all items after the current one are removed and replaced by Vibe picks.
  The engine keeps 2 picks queued after the current item, refilling on each media item
  transition. Disabling Vibe Mode stops refilling and leaves queued picks in place.
- **Low pool.** With fewer than 15 analyzed eligible songs: relax the 2 h rule to 30 min, then the
  artist rule, then fall back to Autoplay picks. Never stop playback for lack of picks.
- **UI.** A Vibe toggle on Now Playing, highlighted with the accent color while active, and no
  status line. A "Start Vibe Mode" item in the shared song overflow menu (`SongMenuHelper`)
  plays that song and enables Vibe Mode.
- **Control.** A new session command `COMMAND_SET_VIBE` (enabled, optional start song ID). The
  state is mirrored in the session extras `vibe_active`, like Korokae.
- The Vibe session is saved with the existing `PlaybackStateStore`. After process death it
  resumes with the saved `m0` and ramp step.

### 3.2 Autoplay

- When the last queue item starts and Autoplay is on (Settings toggle, default on), append 2
  picks targeting the current song's mood, with no ramp. Keep refilling while the user keeps
  listening.
- Autoplay is inactive while Vibe Mode is on, since Vibe Mode already fills the queue.

### 3.3 "For you" on Home

Up to three mixes of about 25 songs each, rebuilt daily (WorkManager) or when the library
changes:

| Mix | Selection |
|---|---|
| For this time of day ("Morning", "Afternoon", "Evening", "Late night") | top `hourOfDayAffinity` for the current hour bucket |
| Feel-good | valence ≥ 0.65 and positive taste |
| Rediscover | high past affinity, not played for 30 days or more |

- A mix with fewer than 10 songs is hidden, which covers new users with little history.
- Tapping a mix plays it as a normal queue.
- Mixes are stored as ordered song-ID lists in the `mixes` table (section 4), so Home loads
  instantly.

## 4. Data model (Room v3 → v4, explicit `MIGRATION_3_4`)

```
song_mood(
  songId INTEGER PRIMARY KEY, valence REAL, energy REAL, tempoBpm REAL,
  rawFeatures BLOB, scorerVersion INTEGER, status INTEGER /* OK, FAILED */, analyzedAt INTEGER)

listen_events(
  id INTEGER PRIMARY KEY AUTOINCREMENT, songId INTEGER, type INTEGER,
  positionMs INTEGER, timestamp INTEGER, hourOfDay INTEGER)
  INDEX(songId), INDEX(timestamp)

mixes(
  mixKey TEXT PRIMARY KEY, title TEXT, songIds TEXT, builtAt INTEGER)
```

`play_history` is unchanged; Recently Played keeps working as today. `SongDeletionManager`
cascade cleanup also deletes the song's `song_mood` and `listen_events` rows.

## 5. Error handling

- A decode or analysis failure sets `status = FAILED` with the current `scorerVersion`, so the
  song is not retried until the scorer changes. The song is still recommendable via taste.
- Analysis takes at most 10 s per song; a longer one is cancelled and marked failed.
- Missing permission: the worker exits quietly and is re-enqueued on grant (hooked into the H1
  permission flow).
- Deleted or missing files: skipped during picking; their rows are removed on the next
  library-change prune.

## 6. Testing

- **Unit tests (JVM):**
  - DSP primitives on synthetic signals: a 120 BPM click track gives a tempo of about 120; a
    C-major triad gives major mode, and an A-minor triad gives minor.
  - Percentile normalization.
  - `VibeSession` ramp math: hold, smoothstep, the 0.08 cap, skip halving and the
    two-skip rollback.
  - Scoring weights, filters and the relaxation order for small pools.
  - Mix-building thresholds.
  - `TasteProfile` affinity rules.
- **Migration test:** a v3 database migrates to v4 with existing data intact (Room
  `MigrationTestHelper`, using the schema exported by the audit work).
- **On-device calibration:** a debug-only screen (debug builds only) lists songs sorted by
  valence and energy. Used with the developer's film-song library to tune the section 2.1 weights
  against success criterion 2.

## 7. Out of scope

- A trained mood model. The `MoodScorer` interface leaves room for it; it needs a dataset licensed
  for commercial use, to be decided with Korokae v2.
- Cloud sync of taste data.
- Wrapped, Jam and playlist sharing/import (separate sub-projects that will reuse `listen_events`).
