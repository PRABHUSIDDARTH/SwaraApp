# Swara Voice — Offline "Hey Swara, play …" — Design

Date: 2026-11-13
Status: Approved in brainstorming, awaiting spec review
Branch: `feature/swara-voice` (off `main`)

## 1. Goal and constraints

Let the user control Swara by voice: "hey Swara, play Kaavaalaa", "play Rahman songs",
"play Roja movie songs". Swara finds the song, artist or album in the **local library**, plays it,
and replies out loud ("Playing 42 A.R. Rahman songs, shuffled.").

Constraints, all decided with the product owner:

1. **Fully offline.** No network permission is added; Swara still has none. Audio is processed in
   memory and dropped after each command. Nothing is saved, logged or sent.
2. **Commercially licensable.** Swara ships free on the Play Store with commercial options kept
   open. Every shipped model and library is Apache-2.0, MIT, or ours. No GPL code ships.
3. **Bundle small open models, plus one model of our own.** Speech-to-text uses a pretrained
   open model (this differs from Korokae v2's "ours end to end" rule; the owner chose it because
   training STT needs speech data we cannot license). The wake-word model is trained by us on data
   we own or that is CC0 / CC BY.
4. **Listening only while Swara is on screen.** No background listening, no microphone foreground
   service.
5. **English commands; song, artist and album names may be Tamil/Hindi words in Latin script**
   ("Kaavaalaa", "Roja", "Rahman").
6. **Java only** (no Kotlin), consistent with the rest of the app.

### Licence findings that shaped this design (checked 2026-11-13)

| Component | Licence | Decision |
|---|---|---|
| Moonshine tiny (English STT) | MIT | **Ship** (int8 ONNX) |
| ONNX Runtime Android 1.31.1 | MIT | Already a dependency (Korokae v2); reuse |
| sherpa-onnx KWS gigaspeech-3.3M | Apache-2.0 on weights, but trained on GigaSpeech (non-commercial dataset); commercial status is an open upstream issue (k2-fsa/sherpa-onnx#3802) | **Do not ship**; train our own wake word |
| Kokoro-82M (TTS) | Apache-2.0 weights | **Training-time only** for wake-word positives; not shipped in v1 of this feature |
| espeak-ng (G2P used by Kokoro/Piper at runtime) | GPL-3.0 | **Do not ship.** Fine to use offline on the training machine (its output audio is not covered) |
| Android `TextToSpeech` | Platform API | **Ship** as the reply voice |

sherpa-onnx itself is not used. Its AAR bundles its own `libonnxruntime.so`, which would clash
with the existing ONNX Runtime dependency, and both of our models can run on ONNX Runtime directly.

### Success criteria (definition of done)

1. **Wake word (offline eval set):** ≥ 95% detection of "hey Swara" and ≤ 1 false trigger per
   10 hours of mixed music and speech audio. This is a gate before export.
2. **End to end on the owner's phone:** of ~50 recorded real commands against the owner's library,
   ≥ 85% lead to the correct action (right song / artist queue / album / transport command).
3. **Latency:** end of speech → music starts in < 1.5 s on a mid-range phone (Snapdragon 6-series
   class).
4. **Size:** ≤ 45 MB added to the APK/AAB.
5. **Privacy:** no new permission other than `RECORD_AUDIO`; no audio or transcript written to
   disk or logs in release builds.

## 2. User experience

### Entry points

- **Mic button** in the mini player and the search bar. Always available once the models load.
  The first tap asks for `RECORD_AUDIO` with a one-line reason ("Swara listens only when you tap
  this or say 'Hey Swara', and nothing leaves your phone.").
- **"Hey Swara" wake word.** Opt-in, **off by default**, toggled in Settings → Voice. While it is
  on and Swara is on screen, the Android 12+ green mic indicator is visible, which is why it is
  opt-in.

### Listening sheet

A small bottom sheet in the app's Liquid Glass style with four states:
`Listening…` (live level meter) → `Thinking…` → reply text (with optional chips) → auto-dismiss
after 2.5 s, or immediately when the user picks a chip.

### Commands

`IntentParser` is rule-based (no ML). Cue words choose the category:

| The user says | Intent | Looked up |
|---|---|---|
| "play kaavaalaa" | `PLAY_ANY` | song first, then album, then artist |
| "play rahman songs" / "songs by rahman" / "play rahman" | `PLAY_ARTIST` | artist |
| "play roja movie songs" / "play roja album" / "roja padam songs" / "roja film songs" | `PLAY_ALBUM` | album |
| "pause" / "stop" | `PAUSE` | — |
| "resume" / "play" (alone) / "continue" | `RESUME` | — |
| "next" / "skip" | `NEXT` | — |
| "previous" / "go back" | `PREVIOUS` | — |

The wake phrase and fillers ("hey swara", "can you", "please", "the", "some") are removed before
parsing. "play X" with no cue word is `PLAY_ANY`; "play X songs" with no movie/album/film/padam cue
is `PLAY_ARTIST` first, then album if no artist matches.

### What gets played

The executor builds the song list in memory and plays it through the existing
`QueueManager.play(songs, startIndex)`. It is **not** saved to Room or shown as a playlist.

- **Song:** that one song. The rest of the queue follows existing behaviour.
- **Artist:** every library song where `ArtistIdentityHelper.containsArtist(credit, artistKey)`
  is true (so "A.R. Rahman, Shreya Ghoshal" counts for A.R. Rahman), **shuffled**.
- **Album:** every song of the album **in track order** (disc, then track number, then title),
  starting at track 1, with shuffle turned off.
- **Only what was asked for:** an artist or album voice queue turns **Vibe Mode and Autoplay off
  for that queue**. When its last song ends, playback stops. The next manual play restores the
  user's normal Vibe/Autoplay settings. This is implemented as a session flag in
  `SwaraPlaybackService` (`voiceQueueActive`), cleared on the next non-voice `play()`.

### Replies

The same text is shown on the sheet and spoken through `SwaraVoice`:

| Situation | Reply |
|---|---|
| Song | "Playing Kaavaalaa." |
| Artist | "Playing 42 A.R. Rahman songs, shuffled." |
| Album | "Playing the album Roja, 6 songs." |
| Medium confidence | "Did you mean Kaavaalaa?" + **Yes** + up to 3 alternative chips |
| Two close candidates | "Which one?" + chips with disambiguating detail ("Roja · Tamil · 1992", "Roja · Hindi · 1992", or artist names) |
| Not found | "I couldn't find Scene in your library. Add the songs to your phone and I'll find them." |
| Unclear / empty | "Sorry, I didn't catch that." |
| Transport | No speech; a short sheet message ("Paused") only, so the reply doesn't talk over music |

Swara never offers to download anything; it has no network access.

## 3. Architecture

New package `com.psthetech.swara.voice`. Every unit has one job and is testable alone.

```
 Mic (AudioRecord, 16 kHz mono, VOICE_RECOGNITION source + AcousticEchoCanceler if available)
   │   runs only while MainActivity is resumed AND (wake word enabled OR mic tapped)
   ▼
 WakeWordDetector ── score ≥ threshold for 2 consecutive hops ──┐      Mic button
 (our ONNX, 1.0 s window, 80 ms hop)                             │◄──── (skips wake word)
                                                                 ▼
                                            CommandRecorder  (playback ducked to ~15%)
                                            energy VAD: ends after 0.8 s silence, max 6 s
                                                                 ▼
                                            SpeechToText → MoonshineStt (ONNX Runtime)
                                                                 ▼  "play kavalaa"
                                            IntentParser → VoiceIntent(type, query)
                                                                 ▼
                                            LibraryVoiceMatcher → MatchResult(candidates, confidence)
                                                                 ▼
                                            VoiceCommandExecutor → QueueManager / MediaController
                                                                 ▼
                                            SwaraVoice.speak(reply)  → volume restored after reply
```

### Units

| Unit | Responsibility | Depends on |
|---|---|---|
| `MicSource` | Owns `AudioRecord`; emits 16 kHz mono `float[]` frames of 1280 samples (80 ms); enables AEC when `AcousticEchoCanceler.isAvailable()` | Android audio |
| `WakeWordDetector` (interface) / `OnnxWakeWordDetector` | Ring buffer of the last 1.0 s; runs the model every hop; emits `onWake()` after 2 consecutive hops ≥ threshold; 2 s refractory period | ONNX Runtime |
| `CommandRecorder` | Collects audio after wake/tap; energy VAD with adaptive noise floor; returns the utterance (≤ 6 s) | `MicSource` |
| `SpeechToText` (interface) / `MoonshineStt` | `String transcribe(float[] audio16k)`; Moonshine preprocess/encode/decode graphs with greedy decoding; tokenizer in Java | ONNX Runtime |
| `IntentParser` | Pure Java; transcript → `VoiceIntent {type, query}` per §2 | — |
| `LibraryVoiceMatcher` | Pure Java; `match(VoiceIntent, List<Song>)` → ranked candidates of kind SONG / ARTIST / ALBUM with scores; confidence band HIGH / MEDIUM / AMBIGUOUS / NONE | `KorokaeMatcher.normalizeString`, `ArtistIdentityHelper`, `PhoneticKey` |
| `PhoneticKey` | Pure Java; folds transliteration variants into one key | — |
| `VoiceCommandExecutor` | Turns a chosen candidate or transport intent into queue actions and reply text; sets `voiceQueueActive` through a custom session command (`ACTION_VOICE_QUEUE`) before `play()` | `PlaybackViewModel` (which wraps `QueueManager`), `MusicRepository` |
| `SwaraVoice` (interface) / `SystemTtsVoice` | `speak(String)`, `isAvailable()`; uses `TextToSpeech` and only offline-capable voices (`Voice.isNetworkConnectionRequired() == false`); silent otherwise | Android TTS |
| `VoiceSessionController` | State machine IDLE → LISTENING → THINKING → REPLY → IDLE; owns ducking; tied to `MainActivity` lifecycle | all of the above |
| `VoiceModels` | Loads both ONNX models lazily on a background thread on first use; exposes `isReady()` | ONNX Runtime |
| `ui/voice/VoiceSheetFragment` | The listening sheet | `VoiceSessionController` |
| Settings → Voice | Wake-word toggle, wake sensitivity (Low/Normal/High → threshold), "Speak replies" toggle | `VoicePreferences` |

`SwaraVoice` is an interface so a bundled voice (e.g. Kokoro with a non-GPL G2P) can replace
`SystemTtsVoice` later without touching anything else.

### Matching details

1. **Normalize:** `KorokaeMatcher.normalizeString` (lowercase, strip punctuation), then remove album
   noise: "(original motion picture soundtrack)", "ost", "(from …)", and the junk-tag patterns
   already in `ArtistIdentityHelper` ("- MassTamilan" etc.). The noise-stripping code is moved
   into a shared `TagNoise` utility rather than duplicated.
2. **PhoneticKey:** lowercase ASCII; `aa/ee/oo/uu → a/i/u/u`; `zh → l`; `th/dh → t/d`;
   `ph → f`; `w → v`; collapse doubled letters; drop `h` after consonants; drop non-letters.
   "Kaavaalaa" and "kavalaa" both become `kavala`.
3. **Score** = max of (a) normalized Levenshtein similarity of phonetic keys, (b) token-overlap
   score where every query token must match a candidate token by phonetic key. Initials
   ("a", "r") are ignored for artists, so "rahman" fully covers "A.R. Rahman".
4. **Category weighting:** the intent's category gets a +0.10 bonus; `PLAY_ANY` orders ties
   song > album > artist.
5. **Bands:** HIGH if top ≥ 0.85 and lead over the next distinct candidate ≥ 0.10; AMBIGUOUS if
   top ≥ 0.85 but the lead is < 0.10; MEDIUM if 0.60 ≤ top < 0.85; NONE below 0.60. Thresholds
   are tuned against the unit-test fixture and the 50-command set.
6. Album candidates with the same normalized name are distinct by album ID and are disambiguated
   by album artist and year.
7. The library comes from `MusicRepository.getCachedLibrarySongs()`; phonetic keys are
   precomputed once per library refresh and cached in memory.

## 4. Models

### 4.1 Speech-to-text: Moonshine tiny (MIT)

- The int8 ONNX export (preprocessor, encoder, uncached and cached decoders) ships in
  `app/src/main/assets/voice/moonshine/` with its tokenizer and `LICENSE`. Exact files and size are
  measured in the implementation plan.
- It takes raw 16 kHz audio, so no feature extraction is needed in Java.
- Greedy decoding, max tokens = 6 tokens/s × duration.
- Credits are added to the in-app open-source licences screen and `docs/`.

### 4.2 Wake word: ours (`ml/wakeword/`)

- **Input:** raw 16 kHz audio, 1.0 s (16,000 samples). The log-mel front end (40 bins,
  25 ms / 10 ms) is **inside the ONNX graph**, so Java and Python can never disagree on features.
  Output is one probability.
- **Architecture:** a small depthwise-separable CNN (target ≤ 250k parameters, ≤ 1 MB fp32, < 5 ms
  per hop on a mid-range phone CPU).
- **Positives:** (a) "hey swara" synthesised with Kokoro-82M across all its voices, with speed
  and pitch variation; (b) recordings of the owner and consenting friends/family (owned by us,
  consent recorded in the manifest).
- **Negatives:** LibriSpeech (CC BY 4.0) speech; near-miss phrases synthesised with Kokoro
  ("hey sara", "hey siri", "swaram", "play some", "hey there"); the CC0/CC BY music already audited
  in the Korokae v2 manifest; synthetic room noise.
- **Augmentation:** mix positives over music at −5 to +15 dB SNR, reverb, gain, and
  low-quality-mic filtering, so playback through the speaker does not trigger it.
- **Licence audit:** the same licence-checked manifest pattern as `ml/korokae` (every file has a
  source, licence and credit; NC/ND/SA rejected).
- **Eval and gate:** a held-out set of real recordings (owner + 2 or more other speakers) plus 10
  hours of held-out music/speech. Export only if success criterion 1 passes. The threshold is
  chosen on the eval set and written next to the model in `wakeword.json`.
- **Training:** Kaggle free GPU or the laptop CPU (the model is small); smoke tests run on the
  laptop.

## 5. Platform details

- **Manifest:** add `android.permission.RECORD_AUDIO`. No foreground-service type change, because
  listening never runs in the background.
- **Lifecycle:** `VoiceSessionController` starts `MicSource` in `MainActivity.onResume` (wake word
  on) or on a mic tap, and stops it in `onPause`, during phone calls (`AudioManager` mode not
  `MODE_NORMAL`), and on audio-focus loss.
- **Ducking:** while recording a command, the service sets player volume to 0.15 through a custom
  session command (`ACTION_VOICE_DUCK` / `ACTION_VOICE_UNDUCK`). It is restored after the reply,
  or after 8 s at most as a safety net.
- **Threads:** a single `voice` HandlerThread for mic reads and wake inference; STT runs on a
  separate executor so the mic keeps draining. Main-thread work is limited to UI and controller
  calls.
- **Memory:** models load lazily on the first mic tap or when the wake word is enabled, and are
  released when the activity is destroyed.
- **APK:** the existing `noCompress 'onnx'` covers the models.

## 6. Error handling

| Failure | Behaviour |
|---|---|
| A model fails to load / unsupported ABI | Mic button and Voice settings hidden; logged once (debug builds) |
| `RECORD_AUDIO` denied | Mic tap shows a sheet explaining how to enable it in system settings; no repeated prompts; wake word toggle turns itself off |
| Mic busy (`AudioRecord` init fails, call active) | "The mic is busy right now." |
| No speech within 6 s / empty transcript | "Sorry, I didn't catch that." |
| No offline TTS voice | Reply shown on the sheet only |
| Library not loaded yet | "Your library is still loading, try again in a moment." |
| Matched song file deleted since the scan | Skip it; if nothing is left, use the "Not found" reply |
| Any exception inside the voice pipeline | Session returns to IDLE, playback volume restored, no crash |

## 7. Testing

**Unit tests (JVM)**
- `IntentParserTest`: every row of the command table, filler and wake-phrase stripping, and
  "play X songs" fallback order.
- `PhoneticKeyTest`: Kaavaalaa/kavalaa, Rahman/Rehman, Thalapathy/Talapati, Azhagu/Alagu.
- `LibraryVoiceMatcherTest`: a fixture library of about 60 songs including A.R. Rahman credit
  variants, multi-artist credits, OST/junk-tag album names, two albums named "Roja", and a
  "Scene" album. It asserts the top candidate and the confidence band.
- `VoiceCommandExecutorTest` with a fake queue controller: artist queue is shuffled and complete,
  album queue is in track order with shuffle off, `voiceQueueActive` is set, and reply strings
  are correct.
- `VoiceSessionControllerTest` with fake mic, detector, STT and voice: state transitions, duck
  and unduck on every path including errors and timeouts.

**Instrumented tests**
- `MoonshineSttInstrumentedTest`: transcribes 5 bundled CC BY test clips; asserts expected
  keywords appear.
- `OnnxWakeWordDetectorInstrumentedTest`: one positive and one negative clip; inference time per
  hop is under 5 ms.

**Python (`ml/wakeword/tests`)**
- Manifest licence gate, dataset mixing keeps labels correct, ONNX export matches PyTorch
  outputs within 1e-4, and the eval gate script.

**Manual / acceptance**
- The owner records about 50 real commands (quiet room, music playing on the phone speaker,
  headphones) and runs them through a debug "voice test" screen that logs intent, match and
  action. Success criteria 2 and 3 are measured here.

## 8. Out of scope (for this feature)

- Background or screen-off listening, and a microphone foreground service.
- Tamil/Hindi-language commands (a multilingual STT model would be a later upgrade).
- A bundled TTS voice (Kokoro). It needs a non-GPL G2P first; `SwaraVoice` keeps the door open.
- Saving voice queues as playlists, playlist commands ("play my workout playlist"), volume,
  seek and Korokae/Vibe voice commands. These are easy follow-ups on the same `IntentParser`.
- Fine-tuning STT on Indian-accented speech.

## 9. Build order (for the implementation plan)

1. Pure-Java core: `PhoneticKey`, `TagNoise`, `IntentParser`, `LibraryVoiceMatcher`,
   `VoiceCommandExecutor` with tests (usable at once with a typed-text debug entry).
2. Playback integration: `voiceQueueActive`, ducking commands.
3. `MoonshineStt`, `MicSource`, `CommandRecorder`, `SystemTtsVoice`, `VoiceSessionController`,
   mic button and sheet (tap-to-talk works end to end).
4. `ml/wakeword/` pipeline: manifest, data, training, eval gate, export.
5. `OnnxWakeWordDetector`, Settings → Voice, wake-word opt-in.
6. Acceptance run with the 50-command set; tune thresholds.
