# Vibe Mood Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the device-reported problems with Vibe Mode:
- dance songs scored as sad;
- a romantic start leading to sad picks;
- a Vibe button the user can't see change;
- no "For you" section for new listeners.

The fix also adds per-song mood correction.

**Architecture:**
- Re-tune the existing pure-Java mood model against labelled songs from the user's own library, and fix the tempo octave errors.
- Add a `mood_override` table (Room v5) that takes precedence over the analyzed mood everywhere it is read.
- Small UI changes on Now Playing and in MixBuilder.

**Tech Stack:** Java 11, Room 2.6.1, Media3 1.4.1, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-17-vibe-recommender-design.md`. The device findings and decisions are in the conversation:
- The model analysis ruled out YAMNet for now.
- The user approved fixes 1–4 on 2026-10-17.

## Global Constraints
- No new libraries and no network calls. Java only.
- No Room access on the main thread.
- User-facing copy makes no health claims.
- Every new string goes in `res/values/strings.xml`.
- `AudioFeatureScorer.VERSION` goes from 2 to 3 in Task 1, so the library re-analyzes.
- Mood override labels and their (valence, energy) values: `SAD (0.15, 0.25)`, `CALM (0.50, 0.25)`, `HAPPY (0.80, 0.60)`, `ENERGETIC (0.85, 0.90)`.
- Calibration data (local, git-ignored; never commit audio):
  - Harness: `/tmp/claude-1000/-mnt-D-SwaraApp/9884bf48-44e8-472c-a474-bfdcbc26c7a4/scratchpad/harness/` (`Probe.java`, `labels.tsv`, `feat.tsv`).
  - Decoded clips: `…/scratchpad/audio/*.f32`. These are 30 s, mono, 22 050 Hz, little-endian float32 from the middle of each song.
  - Labels: 15 D (dance/energetic), 7 P (pleasant romantic), 8 S (sad).

## Review Focus
1. **The library has no OK moods yet** (re-analysis at v3 in progress). Overrides still apply, and nothing crashes.
2. **A song with an override is later deleted outside the app.** The override is pruned along with the song.
3. **Override set while Vibe is running on that song.** The next pick uses the new mood. The session's m0 changes only if the overridden song is the current start song and no pick has landed yet.
4. **Tempo octave fix on a genuinely slow song** (sparse onsets). It must not double the tempo.

---

### Task 1: Re-tune the mood model

**Files:**
- Modify: `vibe/RawMoodModel.java`, `vibe/dsp/FeatureExtractor.java` (tempo octave correction only), `vibe/android/AudioFeatureScorer.java` (VERSION 3)
- Test: `app/src/test/java/com/psthetech/swara/vibe/MoodModelTest.java`, `FeatureExtractorTest.java`

**Requirements:**
- **Energy** is driven by onset rate and loudness. Tempo is removed from energy.
- **Valence** puts dance/energetic songs above pleasant songs, and pleasant songs above sad ones. Energy-like cues (onset rate, loudness) feed valence because a danceable song should count as happy. Keep majorness and tempo only if they help on the labels. Drop centroid unless it helps.
- **Tempo octave correction:** when the chosen tempo is below 90 BPM and the onset rate shows a denser pulse, consider doubling it. When it is above 160 BPM with a sparse onset rate, consider halving it. Choose the rule from the harness data, and pin the slow-song case with a test (Review Focus 4).
- **Calibration:**
  - Use the harness. Recompile `Probe.java` against the rebuilt classes at `app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes`.
  - Report AUCs on the 30 labels for energy (D vs rest) and for valence (D vs rest, and P vs S).
  - Targets: energy D-vs-rest ≥ 0.85; valence D-vs-rest ≥ 0.85; valence P-vs-S as high as honestly reachable.
  - Avoid overfitting. Use at most 4 weights, round them to one decimal, and report a leave-one-out or held-out check.
- Weights stay `public static final` constants. Update the existing tests whose expectations change (monotonicity tests must still hold), and add tests for the new behavior.
- Commit: `fix(vibe): re-tune mood model; dance reads as happy, tempo octave fix`.

### Task 2: Mood correction ("This feels…")

**Files:**
- Create: `data/db/entity/MoodOverride.java`, `data/db/dao/MoodOverrideDao.java`, `vibe/MoodLabel.java` (enum with the Global Constraint values)
- Modify:
  - `data/db/AppDatabase.java`: version 5, `MIGRATION_4_5`, export `5.json`.
  - `vibe/android/RecommendationService.java` (pool moods and `moodOf`) and `service/VibeController.java` (m0): an override wins over the analyzed mood.
  - `vibe/android/MoodAnalysisWorker.java`: prune overrides for deleted songs.
  - `data/repository/SongDeletionManager.java`: cascade the delete to overrides.
  - `ui/adapter/SongMenuHelper.java` + `SongAdapter.Listener`: a "This song feels…" item opens a single-choice dialog (Sad / Calm / Happy / Energetic / Clear) and calls `listener.onSetMood(song)`. All implementors use a shared helper that shows the dialog and writes on the DB executor.
  - The debug screen shows the override label.
- Test:
  - JVM tests for `MoodLabel` values.
  - A test for override precedence in a pure helper, e.g. `MoodResolver.resolve(@Nullable double[] analyzed, @Nullable MoodLabel override)`.
  - A device test for `MigrationV5Test`, run with the adb `--user 0` procedure.
- **Behavior:** see Review Focus 3. The override applies to subsequent picks. The analysis keeps storing its own values; the override is a separate layer.
- **Strings:** `set_mood` "This song feels…", `mood_sad` "Sad", `mood_calm` "Calm", `mood_happy` "Happy", `mood_energetic` "Energetic", `mood_clear` "Clear", `mood_saved` "Saved".
- Commit: `feat(vibe): let users correct a song's mood`.

### Task 3: Visible Vibe control and "For you" for new listeners

**Files:**
- Modify: `res/layout/fragment_now_playing.xml`, `ui/nowplaying/NowPlayingFragment.java`, `res/drawable/ic_vibe.xml`, `vibe/MixBuilder.java`, `vibe/android/MixRefreshWorker.java` (if needed), strings
- Test: `MixBuilderTest.java`

**Requirements:**
- **Vibe pill:** replace `btnVibe` with a pill styled like `btnKorokae`: an icon plus a "Vibe" label, inside the same bottom row.
  - When on, the pill background fills with the accent color and the text and icon use contrasting colors. When off, it uses the same look as the Karaoke pill when that is off.
  - Make the row fit at 360 dp width; shrink spacing if needed.
  - Redraw `ic_vibe` as a clearly different shape from signal bars, e.g. a rising sun or sparkle.
  - On tap, show a Toast: "Vibe Mode on" / "Vibe Mode off".
  - Keep the content description.
- **Feel-good for new listeners:** `MixBuilder.feelGood` includes songs whose valence is at least 0.65 (an override counts) and whose taste is at least 0.5 (neutral allowed), ranked by taste then valence. Songs with taste below 0.5 stay excluded. Update the test and add a test that a library with no history yields a Feel-good mix.
- **Verify on the device** (the phone is unlocked): install, open Now Playing, take screenshots of the pill on and off, and check that Home shows "For you" once the Feel-good mix is built (trigger `MixRefreshWorker.refreshNow` via the debug screen or a temporary test).
- Commit: `fix(vibe): visible Vibe pill; Feel-good mix for new listeners`.
