# Cross-platform sync and Korokae v2 — design

Date: 2026-11-03
Status: draft, awaiting review

## 1. Goal

Make Swara usable on a laptop as well as on the phone. The laptop shows and plays the phone's library, including when the phone is away. Pair once, then the devices reconnect on their own. Separately, improve the Korokae vocal-removal model, and add a user-adjustable vocal-reduction strength.

## 2. Decomposition

The request covers four independent subsystems. Each gets its own spec, plan and build cycle.

| # | Sub-project | Covered by this spec |
| --- | --- | --- |
| 1 | Pairing and library sync (Android + shared protocol) | Yes |
| 2 | Desktop core and CLI (Tauri + Rust core) | Decisions only; own spec later |
| 3 | Desktop UI (Liquid Glass look, 8 themes, app features) | Decisions only; own spec later |
| 4 | Korokae v2 and the strength slider | Yes |

Build order: 1, then 2, then 3, with 4 in parallel. Sub-project 4 does not depend on 1–3 until the desktop adopts the ONNX build.

## 3. Decisions already made

- **Song availability.** The laptop keeps copies of songs. Songs are copied over local Wi-Fi while the phone is nearby, then play offline. A song never copied is unavailable while the phone is away. There is no cloud or relay.
- **Desktop stack.** Tauri with a Rust core. The core handles playback, sync and the CLI. The UI is web-based. Korokae runs through ONNX Runtime.
- **Pairing.** A QR code shown on the laptop and scanned by the phone, like WhatsApp Web.
- **Transport.** Wi-Fi carries the data. Bluetooth LE carries presence only; it is too slow for audio at roughly 100 KB/s.

## 4. Sub-project 1: pairing and sync

### 4.1 Pairing and trust

- The laptop generates a self-signed certificate. The QR code holds the certificate fingerprint, a one-time token, LAN address hints, and a BLE service ID.
- The phone scans the QR code and connects. Both devices show a short confirmation code, which the user checks matches. This blocks a man-in-the-middle.
- Each side then stores the other's certificate. The phone stores it in a new Room table, `paired_device` (schema v6 to v7, with a migration test like the existing `MigrationV6Test`).
- Settings gets a "Linked devices" list. Revoking a device deletes its stored certificate; the phone then refuses its connections. The laptop keeps its cached audio files and drops the library index.

### 4.2 Transport

- Mutual TLS 1.3 with pinned certificates. The laptop uses rustls, and the phone uses the Android TLS stack.
- Discovery is mDNS (`_swara._tcp`). If the network blocks mDNS, the phone's BLE beacon carries its current IP as a fallback.
- The BLE beacon is a rotating presence signal. When the laptop sees it, it starts a sync automatically.
- The phone runs sync in an opt-in foreground service. It can be limited to charging or unmetered Wi-Fi.
- New Android permissions: camera, nearby Wi-Fi devices, Bluetooth scan/advertise/connect, and the network permission for local sockets. The README privacy statement changes to "no cloud; the network is used only to sync with devices you have paired".

### 4.3 What syncs

- **Library index** (phone to laptop, read-only): song ID, title, artist, album, duration, size, content hash, artwork hash.
- **User data** (two-way): playlists, favorites, play history and listen events.
  - Each record carries a device ID and a timestamp, and merges last-writer-wins.
  - Deletes are tombstones so they are not resurrected.
  - Songs match across devices by content hash first, then by title and artist, like the existing Wrapped backup.
- **Audio files** (phone to laptop, on demand): see 4.4.

### 4.4 Getting songs onto the laptop

- **Play-while-copying.** If a song is not cached and the phone is reachable, the laptop fetches it with range requests, starts playing after the first few seconds, and writes to the cache as it goes.
- **Keep offline.** A song, album, playlist or the favorites can be pinned. Pinned items download in the background whenever the phone is nearby.
- **Cache.** Files are stored by content hash, so duplicates never copy twice. A disk cap applies with least-recently-used eviction. Pinned items are never evicted.
- **Integrity.** Transfers resume after a dropped connection. A file is marked available only after its hash matches.
- **Phone away, song not cached.** The song shows greyed out with "available when your phone is nearby". Tapping it queues a download for the next sync.
- **Retry.** If the phone is asleep, the laptop retries with backoff, and the BLE beacon wakes it.

### 4.5 One protocol, two implementations

The protocol is versioned and documented in one place. The Java and Rust implementations both run against shared test vectors so they cannot drift.

### 4.6 Testing

- Protocol test vectors that both implementations must pass.
- Merge tests covering conflicts and tombstones.
- Resume, hash-mismatch and phone-disappears-mid-transfer tests.
- An end-to-end test against a fake phone server on localhost.
- Migration test for schema v7.

## 5. Sub-project 4: Korokae v2

### 5.1 Baseline (measured)

Korokae v1 reaches 8.95 dB median accompaniment SDR on the MUSDB18 test set, against a 4.50 dB baseline of the unprocessed mixture. The model is 4.2 MB. The training log shows it plateauing at 8.8–8.9 dB from step 8000, so more steps will not help.

### 5.2 Why v1 is limited

1. It sees only the mid channel, so it gets no stereo cue. Vocals sit in the stereo centre, which makes the side channel (L−R) the best clue.
2. It has no lookahead.
3. The loss rewards matching the accompaniment only. Nothing penalises leftover vocal.
4. Augmentation is gain, channel flips and remixing. It has no compression artifacts, reverb or EQ.
5. Evaluation is a single SDR number.

### 5.3 v2 changes

- **Input.** Log-magnitudes of mid and side. The network predicts a separate mask per output channel.
- **Two sizes, one training script.**
  - **Standard:** the phone model, with a similar compute budget to v1.
  - **High:** a bigger GRU with 2 frames of lookahead (about 23 ms extra delay at a 512-sample hop), for the laptop.
- **Loss.** Keep the compressed-magnitude loss. Add a vocal-residual term and a multi-resolution STFT loss on the waveform.
- **Data.** Add MP3/AAC round-trips, vocal reverb and EQ, and more all-instrumental clips so instrumentals pass through untouched. Any extra dataset needs a licence check against `MODEL_LICENSE.md`.
- **Evaluation.** Report accompaniment SDR, vocal attenuation in dB, and an instrumental-passthrough check. Keep a fixed A/B listening set.

### 5.4 Acceptance criteria

- Standard: at least +1.5 dB SDR over v1 and better vocal attenuation, within v1's on-device real-time budget.
- High: beats Standard on both metrics.
- These are hypotheses until trained. A model ships only if it beats v1 on every metric. Otherwise v1 stays the default.

### 5.5 Format and rollout

- New `KRK2` weight format with a version header and a layer config. The Java engine reads both KRK1 and KRK2. v1 stays bundled as the fallback.
- The parity test in `app/src/test` is extended to cover KRK2.
- The desktop uses an ONNX export with streaming state in and out. It is validated against PyTorch.
- Training runs on the local RTX 5070, about an hour per run.

### 5.6 Vocal-reduction strength slider

- A "Vocal reduction" control from 0 to 100 percent, default 100, stored in preferences and shown with the Korokae controls.
- It reuses the engine's existing dry/wet control, `KorokaeEngine.setMix`. The engine already time-aligns the dry signal and ramps changes per hop, so the slider adds no latency and no clicks. The processor sets the target mix to `enabled ? strength : 0`.
- At 0 percent the audio passes through unchanged. At 100 percent it behaves exactly as the model outputs.
- It is a pure engine change on the Java side and the Rust side, and it does not depend on retraining. It can ship before v2.
- Tests: strength 0 is a delayed passthrough, strength 1 matches the parity fixture, and values between lie between the two.

## 6. Out of scope

- Internet relay or cloud sync.
- Lyric detection. Korokae stays an always-on separator.
- iOS.
- Detailed design of the desktop core, CLI and UI. These are sub-projects 2 and 3.

## 7. Risks

- **BLE background limits on Android.** Android restricts background Bluetooth scanning and foreground services. Mitigation: the opt-in foreground service, plus a manual "sync now" button.
- **Networks that isolate clients.** Some Wi-Fi networks block device-to-device traffic. Mitigation: show a clear message; no fallback is built in this version.
- **Model targets may not be met.** The accuracy gains are unproven. Mitigation: the ship gate in 5.4.
- **Licence.** The model weights stay non-commercial while trained on MUSDB18.
