# Swara Voice Accuracy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Voice commands find Indian song, artist and album names, and duplicate artist credits
("Anirudh" / "Anirudh Ravichander") behave as one artist in the Artists screen and in voice.

**Architecture:**
- A pure-Java `ArtistAliasResolver` (automatic rule plus Room-stored overrides) sits under both the
  Artists screen and the voice `LibraryIndex`.
- `LibraryVoiceMatcher` gains space-insensitive keys and Indian-name folding, and the executor gets
  an UNKNOWN fallback.
- `MoonshineStt` gains library biasing: a BPE encoder, a token trie of library phrases, and a logit
  boost limited to the top 10.
- A desktop `bias_eval.py` tunes the boost on the owner's recordings.

**Tech Stack:** Java 11, Room (schema 6→7), ONNX Runtime Android 1.31.1, JUnit 4; Python
`tokenizers` and `onnxruntime` for the parity fixture and the evaluation.

**Spec:** `docs/superpowers/specs/2026-11-13-swara-voice-accuracy-design.md` (extends
`docs/superpowers/specs/2026-11-13-swara-voice-design.md`)

## Global Constraints

- Offline, no new permissions, Java only, and no audio, transcripts or names logged in release
  builds (`BuildConfig.DEBUG` only).
- Merging is view-level only: it never touches play history, favourites, playlists or file tags.
- Automatic merge rule: S merges into L only when (1) S's tokens are a strict prefix of L's tokens
  (initials-aware), (2) L is the only artist S prefixes, and (3) they share ≥ 1 `albumId`. An
  override always wins. An override with `to_key = null` means "never merge".
- Room schema goes 6 → 7, adding the table `artist_alias(from_key TEXT PRIMARY KEY NOT NULL, to_key TEXT, created_at INTEGER NOT NULL)`.
- Biasing defaults: `BIAS = 3.0f`, `TOP_K = 10`. Command words: play, songs, song, movie, album,
  by, pause, resume, next, previous, stop, skip.
- The UNKNOWN fallback acts only on HIGH confidence.
- Mishearings of a leading "play": player, played, plays, pray, flay.
- Debug capture keeps 50 WAVs and writes `cache/voice-debug/library.txt`.
- Success criteria:
  - +20 percentage points of correct actions on ≥ 30 owner recordings
  - < 50 ms added per command
  - no regression on the 5 LibriSpeech clips with biasing off
  - ≤ 1 extra false match in the noise category
- The owner's recordings are never committed.

## Review Focus

1. **A genuinely different artist that shares a first name and an album** (e.g. singer "Karthik"
   and lyricist "Karthik Netha" on one soundtrack): the auto rule would merge them, so the manual
   "Unmerge" must undo it and persist. Test in Task 2 (`unmergeOverrideBeatsAutoRule`) and Task 1
   (`neverMergeOverrideWins`).
2. **An artist page opened from an old key after a merge** (navigation args carry the short key
   "anirudh"): the page must still show the merged artist's songs, not an empty list. Test in
   Task 3 (`getSongsForAliasedKeyReturnsMergedSongs`).
3. **Biasing that turns noise or unrelated speech into a library name**: the top-10 guard must
   hold. Test in Task 7 (`biasDoesNotForceTokenOutsideTopK`); measured on the noise category in
   Task 8.
4. **The UNKNOWN fallback firing on chatter** ("but he bought them laptops") and playing
   something: it acts only on HIGH. Test in Task 5 (`unknownChatterDoesNotPlay`).
5. **A library change while the bias trie is being built** (rescan mid-build): the trie must be
   keyed on the library-cache version and swapped atomically, so a stale trie never crashes or
   mixes libraries. Test in Task 7 (`trieSwapIsAtomicAcrossVersions`).

---

### Task 1: ArtistAliasResolver (pure Java)

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/util/ArtistAliasResolver.java`
- Test: `app/src/test/java/com/psthetech/swara/ArtistAliasResolverTest.java`

**Interfaces:**
- Consumes: `ArtistIdentityHelper.extractArtists(String)`, `getCanonicalKey(String)`,
  `normalizeDisplayName(String)`; `Song.getArtist()/getAlbumId()`.
- Produces:
  - `ArtistAliasResolver.build(List<Song> songs, Map<String, String> overrides) -> ArtistAliasResolver`
    - Keys are canonical keys.
    - A `null` value means "never merge".
    - A value that is a key means "merge into".
  - Methods:
    - `String resolve(String canonicalKey)`: returns the merged key, or the input unchanged.
    - `Map<String, String> autoMerges()`: debug and listing.
    - `String displayName(String mergedKey)`: the longest display name in the group.
    - `static String tokensKey(String name)`: initials-aware tokens joined by a single space.
    - `static ArtistAliasResolver identity()`.

- [ ] **Step 1: Write the failing tests** using `Song` fixtures built with the existing constructor:
  - `anirudhMergesIntoAnirudhRavichander`: "Anirudh" (album 10) plus "Anirudh Ravichander" (albums 10, 11) → `resolve(key("Anirudh")) == key("Anirudh Ravichander")`; display name "Anirudh Ravichander".
  - `noSharedAlbumNoMerge`: same names on albums 10 and 11 only → no merge.
  - `ambiguousPrefixNoMerge`: "Karthik" with "Karthik Netha" and "Karthik Raja", all sharing album 10 → "Karthik" unmerged.
  - `initialsAware`: "A.R." does not merge (single initials are ignored as tokens, so the prefix is empty), and "A.R. Rahman" equals "AR Rahman" equals "A. R. Rahman" (already one key).
  - `chainsResolveToLongest`: "Sid" → "Sid Sriram" → "Sid Sriram Live" (each pair shares an album, and each is the unique prefix at its step) resolves to "Sid Sriram Live".
  - `mergeOverrideWins`: overrides {"karthik" → key("Karthik Raja")} merges despite the ambiguity.
  - `neverMergeOverrideWins`: overrides {key("Anirudh") → null} → unmerged.
  - `multiArtistCreditCountsForEach`: "Anirudh, Arivu" on album 10 still lets "Anirudh" merge into "Anirudh Ravichander".

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --offline --tests "*ArtistAliasResolverTest"`. Expected: FAIL (class missing).

- [ ] **Step 3: Implement `ArtistAliasResolver`.**
  1. Group songs by canonical key, giving each key's token list (from `tokensKey`) and its set of album IDs.
  2. For each key S, find all keys L where S's tokens are a strict prefix of L's tokens.
  3. Auto-merge when exactly one such L exists and the two album sets intersect.
  4. Apply overrides last.
  5. Resolve chains, with cycle protection: stop after 8 hops and keep the original key.

- [ ] **Step 4: Run** the same command. Expected: PASS.

- [ ] **Step 5: Commit** `feat(artists): automatic alias resolver for duplicate artist credits`.

---

### Task 2: Room `artist_alias` table, migration 6 → 7 and repository

**Files:**
- Create: `data/db/entity/ArtistAlias.java`, `data/db/dao/ArtistAliasDao.java`,
  `data/repository/ArtistAliasRepository.java` (all under `app/src/main/java/com/psthetech/swara/`).
- Modify: `data/db/AppDatabase.java` (entity, version 7, `MIGRATION_6_7`, `artistAliasDao()`).
- Test: `app/src/androidTest/java/com/psthetech/swara/MigrationV7Test.java` (follow
  `MigrationV6Test`), `app/src/test/java/com/psthetech/swara/ArtistAliasRepositoryTest.java`
  (JVM, with a fake DAO).

**Interfaces:**
- Consumes: `ArtistAliasResolver.build/identity` (Task 1); `MusicRepository.getCachedLibrarySongs()`
  and `getLibraryCacheVersion()`.
- Produces:
  - **DAO:** `@Query List<ArtistAlias> getAll()`, `LiveData<List<ArtistAlias>> observeAll()`,
    `@Insert(onConflict = REPLACE) void upsert(ArtistAlias)`, `@Query void delete(String fromKey)`.
  - **Repository (`ArtistAliasRepository.get(Context)`):**
    - `ArtistAliasResolver resolver()`: cached, and rebuilt off the main thread when the library
      cache version or the overrides change. Before the first build it returns `identity()`.
    - `void merge(String fromKey, String toKey)`, `void unmerge(String fromKey)` (writes
      `to_key = null`), `void clearOverride(String fromKey)`: all run on a background executor.
    - `LiveData<Long> version()`: bumps whenever the resolver changes.

- [ ] **Step 1: Write the failing tests.**
  - `ArtistAliasRepositoryTest.unmergeOverrideBeatsAutoRule`: library with Anirudh and Anirudh Ravichander sharing an album → `unmerge(key("Anirudh"))` → after rebuild, `resolve` returns the input; `version` bumped.
  - `ArtistAliasRepositoryTest.resolverRebuildsOnLibraryVersionChange`.
  - `MigrationV7Test`: create v6 with existing rows, migrate to 7, the `artist_alias` table exists and is empty, and other tables keep their rows.
- [ ] **Step 2: Run** the JVM test (FAIL). Build the migration test with `./gradlew :app:assembleDebugAndroidTest --offline`.
- [ ] **Step 3: Implement** the entity, DAO, migration SQL (the exact DDL in Global Constraints) and repository. Export the schema JSON to `app/schemas` as the build already does.
- [ ] **Step 4: Run** the JVM tests (PASS). On the owner's phone (`adb -s RFCNC0X6EXF`, install with `adb install -r` for both APKs, then `am instrument -w -e class com.psthetech.swara.MigrationV7Test com.psthetech.swara.test/androidx.test.runner.AndroidJUnitRunner`). Expected: OK. Never uninstall the app.
- [ ] **Step 5: Commit** `feat(artists): artist_alias overrides table (schema 7) and repository`.

---

### Task 3: Use the resolver in the Artists screen and the voice index

**Files:**
- Modify: `util/ArtistIdentityHelper.java`. Add overloads
  `buildArtists(List<Song>, ArtistAliasResolver)` and
  `getSongsForArtist(String artistOrKey, List<Song>, ArtistAliasResolver)`. The old signatures
  delegate with `identity()`.
- Modify: `data/repository/MusicRepository.java:193-212,313-317,459`, and
  `ui/viewmodel/LibraryViewModel.java:326-332`. Use `ArtistAliasRepository.get(ctx).resolver()`
  and re-emit when `version()` changes.
- Modify: `voice/match/LibraryIndex.java`. Add `build(List<Song>, ArtistAliasResolver)`; ARTIST
  candidates group by the resolved key, and display uses `resolver.displayName`.
- Modify: `ui/voice/VoiceUiCoordinator.java`. Pass the resolver into the index build, and key the
  index cache on (library version, alias version).
- Test: `app/src/test/java/com/psthetech/swara/ArtistAliasWiringTest.java`; extend
  `voice/LibraryVoiceMatcherTest`.

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: `LibraryIndex.build(List<Song>, ArtistAliasResolver)`, used by Tasks 5 and 7.

- [ ] **Step 1: Write the failing tests.**
  - `buildArtistsMergesAnirudh`: one Artist "Anirudh Ravichander" with 30 songs.
  - `getSongsForAliasedKeyReturnsMergedSongs`: `getSongsForArtist(key("Anirudh"), songs, resolver)` returns 30 songs.
  - `LibraryVoiceMatcherTest.anirudhMatchesMergedArtist`: PLAY_ARTIST "anirudh" → ARTIST "Anirudh Ravichander", 30 songs, HIGH.
  - Old-signature tests stay green.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --offline`. Expected: the new tests FAIL.
- [ ] **Step 3: Implement** the overloads and wiring.
- [ ] **Step 4: Run** the full unit suite plus `:app:assembleDebug --offline`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(artists): merged artists in the Artists screen and voice index`.

---

### Task 4: Artist page "Merge with…" / "Unmerge", and the debug merge list

**Files:**
- Modify: `ui/library/ArtistDetailFragment.java` and its layout. Add an overflow menu (follow the
  existing menu or `SongMenuHelper` style).
- Create: `ui/library/ArtistPickerBottomSheet.java` and its layout: a searchable list of artists,
  excluding the current one.
- Modify: `res/values/strings.xml` with these exact strings:
  - "Merge with…"
  - "Unmerge %1$s"
  - "Merged %1$s into %2$s"
  - "%1$s is now a separate artist"
- Modify: `ui/voice/VoiceUiCoordinator.java` or `voice/debug/VoiceDiag.java`. In debug only, log
  each auto-merge as `merge: "<short>" -> "<long>"` with tag `SwaraVoiceDiag` once per resolver
  build.
- Test: `app/src/test/java/com/psthetech/swara/ArtistMergeMenuPolicyTest.java` for a pure helper,
  `ArtistMergeMenuPolicy.items(String key, ArtistAliasResolver r) -> List<Item>`. It shows "Unmerge
  <short>" for each short key merged into this artist, and always shows "Merge with…".

- [ ] **Step 1: Write the failing test** for `ArtistMergeMenuPolicy` with the cases: merged artist shows "Unmerge Anirudh"; plain artist shows only "Merge with…".
- [ ] **Step 2: Run** it (FAIL), implement it, run it (PASS).
- [ ] **Step 3: Build the menu and picker.**
  - Choosing an artist calls `merge(currentKey, pickedKey)`, shows a snackbar ("Merged …") and navigates to the merged artist.
  - Unmerge calls `unmerge(shortKey)`.
- [ ] **Step 4: Check on the owner's phone.** Install with `adb install -r`. Confirm:
  - Artists shows one "Anirudh Ravichander" with 30 songs.
  - Unmerge, then Merge again, round-trips.
  - The `SwaraVoiceDiag` merge list is printed.
  - Take screenshots into the scratchpad.
  - The owner checks the merge list for false merges (success criterion 1).
- [ ] **Step 5: Commit** `feat(artists): merge and unmerge artists from the artist page`.

---

### Task 5: Matching robustness for Indian names and misheard "play"

**Files:**
- Modify: `voice/match/PhoneticKey.java`. After the existing rules: e→i, o→u, and drop a final
  "h". Also add `static String joined(String key)`, which removes spaces.
- Modify: `voice/match/LibraryVoiceMatcher.java`. The score is the max of the existing score and
  the similarity of the joined keys.
- Modify: `voice/intent/IntentParser.java`. Treat a leading player/played/plays/pray/flay followed
  by a query as "play".
- Modify: `voice/exec/VoiceCommandExecutor.java`. On UNKNOWN with a non-empty transcript, retry as
  PLAY_ANY on the transcript minus its first word (after wake-phrase and filler stripping), and act
  only on HIGH; otherwise reply `didntCatch()`.
- Test: extend `PhoneticKeyTest`, `LibraryVoiceMatcherTest`, `IntentParserTest` and
  `VoiceCommandExecutorTest`, using the exact transcripts from the spec §1.

**Interfaces:**
- Consumes: `LibraryIndex.build(List<Song>, ArtistAliasResolver)` (Task 3).
- Produces: `PhoneticKey.joined(String)`.

- [ ] **Step 1: Write the failing tests.**
  - `PhoneticKeyTest`: all existing pairs stay equal; `of("Anirudh")` and `of("anirud")` are equal; `joined(of("an erode"))` versus `joined(of("Anirudh"))` has Levenshtein distance ≤ 2.
  - `LibraryVoiceMatcherTest` (fixture plus merged Anirudh): PLAY_ARTIST "an erode" → ARTIST "Anirudh Ravichander" with confidence ≥ MEDIUM; PLAY_ARTIST "an erode ravi chander" → the same, HIGH; PLAY_ANY "respectation" → SONG "Respectaa" (add it to the fixture) with ≥ MEDIUM.
  - `IntentParserTest`: "Player kaavaalaa" → PLAY_ANY "kaavaalaa"; "plays rahman songs" → PLAY_ARTIST "rahman".
  - `VoiceCommandExecutorTest`: `unknownFallbackPlaysOnHigh` ("there are kaavaalaa" → plays Kaavaalaa); `unknownChatterDoesNotPlay` ("But he bought them laptops." → `didntCatch()`, no playback).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --offline`. Expected: the new tests FAIL.
- [ ] **Step 3: Implement** the changes above.
- [ ] **Step 4: Run** the full suite. Expected: PASS, with all previous voice tests green.
- [ ] **Step 5: Commit** `feat(voice): Indian-name folding, joined-word matching and misheard-play fallback`.

---

### Task 6: BPE encoder for the Moonshine tokenizer

**Files:**
- Create: `ml/voice_assets/gen_encode_fixture.py`. It reads `app/src/main/assets/voice/moonshine/tokenizer.json`
  with the Python `tokenizers` library, and writes
  `app/src/test/resources/voice/encode_fixture.json` (≥ 200 strings → ids, encoded without
  special tokens). The strings are library-style: English titles, Tamil/Hindi names in Latin
  script, punctuation, digits, a leading-space variant of each, and a few non-ASCII strings.
- Modify: `voice/stt/MoonshineTokenizer.java`. Add `int[] encode(String text)`: apply the
  normalizer sequence from `tokenizer.json`, then BPE by merge rank, then byte fallback for
  unknown characters.
- Test: `app/src/test/java/com/psthetech/swara/voice/MoonshineTokenizerEncodeTest.java`.

**Interfaces:**
- Produces: `MoonshineTokenizer.encode(String) -> int[]`, and
  `MoonshineTokenizer.isWordStart(int tokenId)` (the token's piece starts with "▁").

- [ ] **Step 1: Generate the fixture.** Run `<voice-venv>/bin/python -I ml/voice_assets/gen_encode_fixture.py`, after `uv pip install tokenizers` into the scratch voice-venv.
- [ ] **Step 2: Write the failing test** `encodeMatchesPythonTokenizers`: every fixture string gives exactly the fixture ids. Add `decodeOfEncodeRoundTrips` for ASCII strings.
- [ ] **Step 3: Run** (FAIL), implement, run (PASS).
- [ ] **Step 4: Commit** `feat(voice): BPE encoder matching the Moonshine tokenizer`.

---

### Task 7: BiasTrie and biased greedy decoding

**Files:**
- Create: `voice/stt/BiasTrie.java`.
  - `static BiasTrie build(Collection<String> phrases, MoonshineTokenizer tok, long version)`.
  - It encodes each phrase both with and without the leading "▁", and stores token-ID paths.
  - The command words (Global Constraints) are always added.
- Create: `voice/stt/BiasState.java`.
  - It tracks active trie paths during decoding.
  - `float[] apply(float[] logits)`: boosts tokens that continue an active path, or that start a
    phrase where `isWordStart`. Only tokens within the top `TOP_K` of the unmodified logits are
    boosted, by `BIAS`.
  - `void advance(int emittedToken)`.
- Modify: `voice/stt/SpeechToText.java` (`default void setBias(@Nullable BiasTrie trie) {}`) and
  `voice/stt/MoonshineStt.java`. Store the trie in a volatile field read once per transcribe; in the
  greedy loop, call `argmax(state.apply(logits))` and then `state.advance(token)`.
- Modify: `ui/voice/VoiceUiCoordinator.java`. On the worker, after the index (re)build, build the
  trie from the song titles, merged artist display names and noise-stripped album names, then
  `stt.setBias(trie)`.
- Modify: `ml/voice_assets/check_moonshine.py`. Port the same trie and boost behind a `bias=`
  argument.
- Test: `app/src/test/java/com/psthetech/swara/voice/BiasTrieTest.java`.

**Interfaces:**
- Consumes: `MoonshineTokenizer.encode/isWordStart` (Task 6), `LibraryIndex` (Task 3).
- Produces: `BiasTrie`, `BiasState`, `SpeechToText.setBias`.

- [ ] **Step 1: Write the failing tests** with a fake 50-token vocabulary and hand-made logits:
  - `boostsTokenContinuingPhrase`: path [5, 9] is active after 5 → token 9 (rank 3) wins over rank 1 when `BIAS` = 3.0 closes the gap.
  - `biasDoesNotForceTokenOutsideTopK`: token 9 at rank 12 is not boosted.
  - `commandWordsAlwaysPresent`.
  - `noTrieMeansIdenticalArgmax`: `apply` returns the logits unchanged.
  - `trieSwapIsAtomicAcrossVersions`: `setBias` with a new trie during decode → the current transcribe keeps its trie, and the next uses the new one.
- [ ] **Step 2: Run** (FAIL), implement, run (PASS).
- [ ] **Step 3: Check on the desktop.** Run `<voice-venv>/bin/python -I ml/voice_assets/check_moonshine.py`. The 5 LibriSpeech clips must be OK with biasing off. Report the biased-decode time added per clip, which must be < 50 ms on the desktop.
- [ ] **Step 4: Run** the full unit suite and `:app:assembleDebug`. Install on the S21+ with `adb install -r` and confirm there is no crash.
- [ ] **Step 5: Commit** `feat(voice): library-biased Moonshine decoding`.

---

### Task 8: Bias evaluation on the owner's recordings, and tuning

**Files:**
- Modify: `voice/debug/VoiceDiag.java` (`KEEP_WAVS = 50`; write `cache/voice-debug/library.txt`
  with one library phrase per line on each index build; debug only).
- Create: `ml/voice_assets/bias_eval.py`.
  - CLI: `--wavs DIR --labels labels.csv --library library.txt --bias 0,1.5,3,4.5`.
  - `labels.csv` has the columns `wav,kind,expected`; kind is one of song, artist, album or noise.
  - For each bias value it prints a table (wav, transcript, top match, correct?) and a summary:
    the percentage correct per kind and the false matches on noise.
  - Matching uses a Python port of `PhoneticKey` plus the joined-key score, or calls the JVM
    matcher through a tiny Gradle task; pick the simplest that matches the Java results on the
    `LibraryVoiceMatcherTest` cases.
- Create: `docs/voice-accuracy-eval.md`, holding the protocol and the results table. Never include
  WAVs.

- [ ] **Step 1: Owner records the commands.** The owner records ≥ 30 commands with Indian names, plus ≥ 5 noise or chatter samples. The controller pulls them with `adb exec-out run-as com.psthetech.swara cat cache/voice-debug/<f>` into the scratchpad only, and the owner fills `labels.csv`.
- [ ] **Step 2: Run** `bias_eval.py` with bias values 0, 1.5, 3 and 4.5.
- [ ] **Step 3: Choose the boost.** Pick the `BIAS` that meets the spec (+20 points versus 0, and ≤ 1 extra noise false match). Set it as the default in `BiasState`, and record the table in `docs/voice-accuracy-eval.md`.
- [ ] **Step 4: Repeat on the device.** The owner repeats the spec §1 commands on the S21+, and the diagnostics confirm the matches.
- [ ] **Step 5: Commit** `feat(voice): tuned library bias and accuracy evaluation results`.
