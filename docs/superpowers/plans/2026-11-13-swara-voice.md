# Swara Voice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Offline voice control: "hey Swara, play Kaavaalaa / play Rahman songs / play Roja movie songs" finds the song, artist or album in the local library, plays it, and replies out loud.

**Architecture:** A new `com.psthetech.swara.voice` package. A pure-Java core (phonetic matching, intent rules, executor) is built and tested first. The audio front end comes next (mic → our wake-word ONNX → energy VAD → Moonshine tiny ONNX), both running on the existing ONNX Runtime. Replies go through Android TTS behind a `SwaraVoice` interface. Voice queues are guarded in `SwaraPlaybackService` so Vibe and Autoplay stay off until the user plays something else. The wake-word model is trained in a new `ml/wakeword/` Python project.

**Tech Stack:** Java 11, Media3 session custom commands, `onnxruntime-android:1.31.1` (already a dependency), Android `AudioRecord`/`AcousticEchoCanceler`/`TextToSpeech`, JUnit 4 + Mockito 4; Python 3 + PyTorch + onnx + pytest for `ml/wakeword/`, and Kokoro-82M (training machine only).

**Spec:** `docs/superpowers/specs/2026-11-13-swara-voice-design.md`

## Global Constraints

- Fully offline: add **no** network permission. Audio and transcripts are never written to disk or logged in release builds (`BuildConfig.DEBUG`-guarded logs only).
- Only new permission: `android.permission.RECORD_AUDIO`. No foreground-service type change; the mic runs only while `MainActivity` is resumed.
- Shipped code and models: Apache-2.0, MIT, or ours. **No sherpa-onnx, no espeak-ng, no GPL** in the APK. Kokoro is used only on the training machine.
- Java only (no Kotlin). Package `com.psthetech.swara.voice`, UI in `com.psthetech.swara.ui.voice`.
- Audio format everywhere: 16 kHz mono `float[]` in [-1, 1]; mic hop = 1280 samples (80 ms); wake window = 16000 samples (1.0 s).
- Command recording: ends after 0.8 s of silence, max 6 s. Ducked player volume = 0.15, restored after the reply or after 8 s at most.
- Match bands: HIGH ≥ 0.85 with a lead ≥ 0.10; AMBIGUOUS ≥ 0.85 with a lead < 0.10; MEDIUM 0.60–0.85; NONE < 0.60. The intent's category gets a +0.10 bonus.
- Size budget: ≤ 45 MB added. Wake gate: ≥ 95% detection, ≤ 1 false trigger per 10 h.
- Reply copy is fixed by the spec §2 "Replies" table. Use it verbatim (see `VoiceReplies`, Task 5).

## Review Focus

1. **Music playing through the phone speaker during a command:** the player must be ducked before recording starts and restored on *every* exit path (success, NONE, timeout, exception, activity paused mid-session). Test in Task 8 (`unduckOnPauseMidSession`, `unduckOnSttException`).
2. **Library still loading or empty** (`getCachedLibrarySongs()` returns null/empty): reply "Your library is still loading…", no crash. Test in Task 5 (`emptyLibraryRepliesLoading`).
3. **Artist name that is also a song title, or one-word queries** like "play rahman": `PLAY_ARTIST` must win over a weak song match, while "play kaavaalaa" must still pick the song. Test in Task 3 (`playRahmanPicksArtistNotSongContainingRahman`).
4. **The user plays something manually after a voice queue:** the guard must clear so Autoplay/Vibe come back; reordering or removing items inside the voice queue must *not* clear it. Test in Task 4 (`VoiceQueueGuardTest`).
5. **Repeated wake triggers** (the user says "hey swara" twice, or music echoes it): no second session while one is active, and a 2 s refractory period. Test in Task 12 (`WakeGateTest.refractoryBlocksSecondTrigger`) and Task 8 (`wakeIgnoredWhileBusy`).

---

### Task 1: PhoneticKey and TagNoise

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/voice/match/PhoneticKey.java`
- Create: `app/src/main/java/com/psthetech/swara/util/TagNoise.java`
- Modify: `app/src/main/java/com/psthetech/swara/util/ArtistIdentityHelper.java` (use `TagNoise.JUNK_TAGS_PATTERN` instead of its private copy)
- Test: `app/src/test/java/com/psthetech/swara/voice/PhoneticKeyTest.java`, `app/src/test/java/com/psthetech/swara/voice/TagNoiseTest.java`

**Interfaces:**
- Produces: `PhoneticKey.of(String) -> String` (never null; "" for null/blank). `TagNoise.JUNK_TAGS_PATTERN` (moved verbatim from `ArtistIdentityHelper`), `TagNoise.stripAlbumNoise(String) -> String` (lowercase, normalized via `KorokaeMatcher.normalizeString`, noise removed, trimmed).

- [ ] **Step 1: Write failing tests**

```java
// PhoneticKeyTest
assertEquals(PhoneticKey.of("Kaavaalaa"), PhoneticKey.of("kavalaa"));
assertEquals("kavala", PhoneticKey.of("Kaavaalaa"));
assertEquals(PhoneticKey.of("Rahman"), PhoneticKey.of("Rehman"));  // e/a vowel fold: see rule 3
assertEquals(PhoneticKey.of("Thalapathy"), PhoneticKey.of("Talapati"));
assertEquals(PhoneticKey.of("Azhagu"), PhoneticKey.of("Alagu"));
assertEquals(PhoneticKey.of("Phir"), PhoneticKey.of("fir"));
assertEquals(PhoneticKey.of("Wada"), PhoneticKey.of("vada"));
assertEquals("", PhoneticKey.of(null));
assertEquals("", PhoneticKey.of("  !! "));
// TagNoiseTest
assertEquals("roja", TagNoise.stripAlbumNoise("Roja (Original Motion Picture Soundtrack)"));
assertEquals("roja", TagNoise.stripAlbumNoise("Roja - MassTamilan.com"));
assertEquals("roja", TagNoise.stripAlbumNoise("Roja OST"));
assertEquals("roja", TagNoise.stripAlbumNoise("Roja (From \"Roja\")"));
assertEquals("scene", TagNoise.stripAlbumNoise("Scene"));
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "com.psthetech.swara.voice.*"`. Expected: FAIL (classes missing).

- [ ] **Step 3: Implement.** `PhoneticKey.of` rules, applied in this order to the lowercase ASCII-folded string (`Normalizer` NFD, drop combining marks):
  1. Keep `[a-z ]` only and collapse spaces. Words are keyed separately and joined with a single space.
  2. Digraphs: `zh→l`, `th→t`, `dh→d`, `ph→f`, `sh→s`, `ch→c`, `kh→k`, `gh→g`, `bh→b`; then `w→v`, `y` stays.
  3. Vowels: `aa→a`, `ee→i`, `ii→i`, `oo→u`, `uu→u`, `ai→e`; then a medial `e` between consonants becomes `a` (folds Rehman/Rahman).
  4. Drop an `h` that follows a consonant; collapse doubled letters; drop a trailing `h`.
  Keep `ArtistIdentityHelper` behaviour identical (its existing tests must stay green).

- [ ] **Step 4: Run** the voice tests plus `ArtistDeduplicationTest` and `ArtistDetailResolutionTest`. Expected: PASS.

- [ ] **Step 5: Commit** `feat(voice): phonetic keys and shared tag-noise stripping`.

---

### Task 2: IntentParser

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/voice/intent/VoiceIntent.java`, `.../voice/intent/IntentParser.java`
- Test: `app/src/test/java/com/psthetech/swara/voice/IntentParserTest.java`

**Interfaces:**
- Produces: `enum VoiceIntent.Type { PLAY_ANY, PLAY_ARTIST, PLAY_ALBUM, PAUSE, RESUME, NEXT, PREVIOUS, UNKNOWN }`. Immutable `VoiceIntent(Type type, String query)` where `query` is lowercase with cue words removed ("" for transport/UNKNOWN), plus `boolean artistThenAlbum` (true for "play X songs" without an album cue). `IntentParser.parse(String transcript) -> VoiceIntent`.

- [ ] **Step 1: Write failing tests** as a table test (`@Test public void commandTable()`), one assertion per spec row:

| transcript | type | query | artistThenAlbum |
|---|---|---|---|
| "Hey Swara, play Kaavaalaa." | PLAY_ANY | "kaavaalaa" | false |
| "play rahman songs" | PLAY_ARTIST | "rahman" | true |
| "songs by A R Rahman" | PLAY_ARTIST | "a r rahman" | false |
| "can you please play roja movie songs" | PLAY_ALBUM | "roja" | false |
| "play roja album" / "roja padam songs" / "play roja film songs" | PLAY_ALBUM | "roja" | false |
| "pause" / "stop" | PAUSE | "" | false |
| "resume" / "play" / "continue" | RESUME | "" | false |
| "next" / "skip" / "next song" | NEXT | "" | false |
| "previous" / "go back" | PREVIOUS | "" | false |
| "" / "um" | UNKNOWN | "" | false |

  Also add `play rahman` → `PLAY_ANY`, "rahman" (the matcher decides; see Task 3).

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*IntentParserTest"`. Expected: FAIL.

- [ ] **Step 3: Implement `IntentParser.parse`.** Lowercase, strip punctuation, remove the leading wake phrase (`hey|hi|ok`) + `swara|sara|swaraa`, and fillers (`can you`, `could you`, `please`, `the`, `some`, `me`). Then apply regex rules in order: transport words (whole-utterance match) → `songs by (.+)` → `(.+) (movie|film|padam|album)( songs)?` → `play (.+) songs` → `play (.+)`.

- [ ] **Step 4: Run** the test again. Expected: PASS.

- [ ] **Step 5: Commit** `feat(voice): rule-based command intent parser`.

---

### Task 3: LibraryVoiceMatcher

**Files:**
- Create: `.../voice/match/MatchKind.java` (enum SONG, ARTIST, ALBUM), `.../voice/match/Candidate.java`, `.../voice/match/Confidence.java` (enum HIGH, MEDIUM, AMBIGUOUS, NONE), `.../voice/match/MatchResult.java`, `.../voice/match/LibraryIndex.java`, `.../voice/match/LibraryVoiceMatcher.java`
- Test: `app/src/test/java/com/psthetech/swara/voice/LibraryVoiceMatcherTest.java`, fixture `app/src/test/java/com/psthetech/swara/voice/VoiceFixtures.java`

**Interfaces:**
- Consumes: `PhoneticKey.of`, `TagNoise.stripAlbumNoise` (Task 1); `VoiceIntent` (Task 2); `ArtistIdentityHelper.extractArtists/getCanonicalKey/normalizeDisplayName`.
- Produces:
  - `Candidate { MatchKind kind; String key; String display; String detail; double score; List<Song> songs; }`. `key` = song id string / artist canonical key / album id string. `detail` = disambiguating text (album: `"<most frequent display artist among its songs> · <year>"`; artist: `"<n> songs"`). `songs` is filled for ARTIST and ALBUM.
  - `MatchResult { Confidence confidence; List<Candidate> ranked; }` (at most 4).
  - `LibraryIndex.build(List<Song>) -> LibraryIndex`, which precomputes keys once per library.
  - `LibraryVoiceMatcher.match(VoiceIntent, LibraryIndex) -> MatchResult`.

- [ ] **Step 1: Write the fixture.** `VoiceFixtures.library()` returns about 60 `Song`s including:
  - Songs credited "A.R. Rahman", "A. R. Rahman", "AR Rahman", "A.R. Rahman, Shreya Ghoshal" (12 in total), plus "Ameen Rahman" (2).
  - Song "Kaavaalaa" (Anirudh, album "Jailer").
  - Album "Roja (Original Motion Picture Soundtrack)", Tamil 1992, 6 tracks with trackNumbers 1..6 given out of order; album "Roja - MassTamilan.com", Hindi, different albumId, year 1993.
  - Album "Scene", 4 tracks.
  - A song titled "Rahman Theme" by someone else.

- [ ] **Step 2: Write failing tests**

```java
@Test public void kaavaalaaFoundFromMisspelling()     // PLAY_ANY "kavalaa" → top SONG "Kaavaalaa", HIGH
@Test public void rahmanSongsGathersAllCreditVariants() // PLAY_ARTIST "rahman" → top ARTIST, songs.size()==12, Ameen not included
@Test public void playRahmanPicksArtistNotSongContainingRahman() // PLAY_ANY "rahman" → top ARTIST A.R. Rahman (HIGH or AMBIGUOUS with A.R. first), not "Rahman Theme"
@Test public void sceneMovieSongsIsAlbum()             // PLAY_ALBUM "scene" → ALBUM "Scene", HIGH, 4 songs
@Test public void twoRojaAlbumsAreAmbiguous()          // PLAY_ALBUM "roja" → AMBIGUOUS, ranked[0..1] are both Roja with details containing "1992" and "1993"
@Test public void closeButNotSureIsMedium()            // PLAY_ANY "kalava" (key lev 2 from "kavala" → 0.667, coverage 0) → MEDIUM, top = Kaavaalaa
@Test public void unknownIsNone()                      // PLAY_ANY "despacito" → NONE, ranked empty
@Test public void artistThenAlbumFallsBackToAlbum()    // PLAY_ARTIST "scene" with artistThenAlbum=true → ALBUM "Scene"
```

  For the PLAY_ANY tie rule (`playRahmanPicksArtist…`): the ARTIST candidate must win because its token coverage is complete (1.0), while the song "Rahman Theme" only covers a sub-part. Score = max(Levenshtein similarity of full keys, token coverage), where token coverage counts *candidate* tokens, ignoring artist initials (single letters).

- [ ] **Step 3: Run** `./gradlew :app:testDebugUnitTest --tests "*LibraryVoiceMatcherTest"`. Expected: FAIL.

- [ ] **Step 4: Implement.**
  - **Scoring:** `score = max(1 - lev(qKey, cKey)/max(len), coverage)`. `coverage` = (candidate tokens, ignoring single letters, that have a query token with `lev ≤ 1`) / (candidate tokens, ignoring single letters), and it is 0 if any query token is unmatched.
  - **Category bonus:** +0.10 to the intent's kind, capped at 1.0. For PLAY_ANY, apply tie order SONG > ALBUM > ARTIST only when scores are equal.
  - **Grouping:** albums group by `albumId`; artists group by `ArtistIdentityHelper.getCanonicalKey` over `extractArtists(credit)`.
  - **Bands and ranking:** apply the bands from Global Constraints. Ranked keeps the top 4 with score ≥ 0.60.

- [ ] **Step 5: Run** the test again. Expected: PASS.

- [ ] **Step 6: Commit** `feat(voice): phonetic library matcher for songs, artists and albums`.

---

### Task 4: Voice queue guard, ducking and service commands

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/service/VoiceQueueGuard.java`
- Modify: `app/src/main/java/com/psthetech/swara/service/SwaraPlaybackService.java` (new commands in `onConnect` and `onCustomCommand`; timeline listener; duck safety timer)
- Modify: `app/src/main/java/com/psthetech/swara/service/VibeController.java` (Autoplay suppression check inside `maybeRefill`, at the `LibraryPreferences.autoplayEnabled` line ~304)
- Modify: `app/src/main/java/com/psthetech/swara/ui/viewmodel/PlaybackViewModel.java` (new methods)
- Test: `app/src/test/java/com/psthetech/swara/service/VoiceQueueGuardTest.java`

**Interfaces:**
- Produces:
  - `VoiceQueueGuard`: `void activate(long[] songIds)`, `boolean isActive()`, `void onPlaylistChanged(List<Long> currentIds)` (clears unless every current id is in the activated set; an empty list clears), `void clear()`.
  - Service constants: `COMMAND_VOICE_QUEUE = "com.psthetech.swara.VOICE_QUEUE"` with extra `EXTRA_VOICE_SONG_IDS` (`long[]`); `COMMAND_VOICE_DUCK = "com.psthetech.swara.VOICE_DUCK"`; `COMMAND_VOICE_UNDUCK = "com.psthetech.swara.VOICE_UNDUCK"`.
  - `VibeController.setAutoplaySuppressed(BooleanSupplier)`. When the supplier returns true, the Autoplay branch returns early (Vibe is turned off by the command, see below).
  - `PlaybackViewModel.playVoiceQueue(List<Song> songs)`, `duckForVoice()`, `unduckForVoice()`.

- [ ] **Step 1: Write failing `VoiceQueueGuardTest`**

```java
@Test public void activeAfterActivate()            // activate({1,2,3}) → isActive()
@Test public void reorderOrRemoveKeepsActive()     // onPlaylistChanged([3,1]) → still active
@Test public void manualSongClears()               // onPlaylistChanged([1,2,3,99]) → !isActive()
@Test public void newQueueClears()                 // onPlaylistChanged([7,8]) → !isActive()
@Test public void emptyPlaylistClears()
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*VoiceQueueGuardTest"`. Expected: FAIL.

- [ ] **Step 3: Implement `VoiceQueueGuard`** (a `HashSet<Long>` and a boolean).

- [ ] **Step 4: Wire the service.**
  - **`COMMAND_VOICE_QUEUE`:** `vibeController.setEnabled(false)`, `player.setShuffleModeEnabled(false)`, `guard.activate(ids)`.
  - **Clearing the guard:** in the existing player listener's `onTimelineChanged` with `TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED`, call `guard.onPlaylistChanged(ids of all media items)`.
  - **Autoplay:** `vibeController.setAutoplaySuppressed(guard::isActive)`.
  - **Ducking:** `COMMAND_VOICE_DUCK` stores the current `player.getVolume()` (only if not already ducked), sets 0.15, and posts an 8 s `mainHandler` runnable that unducks. `COMMAND_VOICE_UNDUCK` restores the volume and cancels the runnable.
  - **Registration:** register all three in `onConnect`.

- [ ] **Step 5: Add the ViewModel methods.** `playVoiceQueue` first sends `COMMAND_VOICE_QUEUE` with the ids, then calls `queueManager.play(songs, 0)`. Media3 delivers controller commands in order, so the guard is active before the playlist-changed callback arrives.

- [ ] **Step 6: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS, with all existing tests green.

- [ ] **Step 7: Commit** `feat(voice): voice queues that suppress Vibe/Autoplay, plus duck commands`.

---

### Task 5: VoiceCommandExecutor and replies

**Files:**
- Create: `.../voice/exec/VoiceReplies.java`, `.../voice/exec/VoicePlayback.java` (interface), `.../voice/exec/VoiceOutcome.java`, `.../voice/exec/VoiceCommandExecutor.java`, `.../voice/exec/ViewModelVoicePlayback.java`
- Test: `app/src/test/java/com/psthetech/swara/voice/VoiceCommandExecutorTest.java`

**Interfaces:**
- Consumes: `VoiceIntent`, `IntentParser` (Task 2); `LibraryIndex`, `LibraryVoiceMatcher`, `MatchResult`, `Candidate` (Task 3); `PlaybackViewModel.playVoiceQueue/pause/resume/skipToNext/skipToPrevious/playSong` (Task 4 and existing).
- Produces:
  - `interface VoicePlayback { void playSong(Song s); void playQueue(List<Song> songs); void pause(); void resume(); void next(); void previous(); }`; `ViewModelVoicePlayback` adapts `PlaybackViewModel`.
  - `VoiceOutcome { String text; boolean speak; List<Candidate> choices; }`. `choices` is non-empty for MEDIUM/AMBIGUOUS. `speak` is false for transport commands.
  - `VoiceCommandExecutor(VoicePlayback, Random)`: `VoiceOutcome handle(String transcript, @Nullable LibraryIndex index)` and `VoiceOutcome choose(Candidate c)` (used when the user taps a chip).
  - `VoiceReplies` static formatters with exact copy:
    - `playingSong(title)` → `"Playing %s."`
    - `playingArtist(n, name)` → `"Playing %d %s songs, shuffled."`
    - `playingAlbum(name, n)` → `"Playing the album %s, %d songs."`
    - `didYouMean(display)` → `"Did you mean %s?"`
    - `whichOne()` → `"Which one?"`
    - `notFound(query)` → `"I couldn't find %s in your library. Add the songs to your phone and I'll find them."` (query in Title Case)
    - `didntCatch()` → `"Sorry, I didn't catch that."`
    - `libraryLoading()` → `"Your library is still loading, try again in a moment."`
    - `micBusy()` → `"The mic is busy right now."`
    - transport labels `"Paused"`, `"Playing"`, `"Next"`, `"Previous"`

- [ ] **Step 1: Write failing tests** with a fake `VoicePlayback` that records calls, using `VoiceFixtures` and `new Random(42)`:

```java
@Test public void songPlaysSong()                 // "play kavalaa" → playSong(Kaavaalaa); text "Playing Kaavaalaa."
@Test public void artistQueueShuffledAndComplete() // "play rahman songs" → playQueue(12 songs, same set as fixture, order != credit order); "Playing 12 A.R. Rahman songs, shuffled."
@Test public void albumQueueInTrackOrder()        // "play scene movie songs" → playQueue sorted by trackNumber then title; "Playing the album Scene, 4 songs."
@Test public void ambiguousReturnsChoicesNoPlayback() // "play roja movie songs" → no playback calls; text "Which one?"; 2 choices
@Test public void choosingChipPlaysAlbum()        // choose(choices[0]) → playQueue(6 Tamil Roja songs in order 1..6)
@Test public void notFoundReply()                 // "play despacito" → "I couldn't find Despacito in your library. Add the songs to your phone and I'll find them."
@Test public void transportNotSpoken()            // "pause" → pause(); speak == false; text "Paused"
@Test public void emptyLibraryRepliesLoading()    // index == null → libraryLoading(); no playback
@Test public void unknownRepliesDidntCatch()      // "" → didntCatch()
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*VoiceCommandExecutorTest"`. Expected: FAIL.

- [ ] **Step 3: Implement.** Artist queue: `Collections.shuffle(copy, random)`. Album queue: sort by `trackNumber` (MediaStore encodes disc×1000+track), then title. HIGH plays immediately. MEDIUM returns `didYouMean(top.display)` with choices = ranked (≤ 4). AMBIGUOUS returns `whichOne()` with choices = the candidates within 0.10 of the top.

- [ ] **Step 4: Run** the test again. Expected: PASS.

- [ ] **Step 5: Commit** `feat(voice): command executor with spec reply copy`.

---

### Task 6: Moonshine tiny speech-to-text

**Files:**
- Create: `ml/voice_assets/fetch_moonshine.py`, `ml/voice_assets/README.md`
- Create: `app/src/main/assets/voice/moonshine/` (model files, `tokens.txt`, `LICENSE`)
- Create: `.../voice/stt/SpeechToText.java` (interface), `.../voice/stt/MoonshineTokenizer.java`, `.../voice/stt/MoonshineStt.java`, `.../voice/VoiceModels.java`
- Test: `app/src/test/java/com/psthetech/swara/voice/MoonshineTokenizerTest.java`; `app/src/androidTest/java/com/psthetech/swara/voice/MoonshineSttInstrumentedTest.java`; test clips `app/src/androidTest/assets/voice/clips/*.wav` (5 LibriSpeech CC BY 4.0 utterances; credits in `ml/voice_assets/README.md`)

**Interfaces:**
- Produces:
  - `interface SpeechToText extends AutoCloseable { String transcribe(float[] audio16k) throws Exception; }`
  - `MoonshineStt(OrtEnvironment env, AssetManager assets, String dir)`
  - `MoonshineTokenizer.load(InputStream tokensTxt)` with `String decode(int[] ids)`
  - `VoiceModels`: singleton `get(Context)`, `void loadAsync(Runnable onReady)`, `boolean isReady()`, `boolean failed()`, `SpeechToText stt()`, `void release()`. Task 12 adds `wake()`.

- [ ] **Step 1: Fetch and measure (the size gate).** `fetch_moonshine.py` downloads `sherpa-onnx-moonshine-tiny-en-int8.tar.bz2` from the k2-fsa GitHub `asr-models` release (an MIT model re-export; use its ONNX files only, not sherpa code). It verifies a pinned SHA-256, copies `preprocess.onnx`, `encode.int8.onnx`, `uncached_decode.int8.onnx`, `cached_decode.int8.onnx`, `tokens.txt` plus Moonshine's MIT `LICENSE` into `app/src/main/assets/voice/moonshine/`, then prints the total MB and each graph's input/output names, shapes and dtypes (via `onnxruntime`). Record that output in `ml/voice_assets/README.md`.
  Run: `python -I ml/voice_assets/fetch_moonshine.py`. Expected: a total ≤ 43 MB (2 MB is left for the wake model). **If it is larger, stop and report to the owner** before continuing.

- [ ] **Step 2: Write failing `MoonshineTokenizerTest`.** Use a 6-line fake `tokens.txt` (`"▁play 0"`, `"▁ka 1"`, `"va 2"`, `"laa 3"`, `"<s> 4"`, `"</s> 5"`); `decode([0,1,2,3])` returns `"play kavalaa"`, and special tokens are skipped.

- [ ] **Step 3: Run** `./gradlew :app:testDebugUnitTest --tests "*MoonshineTokenizerTest"`. Expected: FAIL. Implement `MoonshineTokenizer` and run again. Expected: PASS.

- [ ] **Step 4: Implement `MoonshineStt.transcribe`.** Follow the four-graph flow recorded in Step 1 (the same flow as sherpa-onnx `offline-moonshine-model.cc` / Moonshine's `onnx` demo):
  1. `preprocess(audio[1,N])` → features.
  2. `encode(features, len)` → encoder output.
  3. `uncached_decode([[sos]], enc, len)` → logits + KV cache.
  4. Loop `cached_decode([[tok]], enc, len, cache…)` with greedy argmax until `eos` or `maxTokens = ceil(6 * N / 16000)`.

  Use 2 intra-op threads; reuse one `OrtSession` per graph.

- [ ] **Step 5: Write the instrumented test** `transcribesLibriSpeechClips`. For each clip, the transcript (lowercased) contains the 2 keywords listed in the README. Also `durationUnderBudget`: a 3 s clip transcribes in < 800 ms on the test device (logged, and it fails only above 2000 ms so slow emulators pass).

- [ ] **Step 6: Run** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.psthetech.swara.voice.MoonshineSttInstrumentedTest`. Expected: PASS.

- [ ] **Step 7: Commit** `feat(voice): Moonshine tiny STT on ONNX Runtime` (include the assets; check `git lfs` is not configured, as Korokae assets are committed directly).

---

### Task 7: Mic, VAD and system TTS

**Files:**
- Create: `.../voice/audio/MicSource.java`, `.../voice/audio/EnergyVad.java`, `.../voice/audio/CommandRecorder.java`, `.../voice/tts/SwaraVoice.java` (interface), `.../voice/tts/SystemTtsVoice.java`
- Modify: `app/src/main/AndroidManifest.xml` (add `RECORD_AUDIO`)
- Test: `app/src/test/java/com/psthetech/swara/voice/EnergyVadTest.java`, `app/src/test/java/com/psthetech/swara/voice/CommandRecorderTest.java`

**Interfaces:**
- Produces:
  - `MicSource`: `interface Listener { void onFrame(float[] frame1280); void onError(Exception e); }`, `boolean start(Listener)` (false if `AudioRecord` init fails, which means the mic is busy), `void stop()`. Uses the `VOICE_RECOGNITION` source and enables `AcousticEchoCanceler` when available. Reads on its own thread.
  - `EnergyVad(int hopSamples)`: `boolean isSpeech(float[] frame)`. RMS against an adaptive noise floor (EMA, alpha 0.05, updated only on non-speech frames); speech when RMS > floor × 3 and > 0.01.
  - `CommandRecorder(EnergyVad vad, int sampleRate)`: `enum State { WAITING, RECORDING, DONE, TIMEOUT }`, `State push(float[] frame)`, `float[] audio()`. It waits up to 6 s total for speech, ends after 0.8 s (10 hops) of trailing silence, and caps at 6 s.
  - `interface SwaraVoice { boolean isAvailable(); void speak(String text, Runnable onDone); void shutdown(); }`. `SystemTtsVoice(Context)` picks the first `Voice` for `Locale.ENGLISH` with `isNetworkConnectionRequired()==false`. If there is none, `isAvailable()` is false and `speak` calls `onDone` immediately.

- [ ] **Step 1: Write failing tests** with synthetic frames (zeros, 0.001 noise, 0.2 sine):

```java
@Test public void silenceIsNotSpeech()
@Test public void loudSineIsSpeech()
@Test public void floorAdaptsToSteadyNoise()            // 50 hops of 0.02 noise → not speech
@Test public void endsAfterEightHundredMsSilence()      // 10 speech hops then 10 silent → DONE; audio length == 20*1280
@Test public void timesOutWithoutSpeech()               // 75 silent hops (6 s) → TIMEOUT
@Test public void capsAtSixSeconds()                    // 80 speech hops → DONE at hop 75
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*EnergyVadTest" --tests "*CommandRecorderTest"`. Expected: FAIL. Implement both, run again. Expected: PASS.

- [ ] **Step 3: Implement `MicSource` and `SystemTtsVoice`.** Both are Android-bound; they are exercised by the Task 9 manual check. Add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` to the manifest.

- [ ] **Step 4: Commit** `feat(voice): mic source, energy VAD, command recorder and system TTS`.

---

### Task 8: VoiceSessionController

**Files:**
- Create: `.../voice/session/VoiceState.java` (enum IDLE, LISTENING, THINKING, REPLY), `.../voice/session/VoiceSessionController.java`
- Test: `app/src/test/java/com/psthetech/swara/voice/VoiceSessionControllerTest.java`

**Interfaces:**
- Consumes: `CommandRecorder`, `EnergyVad` (Task 7); `SpeechToText` (Task 6); `VoiceCommandExecutor`, `VoiceOutcome` (Task 5); `SwaraVoice` (Task 7).
- Produces:
  - `VoiceSessionController(Deps deps)`, where `Deps` has `SpeechToText stt`, `VoiceCommandExecutor executor`, `SwaraVoice voice`, `Supplier<LibraryIndex> library`, `Runnable duck`, `Runnable unduck`, `Executor sttExecutor`, `Executor main`, `BooleanSupplier speakReplies`.
  - `interface Listener { void onState(VoiceState s, @Nullable VoiceOutcome outcome, float level); }`
  - Methods: `void setListener(Listener)`, `boolean startCommand()` (from the mic tap or a wake event; false and ignored if not IDLE), `void onFrame(float[] frame)` (fed by `MicSource` while LISTENING), `void onChoice(Candidate c)`, `void cancel()` (activity paused), `VoiceState state()`.

- [ ] **Step 1: Write failing tests** with direct executors, a fake STT (returns a fixed string or throws), a fake voice (calls `onDone` at once) and duck/unduck counters:

```java
@Test public void happyPath()            // startCommand → duck=1, LISTENING; speech+silence frames → THINKING → REPLY "Playing Kaavaalaa." → voice spoke; after onDone → unduck=1, IDLE
@Test public void wakeIgnoredWhileBusy() // second startCommand during LISTENING returns false; duck still 1
@Test public void timeoutRepliesDidntCatch() // 75 silent frames → REPLY didntCatch; unduck=1
@Test public void unduckOnSttException()     // STT throws → REPLY didntCatch; unduck=1; state IDLE after reply
@Test public void unduckOnPauseMidSession()  // cancel() during LISTENING → unduck=1, IDLE, no reply
@Test public void choicesWaitForTap()        // ambiguous outcome → REPLY with choices; unduck=1 (music resumes while choosing); onChoice → plays, REPLY text
@Test public void speakRepliesOffShowsTextOnly() // speakReplies=false → voice never called
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*VoiceSessionControllerTest"`. Expected: FAIL.

- [ ] **Step 3: Implement.** Wrap every stage in try/catch, so any exception leads to unduck and then IDLE. The level passed to the listener is the frame RMS.

- [ ] **Step 4: Run** the test again. Expected: PASS.

- [ ] **Step 5: Commit** `feat(voice): voice session state machine`.

---

### Task 9: Mic button, listening sheet and lifecycle (tap-to-talk end to end)

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/ui/voice/VoiceSheetFragment.java`, `app/src/main/res/layout/sheet_voice.xml`, `app/src/main/res/drawable/ic_mic.xml`
- Create: `app/src/main/java/com/psthetech/swara/ui/voice/VoiceUiCoordinator.java` (owns `MicSource`, `VoiceSessionController`, permission flow; attached to `MainActivity`)
- Modify: `fragment_mini_player.xml` + `MiniPlayerFragment.java` (mic `ImageButton` `@+id/btnVoice` before `btnPlayPause`); `fragment_search.xml` + `SearchFragment.java` (mic icon in the search field end slot); `MainActivity.java` (create the coordinator; `onResume`/`onPause` hooks; `ActivityResultLauncher` for the permission); `res/values/strings.xml`
- Test: `app/src/test/java/com/psthetech/swara/voice/VoicePermissionPolicyTest.java` (the pure decision part: `shouldShowRationale`, `deniedPermanently → open settings`)

**Interfaces:**
- Consumes: Tasks 5–8, `VoiceModels` (Task 6), `PlaybackViewModel` (Task 4).
- Produces: `VoiceUiCoordinator.onMicTapped()`, `onResume()`, `onPause()` (calls `controller.cancel()` and `mic.stop()`), `onDestroy()` (calls `VoiceModels.release()` and `voice.shutdown()`), and `onWake()` (used by Task 12). If `MicSource.start` returns false, the sheet shows `VoiceReplies.micBusy()` without starting a session. `LibraryIndex` is rebuilt when `MusicRepository.getCachedLibrarySongs()` identity changes (cached by list reference).

- [ ] **Step 1: Write failing `VoicePermissionPolicyTest`** for `VoicePermissionPolicy.decide(boolean granted, boolean shouldShowRationale, boolean askedBefore) -> Action {START, REQUEST, EXPLAIN_SETTINGS}`. The cases: not asked → REQUEST; rationale → REQUEST; asked and no rationale and not granted → EXPLAIN_SETTINGS; granted → START.

- [ ] **Step 2: Run** the test (FAIL), implement it, run it again (PASS).

- [ ] **Step 3: Build the UI.** The sheet shows the state title ("Listening…", "Thinking…"), a level bar, the reply text, and up to 4 chips (`display` + `detail`). It uses the existing Liquid Glass card style (`DesignTokens`). It auto-dismisses 2.5 s after a REPLY without choices. The mic button is hidden while `!VoiceModels.isReady()` and while `failed()` is true. The first tap triggers `loadAsync`, then starts the session. Permission rationale string: "Swara listens only when you tap this or say 'Hey Swara', and nothing leaves your phone."

- [ ] **Step 4: Add the debug voice-test screen.** In debug builds only (`BuildConfig.DEBUG`), long-pressing the mic opens a dialog with a text field. Typed text goes straight to `executor.handle`, and the dialog logs intent, ranked candidates with scores, and outcome. This entry is used for the Task 13 acceptance run.

- [ ] **Step 5: Manual check.** Run `./gradlew :app:installDebug`, then on the phone:
  - tap mic → "play kaavaalaa" → it plays, says "Playing Kaavaalaa.", music is ducked during listening;
  - "play rahman songs" → shuffled queue, and playback stops after the last one (no Autoplay);
  - "pause";
  - deny permission → settings explanation.

  Run `./gradlew :app:testDebugUnitTest`. Expected: all PASS.

- [ ] **Step 6: Commit** `feat(voice): mic button and listening sheet (tap-to-talk)`.

---

### Task 10: Wake-word data pipeline (`ml/wakeword/`)

**Files:**
- Create: `ml/wakeword/requirements.txt`, `ml/wakeword/README.md`, `ml/wakeword/conftest.py`, `ml/wakeword/configs/base.yaml`
- Create: `ml/wakeword/wakeword/__init__.py`, `wakeword/data/manifest.py`, `wakeword/data/synth.py`, `wakeword/data/fetch.py`, `wakeword/data/record.py`, `wakeword/data/mix.py`
- Test: `ml/wakeword/tests/test_manifest.py`, `tests/test_mix.py`, `tests/test_synth.py`

**Interfaces:**
- Produces:
  - Manifest CSV rows: `Row(path, label: "positive"|"negative"|"near_miss"|"background", source, source_url, author, licence, attribution, speaker, split)`. `normalise_licence` uses the **same allowlist** as the Korokae v2 manifest (`CC0-1.0`, `CC-BY-3.0`, `CC-BY-4.0`, `own`). Copy that function and its tests from branch `feature/korokae-v2-training:ml/korokae_v2/korokae_v2/data/manifest.py`, since the two ml projects are independent packages.
  - CLI `python -m wakeword.data.manifest audit --root DIR` exits 1 on problems.
  - `synth.py`: Kokoro-82M (Apache-2.0, run locally) renders "hey swara" (positives) and near-misses ("hey sara", "hey siri", "swaram", "play some", "hey there", "hey sarah", "sweater") across every voice, at speed 0.8–1.25 and ±2 semitones, resampled to 16 kHz. It writes rows with licence `own` (synthetic, generated by us).
  - `record.py`: a CLI that prompts the owner/friends to say "hey swara" 30 times each (16 kHz WAV) and stores speaker id + a consent flag. Rows are written with licence `own` only when consent is given.
  - `fetch.py`: LibriSpeech `train-clean-100` + `dev-clean`/`test-clean` (CC BY 4.0) as speech negatives; CC0/CC BY music from the Korokae v2 audited list as background (read through that branch's manifest CSV when available, else ccMixter CC BY fetch with the same licence gate).
  - `mix.py`: `make_example(clip, background, snr_db, rng) -> np.ndarray[16000]`. It places the clip so it **ends** in the last 200 ms of the 1.0 s window (matching how the detector sees speech as it finishes), with SNR uniform in [-5, 15] dB, random gain, optional reverb (synthetic RIR) and a 300–3400 Hz band-limit "cheap mic" filter with p = 0.3.

- [ ] **Step 1: Write failing tests**
  - `test_manifest.py`: the copied allowlist tests plus `rejects_missing_consent_for_own_recording`.
  - `test_mix.py`: `test_output_length_16000`, `test_snr_within_0p5_db` (measure clip energy vs background), `test_clip_ends_in_last_200ms`.
  - `test_synth.py`: `test_renders_16k_mono` using a monkeypatched fake Kokoro pipeline (no model download in CI).

- [ ] **Step 2: Run** `cd ml/wakeword && python -m pytest -q`. Expected: FAIL.

- [ ] **Step 3: Implement**, then run again. Expected: PASS.

- [ ] **Step 4: Commit** `feat(wakeword): licence-audited data pipeline with Kokoro synthesis and mixing`.

---

### Task 11: Wake-word model, training, eval gate and export

**Files:**
- Create: `ml/wakeword/wakeword/model.py`, `wakeword/train.py`, `wakeword/evaluate.py`, `wakeword/export.py`
- Test: `ml/wakeword/tests/test_model.py`, `tests/test_export.py`, `tests/test_evaluate.py`
- Output: `app/src/main/assets/voice/wakeword/wakeword.onnx`, `app/src/main/assets/voice/wakeword/wakeword.json`

**Interfaces:**
- Produces:
  - `WakeNet(nn.Module)`: input `audio` float32 `[B, 16000]`, output `prob` float32 `[B]`. The log-mel front end is inside the module (n_fft 400, hop 160, 40 mels, log(x + 1e-6)), followed by 5 depthwise-separable conv blocks and global average pooling.
  - `wakeword.json`: `{"threshold": float, "consecutive_hops": 2, "refractory_ms": 2000, "window": 16000, "hop": 1280, "detection_rate": float, "false_triggers_per_hour": float}`.
  - `evaluate.py`: `gate(model, eval_dir) -> GateResult(detection_rate, fa_per_hour, threshold, passed)`. It simulates streaming over the long negatives at an 80 ms hop with the 2-consecutive and 2 s refractory rules. The threshold is the lowest value meeting ≤ 0.1 FA/hour (1 per 10 h); it passes if detection ≥ 0.95 at that threshold.

- [ ] **Step 1: Write failing tests**
  - `test_model.py`: `test_param_count_le_250k`, `test_output_shape_and_range` (prob in [0,1]).
  - `test_evaluate.py`: on a synthetic stream with known spikes, check `fa_per_hour` and the effect of the 2-consecutive and refractory rules (`test_two_consecutive_required`, `test_refractory_merges_triggers`).
  - `test_export.py`: `test_onnx_matches_torch_1e4` (opset 17, ORT 1.31), `test_onnx_size_le_1mb`.

- [ ] **Step 2: Run** `python -m pytest -q`. Expected: FAIL. Implement, run again. Expected: PASS.

- [ ] **Step 3: Train.** Run `python -m wakeword.train --config configs/base.yaml` on Kaggle or the laptop CPU, with BCE loss and positives ≈ 1/3 of each batch. Hard-negative mining: after each epoch, add the top-scoring negative windows from the background stream to the next epoch.

- [ ] **Step 4: Gate and export.** Run `python -m wakeword.evaluate --ckpt best.pt --eval data/eval`. Expected: `passed: true`. If it fails, report the numbers to the owner and do not export. Then run `python -m wakeword.export --ckpt best.pt --out ../../app/src/main/assets/voice/wakeword/`.

- [ ] **Step 5: Commit** `feat(wakeword): WakeNet training, streaming eval gate and ONNX export` (model + json; no data).

---

### Task 12: On-device wake word and Settings → Voice

**Files:**
- Create: `.../voice/wake/WakeGate.java`, `.../voice/wake/WakeWordDetector.java` (interface), `.../voice/wake/OnnxWakeWordDetector.java`, `app/src/main/java/com/psthetech/swara/data/preference/VoicePreferences.java`
- Modify: `VoiceModels.java` (`WakeWordDetector wake()`; load `wakeword.json`), `VoiceUiCoordinator.java` (when wake is enabled and permission is granted, start `MicSource` in `onResume` and feed frames to the detector while IDLE), `fragment_settings.xml` + `SettingsFragment.java` (a "Voice" section: "Hey Swara" switch, sensitivity Low/Normal/High, "Speak replies" switch, privacy note "Audio stays on your phone and is never saved."), `strings.xml`
- Test: `app/src/test/java/com/psthetech/swara/voice/WakeGateTest.java`; `app/src/androidTest/java/com/psthetech/swara/voice/OnnxWakeWordDetectorInstrumentedTest.java` (+ one positive and one negative clip: owner-recorded "hey swara", LibriSpeech negative)

**Interfaces:**
- Consumes: `wakeword.onnx` and `wakeword.json` (Task 11); `MicSource` (Task 7); `VoiceUiCoordinator.onWake()` (Task 9).
- Produces:
  - `WakeGate(float threshold, int consecutive, long refractoryMs)` with `boolean push(float prob, long nowMs)`.
  - `interface WakeWordDetector extends AutoCloseable { boolean onFrame(float[] frame1280, long nowMs); }`. `OnnxWakeWordDetector` keeps a 16000-sample ring buffer and runs `WakeGate`.
  - `VoicePreferences`: `wakeEnabled(Context)` (default **false**), `setWakeEnabled`, `sensitivity(Context)` (`LOW`/`NORMAL`/`HIGH` → threshold ×1.15 / ×1.0 / ×0.85, clamped to [0.05, 0.99]), `speakReplies(Context)` (default true).

- [ ] **Step 1: Write failing `WakeGateTest`**

```java
@Test public void singleHopDoesNotTrigger()
@Test public void twoConsecutiveTrigger()
@Test public void refractoryBlocksSecondTrigger()   // trigger at t=0; high probs at t=1000 → false; at t=2100 (after 2 consecutive) → true
@Test public void dipResetsCount()
```

- [ ] **Step 2: Run** the test (FAIL), implement `WakeGate`, run it again (PASS).

- [ ] **Step 3: Implement `OnnxWakeWordDetector`, the Settings section and coordinator wiring.**
  - **Turning the switch on:** check the mic permission with `VoicePermissionPolicy`; if it is denied, turn the switch back off.
  - **When listening stops:** the mic stops in `onPause`, and when `AudioManager.getMode() != MODE_NORMAL` (checked on each `onResume`, plus an `AudioManager.OnModeChangedListener` on API 31+).
  - **What a wake does:** `onWake()` calls `controller.startCommand()` while the mic keeps running.

- [ ] **Step 4: Write the instrumented test.** The positive clip triggers within its window, the negative clip does not, and the mean `onFrame` inference time is < 5 ms (logged; it fails above 15 ms).

- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.psthetech.swara.voice`. Expected: PASS.

- [ ] **Step 6: Commit** `feat(voice): opt-in "Hey Swara" wake word and voice settings`.

---

### Task 13: Licences, docs and acceptance run

**Files:**
- Create: `app/src/main/assets/licenses/voice.txt` (Moonshine MIT text + attribution, ONNX Runtime MIT, LibriSpeech CC BY 4.0 credit for test clips, Kokoro Apache-2.0 credit as a training tool), `docs/voice-acceptance.md`
- Modify: `fragment_settings.xml` + `SettingsFragment.java` ("Open-source licences" row showing all files in `assets/licenses/` in a scrollable dialog); `README.md` (Features: "Voice control"; Architecture table: `voice/`, `ml/wakeword/`)

- [ ] **Step 1: Add the licences dialog and text.** Verify on device that it opens and lists Moonshine.

- [ ] **Step 2: Check size.** Run `./gradlew :app:bundleRelease` and compare the AAB size with `main` (`git stash`-free: build `main` in a worktree). Expected: ≤ 45 MB added. Record both numbers in `docs/voice-acceptance.md`.

- [ ] **Step 3: Owner acceptance run (manual).** In `docs/voice-acceptance.md`, write the protocol. The owner records about 50 commands in three conditions: a quiet room, music playing on the phone speaker, and headphones. The mix is song (20), artist (12), album (12) and transport (6), against their own library. For each command, note expected vs actual action (the debug long-press screen shows intent and candidates), and time from end of speech to music start (stopwatch on screen recording).
  Pass: ≥ 85% correct actions, median latency < 1.5 s. If it fails, tune the match band thresholds or `PhoneticKey` rules with the failing transcripts added as `LibraryVoiceMatcherTest` cases, then re-run.

- [ ] **Step 4: Privacy check.** Run `grep -rn "Log\.\(d\|i\|v\)" app/src/main/java/com/psthetech/swara/voice` and confirm every match is guarded by `BuildConfig.DEBUG`. Run `aapt2 dump permissions` on the release APK: the only addition vs `main` is `RECORD_AUDIO`, and there is no `INTERNET`.

- [ ] **Step 5: Commit** `docs(voice): licences screen, README and acceptance results`.
