# Vibe Mode & On-Device Recommender Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On-device mood analysis, a taste profile and a scoring recommender that power Vibe Mode, Autoplay and "For you" mixes, with no shipped weights and no network.

**Architecture:** Pure-Java logic (DSP, mood normalization, taste, scoring, Vibe ramp, mix building) lives in `com.psthetech.swara.vibe` and is unit-tested on the JVM. Android glue (MediaCodec decoding, WorkManager workers, Room, the playback service, UI) wraps that logic. Vibe Mode and Autoplay run in `SwaraPlaybackService` and are controlled by session commands, like Korokae.

**Tech Stack:** Java 11, Room 2.6.1 (schema v4), Media3 1.4.1, WorkManager (`androidx.work:work-runtime`, new), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-17-vibe-recommender-design.md`

## Global Constraints

- No new network permission and no network calls.
- No third-party DSP or ML libraries (no Essentia or TarsosDSP); plain Java only.
- Java only, no Kotlin (project convention).
- Room access never on the main thread; use `SwaraApplication.getDbExecutor()` / `getIoExecutor()`.
- User-facing copy makes no health claims. Use "lifts your mood gently", never "treats" or "depression".
- Every new string goes in `res/values/strings.xml`.
- Recommender weights: moodFit 0.50, taste 0.25, transition 0.15, freshness 0.10, jitter ±0.03.
- Vibe constants: hold 2 picks; ramp N = 8; valence cap 0.08 per pick; vGoal = max(v0, 0.70); eGoal = clamp(e0, 0.55, 0.65); 2 picks queued ahead.
- Early skip: left before 30 000 ms or before 40% of the duration.
- Hard filters: not played within 2 h; at most 2 consecutive songs by the same canonical artist; not the current song.
- `listen_events` capped at the newest 5 000 rows. Analysis budget 10 s per song. At most 5 on-demand analyses per pick.

## Review Focus

1. **A library where every song scores the same raw value** (or only 1 song analyzed): percentile normalization must return 0.5, not NaN or division by zero. Pinned in Task 3.
2. **A very short, silent or mono file:** feature extraction must return a result marked FAILED, not throw or hang. Pinned in Tasks 2 and 4.
3. **Vibe Mode started on a song that is already above the goal** (v0 = 0.9): the target must stay at about v0 and never fall. Pinned in Task 7.
4. **The user deletes or skips every candidate in a tiny library** (fewer than 3 songs): the picker must relax the filters and still return a song, or return empty without crashing. Playback must not stop with an error. Pinned in Task 6.
5. **Process death mid-Vibe session:** on restore, Vibe resumes with the saved m0 and ramp step instead of restarting the hold. Pinned in Task 8.

---

## File Structure

```
app/src/main/java/com/psthetech/swara/
  vibe/                         pure logic, JVM-testable
    dsp/Fft.java                in-place radix-2 FFT
    dsp/AudioFeatures.java      value class: rms, onsetRate, tempoBpm, centroidHz, majorness
    dsp/FeatureExtractor.java   float[] mono 22050 Hz → AudioFeatures
    MoodResult.java             valence, energy, tempoBpm, raw features, scorerVersion, ok
    MoodScorer.java             interface
    RawMoodModel.java           AudioFeatures → raw valence/energy (weights live here)
    PercentileNormalizer.java   raw values → library percentiles
    TasteProfile.java           affinities from events + favorites
    Candidate.java              song id, artist key, mood (nullable), last played, affinity
    Recommender.java            scoring + filters + relaxation
    VibeSession.java            ramp state machine (serializable)
    MixBuilder.java             "For you" mix selection
    EventClassifier.java        PLAY/SKIP/FINISH/REPEAT rules
  vibe/android/
    PcmDecoder.java             MediaExtractor+MediaCodec → mono 22050 Hz float[] (30 s, middle)
    AudioFeatureScorer.java     MoodScorer implementation (PcmDecoder + FeatureExtractor + RawMoodModel)
    MoodRepository.java         analyze/store/normalize; on-demand analysis
    MoodAnalysisWorker.java     WorkManager batch analysis
    MixRefreshWorker.java       daily mix rebuild
    RecommendationService.java  builds Candidates from Room + MusicRepository; used by the playback service
  data/db/entity/SongMood.java, ListenEvent.java, Mix.java
  data/db/dao/MoodDao.java, ListenEventDao.java, MixDao.java
  ui/debug/MoodDebugFragment.java (debug only)
```

Modified: `AppDatabase.java` (v4 + `MIGRATION_3_4`), `SongDeletionManager.java`, `SwaraPlaybackService.java`, `PlaybackStateStore.java`, `PlaybackViewModel.java`, `NowPlayingFragment.java` + `fragment_now_playing.xml`, `SongMenuHelper.java` + `SongAdapter.Listener`, `HomeFragment.java` + `fragment_home.xml`, `SettingsFragment.java` + `fragment_settings.xml`, `LibraryPreferences.java`, `MainActivity.java`, `nav_graph.xml`, `gradle/libs.versions.toml`, `app/build.gradle`, `strings.xml`.

---

### Task 1: Room schema v4 (song_mood, listen_events, mixes)

**Files:**
- Create: `data/db/entity/SongMood.java`, `ListenEvent.java`, `Mix.java`; `data/db/dao/MoodDao.java`, `ListenEventDao.java`, `MixDao.java`
- Modify: `data/db/AppDatabase.java` (version 4, `MIGRATION_3_4`, the 3 DAOs); `data/repository/SongDeletionManager.java` (cascade)
- Test: `app/src/androidTest/java/com/psthetech/swara/MigrationV4Test.java`; add `androidTestImplementation "androidx.room:room-testing:2.6.1"` and `sourceSets { androidTest.assets.srcDirs += files("$projectDir/schemas") }`

**Interfaces:**
- Produces:
  - `SongMood { long songId; double valence, energy, tempoBpm; byte[] rawFeatures; int scorerVersion; int status /* SongMood.STATUS_OK=0, STATUS_FAILED=1 */; long analyzedAt; }`
  - `ListenEvent { long id; long songId; int type /* TYPE_PLAY=0, SKIP=1, FINISH=2, REPEAT=3 */; long positionMs; long timestamp; int hourOfDay; }`, with indices on songId and timestamp.
  - `Mix { String mixKey; String title; String songIds /* comma-separated */; long builtAt; }`
  - `MoodDao`: `upsert(SongMood)`, `List<SongMood> getAll()`, `SongMood get(long)`, `List<Long> idsNeedingAnalysis(List<Long> libraryIds, int version)` (missing or `scorerVersion < version`), `deleteForSong(long)`, `updateNormalized(long songId, double valence, double energy)`.
  - `ListenEventDao`: `insert(ListenEvent)`, `List<ListenEvent> since(long ts)`, `List<ListenEvent> all()`, `trimTo(int keep)`, `deleteForSong(long)`, `Long lastPlayed(long songId)`.
  - `MixDao`: `upsert(Mix)`, `LiveData<List<Mix>> allLive()`, `deleteAll()`.

- [ ] **Step 1: Write the failing migration test.** `MigrationV4Test.migrate3To4_keepsPlaylistsAndCreatesTables`: create v3 with `MigrationTestHelper`, insert a playlist and a favorite, run `MIGRATION_3_4`, then assert both rows are still present and `song_mood`, `listen_events` and `mixes` exist (insert one row into each).
- [ ] **Step 2: Run** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.psthetech.swara.MigrationV4Test`. Expected: FAIL (no `MIGRATION_3_4`). Needs the device on adb.
- [ ] **Step 3: Implement** the entities, DAOs, version 4 and `MIGRATION_3_4` (`CREATE TABLE` SQL matching the exported schema; build once and copy the SQL from `app/schemas/.../4.json`). Add `db.moodDao().deleteForSong(id)` and `db.listenEventDao().deleteForSong(id)` to `SongDeletionManager.performCascadeCleanup` on the DB executor.
- [ ] **Step 4: Run** the migration test again (PASS) and `./gradlew testDebugUnitTest` (all existing tests PASS). Commit `4.json`.
- [ ] **Step 5: Commit** `feat(db): schema v4 with song_mood, listen_events, mixes`.

### Task 2: DSP feature extraction (pure Java)

**Files:**
- Create: `vibe/dsp/Fft.java`, `vibe/dsp/AudioFeatures.java`, `vibe/dsp/FeatureExtractor.java`
- Test: `app/src/test/java/com/psthetech/swara/vibe/FeatureExtractorTest.java`

**Interfaces:**
- Produces:
  - `static void Fft.transform(double[] re, double[] im)`, where the length is a power of 2.
  - `AudioFeatures { double rms, onsetRate /*onsets per s*/, tempoBpm, centroidHz, majorness /*-1 minor … +1 major*/; }`
  - `static @Nullable AudioFeatures FeatureExtractor.extract(float[] mono, int sampleRate)`. Returns null when there are fewer than 5 s of samples or the RMS is below 1e-4 (silence).

Approach: frame 2048, hop 512, Hann window. Onset strength is half-wave-rectified spectral flux. Tempo is the onset-strength autocorrelation peak within 60–180 BPM. Centroid is the mean spectral centroid. Chroma uses 12 pitch classes, summed over frames. Majorness is the best Pearson correlation of chroma with the 12 rotated Krumhansl major profiles minus the best minor one. Profiles: major `{6.35,2.23,3.48,2.33,4.38,4.09,2.52,5.19,2.39,3.66,2.29,2.88}`, minor `{6.33,2.68,3.52,5.38,2.60,3.53,2.54,4.75,3.98,2.69,3.34,3.17}`.

- [ ] **Step 1: Write failing tests.**
  - `clickTrack120_tempoNear120`: a 20 s click every 0.5 s at 22050 Hz; `|tempo − 120| ≤ 3`.
  - `cMajorTriad_isMajor`: sines at C4, E4 and G4 for 10 s; `majorness > 0`.
  - `aMinorTriad_isMinor`: sines at A3, C4 and E4; `majorness < 0`.
  - `brighterSignal_hasHigherCentroid`: a 4 kHz sine's centroid is greater than a 200 Hz sine's.
  - `silence_returnsNull` (zeros for 10 s) and `tooShort_returnsNull` (3 s).
  - `fft_impulse_isFlat`: an impulse gives all-ones magnitude.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*FeatureExtractorTest'`. Expected: FAIL (classes missing).
- [ ] **Step 3: Implement** the three classes per the approach above.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(vibe): on-device audio feature extraction`.

### Task 3: Raw mood model and library normalization

**Files:**
- Create: `vibe/MoodResult.java`, `vibe/MoodScorer.java`, `vibe/RawMoodModel.java`, `vibe/PercentileNormalizer.java`
- Test: `app/src/test/java/com/psthetech/swara/vibe/MoodModelTest.java`

**Interfaces:**
- Consumes: `AudioFeatures` (Task 2).
- Produces:
  - `interface MoodScorer { int version(); MoodResult score(android.net.Uri song); }`
  - `MoodResult { boolean ok; double valence, energy, tempoBpm; byte[] raw; int scorerVersion; static MoodResult failed(int version); }`
  - `static double RawMoodModel.rawEnergy(AudioFeatures f)` and `static double rawValence(AudioFeatures f)`. Weights are `public static final` fields at the top of the class.
  - `static double[] PercentileNormalizer.normalize(double[] raw)`. Element i is the fraction of other values that are lower, with ties counted half. A single element or all-equal values give 0.5 for every element.
  - `static byte[] RawMoodModel.pack(double rawValence, double rawEnergy, double tempo)` and `double[] unpack(byte[])`, to store raw values in `rawFeatures`.

Initial weights. Energy is a z-scored combination of log(rms) 0.5, onsetRate 0.3 and tempo 0.2. Valence is majorness 0.5, log(centroid) 0.3 and tempo 0.2. Each input is clamped to typical ranges before weighting: rms 1e-3..0.5, onset 0..8, tempo 60..180, centroid 300..5000.

- [ ] **Step 1: Failing tests.**
  - `fasterLouder_isMoreEnergetic`.
  - `major_isHigherValenceThanMinor`, with other features equal.
  - `normalize_ranks`: [3,1,2] gives [1.0, 0.0, 0.5].
  - `normalize_allEqual_isHalf`.
  - `normalize_single_isHalf`.
  - `packUnpack_roundTrips`.
- [ ] **Step 2: Run** `--tests '*MoodModelTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(vibe): raw mood model and percentile normalization`.

### Task 4: Android mood analysis (decoder, scorer, repository, worker)

**Files:**
- Create: `vibe/android/PcmDecoder.java`, `AudioFeatureScorer.java`, `MoodRepository.java`, `MoodAnalysisWorker.java`
- Modify: `gradle/libs.versions.toml` (add `work = "2.9.1"` and `work-runtime = { group = "androidx.work", name = "work-runtime", version.ref = "work" }`), `app/build.gradle` (`implementation libs.work.runtime`), `SwaraApplication.java` (enqueue on start), `MainActivity.java` (enqueue after the permission grant, inside the H1 grant branch), `LibraryViewModel.java` (enqueue on a library change after `refreshAll`)
- Test: `app/src/androidTest/java/com/psthetech/swara/vibe/AudioFeatureScorerDeviceTest.java`

**Interfaces:**
- Consumes: `FeatureExtractor`, `RawMoodModel`, `PercentileNormalizer`, `MoodDao`.
- Produces:
  - `static @Nullable float[] PcmDecoder.decodeMiddle(Context, Uri, int seconds /*30*/, int targetRate /*22050*/)`. Downmixes to mono and decodes from `max(0, duration/2 − 15 s)`. Returns null on failure. Aborts after 10 s of wall time.
  - `AudioFeatureScorer implements MoodScorer`, with `version() == 1`.
  - `MoodRepository(Context)`:
    - `void analyzeBatch(List<Long> songIds)`
    - `@Nullable SongMood getOrAnalyze(long songId)`, blocking and I/O thread only
    - `void renormalize()`, which recomputes percentiles over the OK rows and writes them with `updateNormalized`
  - `static void MoodAnalysisWorker.enqueue(Context)`: unique work `"mood_analysis"`, `KEEP`, preferring charging and idle. Also `enqueueNow(Context)` (`REPLACE`, no constraints), used after the permission grant.

- [ ] **Step 1: Failing device test.** `scoresAGeneratedWav`: write a 20 s 120 BPM major-chord WAV to cache and score it via `Uri.fromFile`. Assert `ok`, `|tempoBpm − 120| ≤ 5`, and valence/energy in [0, 1] once normalized with one other entry.
- [ ] **Step 2: Run** `connectedDebugAndroidTest` for that class. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Missing permission (a `SecurityException` from the resolver) makes `doWork` return `Result.success()` without writing.
  - A song that fails to decode is stored as `SongMood.failed` with the current version.
  - Call `renormalize()` after each batch of 25.
  - The worker selects ids via `MusicRepository` songs and `idsNeedingAnalysis`.
- [ ] **Step 4: Run** the device test (PASS) and `testDebugUnitTest` (PASS).
- [ ] **Step 5: Commit** `feat(vibe): background mood analysis with WorkManager`.

### Task 5: Listen events and taste profile

**Files:**
- Create: `vibe/EventClassifier.java`, `vibe/TasteProfile.java`
- Modify: `service/SwaraPlaybackService.java`. In the `onPositionDiscontinuity` and `onIsPlayingChanged` handlers added by the audit work, insert `ListenEvent`s via `EventClassifier`, then `trimTo(5000)` on the DB executor. Write a PLAY event in `onMediaItemTransition`.
- Test: `app/src/test/java/com/psthetech/swara/vibe/TasteTest.java`

**Interfaces:**
- Produces:
  - `static int EventClassifier.classifyLeave(long positionMs, long durationMs, boolean autoTransition, boolean sameItemRestarted)`. Returns `TYPE_REPEAT` if the same item restarted, `TYPE_FINISH` on an auto transition, `TYPE_SKIP` if `positionMs < 30000 || positionMs < 0.4 * durationMs`, and otherwise `TYPE_FINISH`.
  - `TasteProfile(List<ListenEvent> events, Set<Long> favoriteIds, Map<Long,String> artistKeyBySong, Map<Long,Long> albumBySong)`, with methods `double song(long id)`, `double artist(String key)`, `double album(long albumId)`, `double hour(long id, int hourOfDay)` and `double combined(long id)`.
  - Song affinity is `tanh(0.4·finishes + 0.6·repeats − 0.5·skips + 1.0·favorite)`, mapped to [0, 1] via `(x+1)/2`. Artist and album affinity is the mean song affinity of that artist or album. `combined` is `0.6·song + 0.25·artist + 0.15·album`. `hour` is that song's share of PLAY events in ±1 h of `hourOfDay`.

- [ ] **Step 1: Failing tests.**
  - `classify_earlySkip`: 10 s of 200 s gives SKIP.
  - `classify_lateLeave_isFinish`: 150 s of 200 s gives FINISH.
  - `classify_shortSongUnder40pct_isSkip`: 35 s of 100 s gives SKIP.
  - `classify_repeat`.
  - `favoriteWithNoPlays_scoresAboveNeutral`.
  - `manySkips_scoresBelowNeutral`.
  - `unknownSong_isNeutralHalf`.
  - `hourAffinity_prefersMatchingHour`.
- [ ] **Step 2: Run** `--tests '*TasteTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the classes, then wire the service.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(vibe): listen events and taste profile`.

### Task 6: Recommender scoring and filters

**Files:**
- Create: `vibe/Candidate.java`, `vibe/Recommender.java`
- Test: `app/src/test/java/com/psthetech/swara/vibe/RecommenderTest.java`

**Interfaces:**
- Consumes: `TasteProfile.combined`.
- Produces:
  - `Candidate { long songId; String artistKey; @Nullable double[] mood /*{valence, energy}*/; double tempoBpm /*0 if unknown*/; long lastPlayedMs /*0 if never*/; double taste; }`
  - `Recommender(java.util.Random rng)`
  - `List<Candidate> pick(List<Candidate> pool, Candidate current, double[] targetMood, List<String> recentArtistKeys, Set<Long> excluded, long nowMs, int count)`
  - `static double moodFit(@Nullable double[] mood, double[] target)`: `1 − euclidean`, clamped to [0, 1]. Unknown mood gives 0.5.
  - `static double transition(Candidate a, Candidate b)`: `1 − |Δenergy|`, minus 0.5 if the tempo difference is over 25 BPM and not about half or double (±8%). Clamped to [0, 1]. An unknown tempo or mood gives 0.5.
  - `static double freshness(long lastPlayedMs, long nowMs)`: 0 within 2 h, linear to 1 at 7 days, and 1 if never played.
  - The score uses the weights in Global Constraints, plus `rng` jitter of ±0.03.
  - Relaxation when fewer than `count` candidates survive: first allow songs played within 30 min (instead of 2 h), then drop the artist rule, then allow any non-excluded song. Return fewer than `count` (possibly empty) only if the pool is exhausted.

- [ ] **Step 1: Failing tests.**
  - `prefersCloserMood`.
  - `excludesPlayedWithin2h`.
  - `noThirdConsecutiveSameArtist`.
  - `excludesCurrentAndExcluded`.
  - `halfTempo_isCompatible`.
  - `tinyLibrary_relaxesAndStillReturns`: a pool of 2, both played 1 h ago, returns 1 song.
  - `emptyPool_returnsEmpty`.
  - `seededRng_isDeterministic`.
- [ ] **Step 2: Run** `--tests '*RecommenderTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(vibe): recommender scoring with filter relaxation`.

### Task 7: VibeSession ramp state machine

**Files:**
- Create: `vibe/VibeSession.java`
- Test: `app/src/test/java/com/psthetech/swara/vibe/VibeSessionTest.java`

**Interfaces:**
- Produces:
  - `VibeSession(double v0, double e0)`
  - `double[] nextTarget()`: the target for the next pick, which also advances the state.
  - `void onEarlySkip()`
  - `void onPickAccepted()`: resets the consecutive-skip count.
  - `String serialize()` and `static VibeSession deserialize(String)`
  - `double v0()`, `double e0()`, `int step()`
  - Rules from Global Constraints. Picks 0–1 return m0. Then the target is `m0 + (goal − m0)·smoothstep(k/8)`, where `smoothstep(x) = x²(3 − 2x)`, with the valence rise per pick capped at 0.08 (the cap carries over, so reaching the goal may take more than 8 picks). After the ramp the target holds at the goal.
  - `onEarlySkip`: the next `nextTarget()` does not advance `k` and uses half the step. Two consecutive early skips decrement `k` by 1 (minimum 0).

- [ ] **Step 1: Failing tests.**
  - `holdsStartMoodForTwoPicks`.
  - `valenceRisesMonotonicallyToGoal`: v0 = 0.1, e0 = 0.2, 20 picks. Non-decreasing, each step ≤ 0.08 + 1e-9, and the final value is 0.70 ± 1e-6.
  - `energyHeadsIntoBand`: e0 = 0.9 ends at 0.65 ± 1e-6, and e0 = 0.2 ends at 0.55.
  - `aboveGoal_neverFalls`: v0 = 0.9 keeps every target valence ≥ 0.9 − 1e-9.
  - `earlySkip_doesNotAdvanceAndHalvesStep`.
  - `twoSkips_stepBack`.
  - `serializeRoundTrip_preservesStep`.
- [ ] **Step 2: Run** `--tests '*VibeSessionTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(vibe): Vibe Mode mood ramp`.

### Task 8: Vibe Mode and Autoplay in the playback service

**Files:**
- Create: `vibe/android/RecommendationService.java`, `vibe/VibeQueuePolicy.java`
- Modify:
  - `service/SwaraPlaybackService.java`: add `COMMAND_SET_VIBE`, `EXTRA_VIBE_ENABLED`, `EXTRA_VIBE_START_SONG_ID`, the session extra `"vibe_active"`, and the queue-refill logic.
  - `service/PlaybackStateStore.java`: add `vibeState` (nullable String) and `autoplay`.
  - `data/preference/LibraryPreferences.java`: add `autoplayEnabled`, default true.
  - `ui/viewmodel/PlaybackViewModel.java`: add `LiveData<Boolean> getVibeActive()`, `void setVibeMode(boolean)` and `void startVibeFrom(Song)`, mirrored from the session extras.
- Test: `app/src/test/java/com/psthetech/swara/service/PlaybackStateStoreTest.java` (extend); `app/src/test/java/com/psthetech/swara/vibe/VibeQueuePolicyTest.java`

**Interfaces:**
- Consumes: `Recommender`, `VibeSession`, `TasteProfile`, `MoodRepository.getOrAnalyze`, `MediaItemFactory.toMediaItem`.
- Produces:
  - `RecommendationService(Context)`: `List<Song> nextPicks(Song current, double[] target, Set<Long> excluded, int count)`, blocking and I/O thread only. It builds the pool from `MusicRepository`'s cached songs, analyzes up to 5 unanalyzed top-taste candidates on demand, and calls `Recommender.pick`.
  - `static int VibeQueuePolicy.picksNeeded(int itemCount, int currentIndex, int ahead /*2*/)`: returns `max(0, ahead − (itemCount − 1 − currentIndex))`.
- Behavior:
  - **Enable:** remove the items after the current one, create the `VibeSession` from the current song's mood (on demand; neutral 0.5/0.5 if it fails), and fill 2 picks.
  - **On each `onMediaItemTransition`:** refill to 2 ahead. An early skip (`EventClassifier` SKIP from Task 5) on a Vibe pick calls `onEarlySkip()` and adds the song to the excluded set.
  - **Autoplay:** when the Vibe session is off, the autoplay preference is on, and `currentIndex == itemCount − 1`, append 2 picks with target = the current mood.
  - **Threads:** picking runs on the I/O executor, and the result is posted back to `mainHandler` before calling `player.addMediaItems`.
  - **Persistence:** `saveState()` stores `vibeSession.serialize()`, and `restoreState()` restores it.

- [ ] **Step 1: Failing tests.**
  - `picksNeeded_fillsToTwoAhead`: (5, 4) gives 2, (5, 3) gives 1, and (5, 1) gives 0.
  - `stateStore_roundTripsVibeState`: extend the store's encoding with a package-private `static String encodeVibe(@Nullable String)` and `decodeVibe`, and test null and non-null.
- [ ] **Step 2: Run** `--tests '*VibeQueuePolicyTest' --tests '*PlaybackStateStoreTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the policy, the store fields, `RecommendationService`, the service wiring and the ViewModel mirroring.
- [ ] **Step 4: Run** `testDebugUnitTest` (PASS). On the device: start Vibe Mode on a sad song, let 3 songs auto-advance, and confirm the queue keeps exactly 2 items ahead. Kill the process (`adb shell am kill com.psthetech.swara`), reopen, and confirm Vibe is still active.
- [ ] **Step 5: Commit** `feat(vibe): Vibe Mode and Autoplay in playback service`.

### Task 9: Vibe and Autoplay UI

**Files:**
- Modify:
  - `res/layout/fragment_now_playing.xml`: add `btnVibe`, an ImageView next to `btnKorokae` (around line 294), with the new drawable `ic_vibe.xml`.
  - `ui/nowplaying/NowPlayingFragment.java`: toggle via `playbackViewModel.setVibeMode`; tint with the accent color when `getVibeActive()` is true, otherwise the same inactive tint used for `btnSleepTimer`. No status text.
  - `ui/adapter/SongMenuHelper.java`: add menu item ID 10 "Start Vibe Mode", calling `listener.onStartVibe(song)`.
  - `ui/adapter/SongAdapter.java`: add `default void onStartVibe(Song song) {}` to `Listener`.
  - Each fragment implementing `SongAdapter.Listener` (Songs, Favorites, Playlist detail, Album/Artist detail, Search): implement `onStartVibe` as `playbackViewModel.startVibeFrom(song)`.
  - `ui/settings/SettingsFragment.java` + `fragment_settings.xml`: add the switch `switchAutoplay` to the Library card.
  - `strings.xml`: `vibe_mode` "Vibe", `start_vibe_mode` "Start Vibe Mode", `cd_vibe_mode` "Vibe Mode: lifts your mood gently", `settings_autoplay` "Keep playing similar songs when the queue ends".
- Test: manual on the device (the UI glue has no JVM-testable logic).

- [ ] **Step 1: Implement** the layout, drawable, strings, toggle wiring, menu item, listener implementations and settings switch.
- [ ] **Step 2: Run** `./gradlew assembleDebug testDebugUnitTest` (PASS), then `adb install -r --user 0 app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 3: Verify on the device.**
  - The Vibe button lights up and turns off.
  - "Start Vibe Mode" from a search result plays that song with the Vibe button lit.
  - Turning off the Autoplay switch stops songs being appended at the end of the queue.
- [ ] **Step 4: Commit** `feat(vibe): Vibe toggle, menu entry and Autoplay setting`.

### Task 10: "For you" mixes on Home

**Files:**
- Create: `vibe/MixBuilder.java`, `vibe/android/MixRefreshWorker.java`, `ui/adapter/MixAdapter.java`, `res/layout/item_mix.xml`
- Modify:
  - `res/layout/fragment_home.xml`: add `sectionForYou` with `tvHeaderForYou` and `rvForYou` above `sectionRecentlyPlayed` (line 78).
  - `ui/home/HomeFragment.java`: observe `MixDao.allLive()`. Hide the section when there are no mixes. Tapping a mix resolves its ids via `MusicRepository.getCanonicalSong`, skips missing ones and calls `playbackViewModel.playSongs`.
  - `SwaraApplication.java`: enqueue a daily periodic `MixRefreshWorker` with unique name `"mix_refresh"`.
  - `strings.xml`: `for_you` "For you", `mix_morning` "Morning", `mix_afternoon` "Afternoon", `mix_evening` "Evening", `mix_late_night` "Late night", `mix_feel_good` "Feel-good", `mix_rediscover` "Rediscover".
- Test: `app/src/test/java/com/psthetech/swara/vibe/MixBuilderTest.java`

**Interfaces:**
- Consumes: `TasteProfile`, `Candidate`, `ListenEvent`.
- Produces:
  - `static int MixBuilder.hourBucket(int hour)`: 5–11 → 0 (morning), 12–16 → 1, 17–21 → 2, and otherwise 3.
  - `List<Long> MixBuilder.timeOfDay(List<Candidate> pool, TasteProfile t, int hour, int size /*25*/)`
  - `List<Long> feelGood(List<Candidate> pool, TasteProfile t, int size)`: valence ≥ 0.65 and `t.combined > 0.5`, ordered by combined taste.
  - `List<Long> rediscover(List<Candidate> pool, TasteProfile t, long nowMs, int size)`: `lastPlayed ≥ 30 days ago` and `t.song > 0.6`.
  - `static final int MIN_MIX_SIZE = 10`. The worker stores only mixes with at least 10 songs and deletes the others.

- [ ] **Step 1: Failing tests.**
  - `hourBuckets`.
  - `feelGood_filtersByValenceAndTaste`.
  - `rediscover_requires30DaysAndHighAffinity`.
  - `timeOfDay_ordersByHourAffinity`.
  - `mixesCappedAtSize`.
- [ ] **Step 2: Run** `--tests '*MixBuilderTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the builder, worker, adapter and Home section.
- [ ] **Step 4: Run** `testDebugUnitTest` (PASS). On the device, force a refresh with `adb shell cmd jobscheduler run -f com.psthetech.swara <jobId>`, or trigger the worker from the debug screen in Task 11. Check that mixes appear on Home and play.
- [ ] **Step 5: Commit** `feat(vibe): For you mixes on Home`.

### Task 11: Mood debug screen and calibration

**Files:**
- Create: `app/src/debug/java/com/psthetech/swara/ui/debug/MoodDebugFragment.java` and `app/src/debug/res/layout/fragment_mood_debug.xml` (debug source set only)
- Modify: `app/src/debug/AndroidManifest.xml` (create it if missing); add a debug-only entry point, a long-press on `tvAppName` in `HomeFragment` that is gated by `BuildConfig.DEBUG`. This needs `buildFeatures { buildConfig true }` in `app/build.gradle` if it isn't already on.
- Test: manual calibration (spec success criterion 2).

The screen lists every analyzed song: title, artist, valence, energy, tempo and status. It sorts by valence or energy and has buttons for "Analyze now" (`MoodAnalysisWorker.enqueueNow`) and "Rebuild mixes".

- [ ] **Step 1: Implement** the screen.
- [ ] **Step 2: Install on the device, run analysis, and sort by valence.** Check that clearly sad songs sit in the bottom third and clearly upbeat songs in the top third.
- [ ] **Step 3: If the check fails,** adjust only the `RawMoodModel` weight constants, rerun `--tests '*MoodModelTest'` (it must still PASS), bump `AudioFeatureScorer.version()` so the library re-analyzes, and repeat Step 2.
- [ ] **Step 4: Commit** `chore(vibe): mood debug screen and calibrated weights`.

### Task 12: Release hygiene

**Files:**
- Delete: `app/src/main/assets/models/vibe_v1.bin` (recovered, unused, untracked).
- Modify: `app/src/main/assets/models/MODEL_LICENSE.md`. Keep it, since it documents the Korokae weights.
- Modify: `README.md`. Add Vibe Mode, Autoplay and "For you" to Features, using health-claim-free copy.

- [ ] **Step 1:** Delete `vibe_v1.bin`, add `MODEL_LICENSE.md` to git, and update the README.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease`. Expected: tests PASS and the release APK builds (R8 OK).
- [ ] **Step 3: Check** that `grep -rniE "depress|treat|therap" app/src/main/res` finds nothing.
- [ ] **Step 4: Commit** `docs: Vibe Mode in README; ship model license; drop unused vibe_v1 asset`.
