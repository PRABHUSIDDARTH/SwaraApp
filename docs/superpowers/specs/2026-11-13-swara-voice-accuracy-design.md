# Swara Voice — Accuracy for Indian Names and Duplicate Artists — Design

Date: 2026-11-13
Status: Approved in chat, awaiting spec review
Branch: `feature/swara-voice`
Extends: `2026-11-13-swara-voice-design.md` (Swara Voice). Every constraint there still applies:
offline, no new permissions, commercially licensable, Java only, no audio or transcripts logged
in release builds.

## 1. Problem (evidence from the owner's Galaxy S21+, 2026-11-13)

After the input-level fix (commit `0ca5bbf`), the debug diagnostics captured these transcripts
from Moonshine tiny (an English-only model):

| Owner said (probably) | Moonshine heard | Outcome |
|---|---|---|
| play Anirudh songs | "Play an erode songs." | MEDIUM → the 1-song artist "Anirudh" |
| play Anirudh Ravichander songs | "Play an erode ravi chander songs." | HIGH → 29 songs ✅ |
| play A.R. Rahman songs | "Play air of mun songs." / "They are Ramon songs." | NONE / UNKNOWN |
| play Sid Sriram songs (?) | "Play side of Bianca songs." | NONE |
| play … | "Player at…", "There are one songs.", "Three cyber-incored songs.", "But he bought them laptops." | UNKNOWN: "play" lost |
| play Respectaa / play Aeroplane | "respectation" / "aeroplane song" | MEDIUM → correct ✅ |

There are three root causes:

1. **The model doesn't know the library's Indian names.** It replaces them with the nearest
   English words.
2. **The command word is misheard.** "play" becomes "player", "there are", "three" and so on, so
   the parser returns UNKNOWN even when the name is close.
3. **Duplicate artist credits.** "Anirudh" (1 song) and "Anirudh Ravichander" (29 songs) are two
   artists, both in the Artists screen and in voice.

## 2. Decisions (owner-approved)

- Fix recognition with **library biasing at decode time**, not training. Measure the result on
  the owner's recordings. A fine-tune of Moonshine is a possible later project; it is out of scope
  here.
- **Artist duplicates:** automatic merging with a conservative rule, plus a manual
  "Merge with…" / "Unmerge" on the artist page.
- **Order of work:** C (artist merge) → B (matching) → A (biasing) → the approved nav bar /
  Now Playing UI change → back to Swara Voice plan Tasks 12–13.

## 3. Design

### C. Duplicate-artist merging (app-wide)

- **New pure-Java `ArtistAliasResolver`** in `util/`. Input: the library songs plus the user's
  overrides. Output: a map `canonicalKey → mergedKey`, built from
  `ArtistIdentityHelper.getCanonicalKey`.
- **Automatic rule.** Artist S merges into artist L when all of these hold:
  1. S's name tokens are a strict prefix of L's tokens (initials-aware, so "A.R." equals "AR").
  2. L is the **only** library artist whose tokens S prefixes.
  3. S and L are credited on **at least one common album** (same `albumId`).

  Chains resolve to the longest name. The rule never merges two names that are both prefixes of
  different longer names. Example: "Karthik" stays separate when both "Karthik" and
  "Karthik Netha" exist with no shared album.
- **Manual overrides.** A new Room table `artist_alias` holds `from_key TEXT PK`, `to_key TEXT`
  (null means "never merge"), and `created_at`.
  - Room schema goes **6 → 7** with a migration test (the `MigrationV6Test` pattern).
  - An override always wins over the automatic rule.
  - "Unmerge" writes `to_key = null` for the short artist.
- **Where it applies:**
  - `ArtistIdentityHelper.buildArtists` and `getSongsForArtist` (Artists screen and artist page),
    through an optional resolver argument. Existing callers keep their behaviour until wired.
  - `LibraryIndex.build` (voice), so ARTIST candidates use merged groups.
  - The displayed name is the longest name in the group.
- **UI.** The artist page overflow menu gains "Merge with…" (a searchable artist picker) and, for
  a merged artist, "Unmerge <short name>". Both refresh the Artists list.
- **Not touched:** play history, favourites, playlists and file tags. Merging is a view-level
  alias only.

### B. Matching robustness (pure Java)

- **Space-insensitive phonetic comparison.** `LibraryVoiceMatcher` also scores the query key with
  spaces removed against the candidate key with spaces removed, and takes the max. With this,
  "an erode" ≈ "anirudh" and "ravi chander" = "ravichander".
- **Extra folding for Indian names in `PhoneticKey`.** e→i, o→u, and a trailing "h" dropped
  (already), applied **after** the existing rules. Existing test pairs must stay equal.
- **UNKNOWN fallback.** When `IntentParser` returns UNKNOWN for a non-empty transcript, the
  executor retries the whole transcript, with the wake phrase, fillers and the first word removed,
  as PLAY_ANY. It plays only on **HIGH** confidence; otherwise it falls back to the
  "didn't catch that" reply.
- **Leading mishearings of "play".** These count as "play" when followed by a query:
  "player", "played", "plays", "pray", "flay". A mishearing that splits "play" into separate words
  ("there are", "they are", "three", "but he") goes through the UNKNOWN fallback rather than a
  parser rule.

### A. Library biasing in `MoonshineStt`

- **`MoonshineTokenizer.encode(String) → int[]`.** BPE with the 61,249 merges from
  `tokenizer.json` (type BPE, byte_fallback true, with its normalizer sequence applied). It must
  match the Python `tokenizers` library exactly on a fixture of ≥ 200 library-style strings,
  including Tamil/Hindi names in Latin script.
- **`BiasTrie`.** Built once per library-cache version, off the main thread, from these phrases:
  - every song title
  - every merged artist name
  - every noise-stripped album name
  - the command words "play", "songs", "song", "movie", "album", "by", "pause", "resume", "next",
    "previous", "stop", "skip"

  Each phrase is encoded with and without a leading space ("▁") so it can match at a word start.
- **Biasing at each greedy decode step:**
  - Tokens that continue an active trie path, or start a new phrase at a word boundary, get
    `+BIAS` added to their logit (default `BIAS = 3.0`, configurable).
  - The guard: a token is boosted only if it is already within the model's **top-10** logits at
    that step.
  - Active paths are tracked as the sequence is emitted (the same mechanism as shallow-fusion
    contextual biasing).
  - With biasing on, a completed phrase ending also slightly boosts ▁-tokens (a word boundary).
- **Interfaces.**
  - `SpeechToText` gains `default void setBias(@Nullable BiasTrie trie)`.
  - The voice coordinator sets the trie when the library index is (re)built.
  - With no trie, behaviour is unchanged. The existing LibriSpeech instrumented test must still
    pass with biasing off.
- **Desktop port.** The same trie and boost are implemented in
  `ml/voice_assets/check_moonshine.py`. A new `ml/voice_assets/bias_eval.py` takes:
  - a folder of WAVs
  - a `labels.csv` (wav, expected kind, expected name)
  - the library names (exported by the debug app to `cache/voice-debug/library.txt`)

  It prints a before/after table: transcript, matched candidate, correct yes/no. That table is
  what we use to tune `BIAS`.
- **Debug capture.** The debug build keeps the **50** newest command WAVs (was 10) and writes
  `library.txt`. Both are debug-only and stay on the device until the owner pulls them.

## 4. Success criteria

1. **Artists.**
   - "Anirudh" and "Anirudh Ravichander" show as one artist with 30 songs on the owner's phone.
   - "play Anirudh songs" plays all 30.
   - No two genuinely different artists in the owner's library are auto-merged. The owner reviews
     the list of auto-merges, which the debug build logs.
2. **Matching.** All transcripts in the §1 table that have a correct answer are matched correctly
   by B alone (unit tests use the exact strings): "an erode" → Anirudh (merged), and
   "an erode ravi chander" → Anirudh Ravichander.
3. **Biasing.** On ≥ 30 owner recordings of Indian names, labelled and replayed through
   `bias_eval.py`, biasing raises correct actions by ≥ 20 percentage points versus no biasing.
   It must cause **no regression** on the 5 LibriSpeech clips with biasing off, and no more than
   one extra false match in the "noise" category (silence or random speech recorded by the owner)
   with biasing on.
4. **Latency.** Biasing adds < 50 ms per command on the S21+.

## 5. Testing

- **JVM:** `ArtistAliasResolverTest` (prefix rule, uniqueness, shared album, chains, overrides,
  "never merge"), the Room migration 6→7 test, `PhoneticKey`/`LibraryVoiceMatcher` cases from §1,
  the UNKNOWN fallback in `VoiceCommandExecutorTest`, a tokenizer encode-parity test against a
  checked-in fixture generated with Python `tokenizers`, and `BiasTrie` + biased-decode unit tests
  with a fake logits source.
- **Desktop:** `bias_eval.py` on the owner's labelled recordings (not committed: private voice).
- **Device:** the owner repeats the §1 commands. Diagnostics show transcript and match before and
  after.

## 6. Out of scope

Fine-tuning Moonshine, a multilingual model swap, Tamil/Hindi-language commands, and editing file
tags to unify artist names.
