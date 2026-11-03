# Pairing and Library Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pair a phone and a laptop once with a QR code, then let the laptop see the phone's library, merge user data both ways, and keep cached copies of songs so they play when the phone is away.

**Architecture:** One versioned protocol, documented in `docs/protocol/` with shared test vectors, implemented twice: in Java inside the Android app (the phone is the server after pairing) and in a Rust library crate `desktop/swara-sync` (the laptop is the client). Transport is mutual TLS 1.3 with pinned certificates; discovery is mDNS plus a Bluetooth LE presence beacon. Desktop playback, CLI and UI are out of scope (sub-projects 2 and 3); this plan ends at a tested sync library.

**Tech Stack:** Java (Room, Android Keystore, NsdManager, BLE advertiser, ZXing embedded), Python (vector generator), Rust (rustls, rcgen, mdns-sd, btleplug, sha2, serde_json).

**Spec:** `docs/superpowers/specs/2026-11-03-cross-platform-sync-and-korokae-v2-design.md` (section 4)

## Global Constraints

- Android code is Java only, no Kotlin; minSdk 24. Commits use Conventional Commits.
- Transport is mutual TLS 1.3 with pinned self-signed certificates. No cloud or relay.
- Discovery is mDNS service `_swara._tcp`; BLE carries a rotating presence beacon only, never audio.
- Sync runs in an opt-in foreground service that can be limited to charging or unmetered Wi-Fi.
- New Android permissions: camera, nearby Wi-Fi devices, Bluetooth scan/advertise/connect, and the network permission for local sockets. The README privacy line becomes "no cloud; the network is used only to sync with devices you have paired".
- Room schema goes from v6 to v7 with a `paired_device` table and a migration test.
- User data merges last-writer-wins per record with a device ID and timestamp; deletes are tombstones. Songs match across devices by content hash first, then title and artist.
- Cache is content-addressed with a disk cap, least-recently-used eviction, and pinned items never evicted. A file is available only after its hash matches. Transfers resume.
- A song never copied shows greyed out with "available when your phone is nearby"; tapping queues a download for the next sync.
- Unit tests: `./gradlew testDebugUnitTest` and `cargo test` stay green at every commit.

## Decisions this plan adds (the spec is silent)

- Pairing token is single-use and expires after 5 minutes.
- After pairing, the phone is the TLS server and the laptop the client. During pairing only, the laptop hosts a temporary TLS endpoint and the phone connects to it, because the QR code lives on the laptop.
- Beacon: 8 bytes, the first 8 bytes of `HMAC-SHA256(beaconKey, u64 big-endian of epochSeconds / 300)`; a receiver accepts the current slot and one either side. `beaconKey` is 32 random bytes made by the phone and sent to the laptop inside the pinned channel.
- Records synced: favorites, playlists, play history, listen events. Wrapped aggregates and recaps are not synced.
- Timestamps more than 5 minutes in the future are clamped to now plus 5 minutes before comparing, to blunt clock skew.
- Index pages hold 500 songs; file chunks are 256 KiB.

## Review Focus

1. Song file replaced after it was hashed (stale hash): rehash when size or modified time change; never serve bytes that do not match the requested hash (Tasks 5, 7).
2. Clock skew between devices: a far-future timestamp must not win forever (Tasks 3, 12).
3. QR token scanned twice, expired, or replayed: rejected (Task 7).
4. Very large library (50,000 songs) and non-ASCII titles (emoji, accents): paging and UTF-8 round-trip (Tasks 2, 7).
5. Range request past end of file, or a file shorter than the index claims: clean error, not a hang (Tasks 7, 16).

## File Structure

| Path | Responsibility |
| --- | --- |
| `docs/protocol/swara-sync-v1.md`, `gen_vectors.py`, `vectors/*.json` | Single source of truth for the wire format |
| `app/.../sync/protocol/` | Frames, messages, QR payload, SAS, beacon token |
| `app/.../sync/merge/` | Pure merge logic |
| `app/.../sync/data/` | Hashing, Room-backed adapters |
| `app/.../sync/net/` | Identity, pinned TLS, server, pairing client |
| `app/.../sync/discovery/` | NSD advertiser, BLE beacon |
| `app/.../sync/service/` | Foreground service and policy |
| `app/.../ui/linked/` | Linked devices screen |
| `desktop/swara-sync/` | Rust client library |

---

### Task 1: Protocol document and test vectors

**Files:**
- Create: `docs/protocol/swara-sync-v1.md`, `docs/protocol/gen_vectors.py`, `docs/protocol/vectors/frames.json`, `pairing.json`, `beacon.json`, `merge.json`

**Interfaces:**
- Produces (wire format, version 1):
  - Frame: `type u8 | length u32 big-endian | payload`, payload at most 1,048,576 bytes.
  - Types: HELLO 0x01, INDEX_REQ 0x02, INDEX_PAGE 0x03, USERDATA_PULL 0x04, USERDATA 0x05, USERDATA_PUSH 0x06, FILE_REQ 0x07, FILE_CHUNK 0x08, FILE_END 0x09, PAIR_COMPLETE 0x10, ERROR 0x7F.
  - Payloads are UTF-8 JSON except FILE_CHUNK, which is `hash[32] | offset u64 BE | bytes`.
  - HELLO `{"protocol":1,"deviceId":str,"name":str}`; INDEX_REQ `{"since":long}`; INDEX_PAGE `{"songs":[{"hash","size","title","artist","album","durationMs","artworkHash"}],"next":long|null}`; FILE_REQ `{"hash":hex,"offset":long,"length":long}`; FILE_END `{"hash":hex,"size":long}`; ERROR `{"code":str,"message":str}`; PAIR_COMPLETE `{"token":hex,"beaconKey":hex}`.
  - QR URI: `swara://pair?v=1&d=<deviceId>&f=<sha256 fingerprint hex>&t=<token hex, 32 chars>&a=<host:port,host:port>&b=<ble uuid>`.
  - SAS: `u32 big-endian of the first 4 bytes of SHA-256(laptopFpBytes | phoneFpBytes | tokenBytes)`, modulo 1,000,000, zero-padded to 6 digits.
  - Merge vectors: input local and remote record lists, `nowMs`, expected `toApplyLocally` and `toSendRemote`, covering LWW, tie broken by the larger `deviceId`, tombstone beating older update, older update losing to tombstone, far-future clamping, and append-only union.

- [ ] **Step 1: Write `swara-sync-v1.md`** with the format above, the pairing flow (laptop hosts temporary TLS, phone connects pinned to the QR fingerprint, both show SAS, phone sends PAIR_COMPLETE, each stores the other's certificate fingerprint), and the sync flow.
- [ ] **Step 2: Write `gen_vectors.py`** using only the standard library (`hashlib`, `hmac`, `struct`, `json`). Each vector file is a list of `{name, input, expected}`.
- [ ] **Step 3: Generate and inspect**

Run: `python3 docs/protocol/gen_vectors.py`
Expected: four JSON files written; `frames.json` includes a FILE_CHUNK vector, a 1,048,576-byte payload vector (hash only, not the bytes), and a title with emoji and accents.

- [ ] **Step 4: Commit**

```bash
git add docs/protocol
git commit -m "docs(sync): add sync protocol v1 and shared test vectors"
```

---

### Task 2: Java protocol codecs

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/sync/protocol/{Frame,FrameType,FrameCodec,FileChunk,PairingPayload,Sas,BeaconToken,ProtocolException}.java`
- Modify: `app/build.gradle` (add `testImplementation 'org.json:json:20240303'` and `android.sourceSets.test.resources.srcDirs += '../docs/protocol'`)
- Test: `app/src/test/java/com/psthetech/swara/sync/ProtocolVectorsTest.java`

**Interfaces:**
- Produces: `Frame(int type, byte[] payload)`; `FrameCodec.encode(Frame) -> byte[]`; `FrameCodec.decode(InputStream) -> Frame` (throws `ProtocolException` on payload over 1,048,576 bytes or truncated stream); `FileChunk.encode(byte[] hash32, long offset, byte[] data, int len) -> byte[]` and `FileChunk.parse(byte[]) -> FileChunk` (fields `hash`, `offset`, `data`); `PairingPayload.parse(String) -> PairingPayload` and `toUri()` (fields `version, deviceId, fingerprintHex, tokenHex, addrs, bleUuid`); `Sas.code(String laptopFpHex, String phoneFpHex, String tokenHex) -> String`; `BeaconToken.compute(byte[] key, long epochSeconds) -> byte[]` (8 bytes) and `BeaconToken.matches(byte[] key, byte[] seen, long nowSeconds) -> boolean`.

- [ ] **Step 1: Write the failing test.** `ProtocolVectorsTest` loads each file under `vectors/` as a class-path resource and asserts per vector: `framesEncodeAndDecodeExactly`, `fileChunkRoundTrips`, `oversizedLengthIsRejected`, `truncatedFrameIsRejected`, `pairingUriParsesAndRoundTrips`, `sasMatchesVectors`, `beaconMatchesCurrentAndAdjacentSlotsOnly` (slot ±1 accepted, ±2 rejected), `emojiAndAccentTitlesRoundTripThroughJson`.
- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*ProtocolVectorsTest'`
Expected: FAIL to compile.

- [ ] **Step 3: Implement the classes above** to satisfy the vectors. `BeaconToken` and `Sas` use `javax.crypto.Mac` and `MessageDigest`.
- [ ] **Step 4: Run the test**

Run: `./gradlew testDebugUnitTest --tests '*ProtocolVectorsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app docs/protocol
git commit -m "feat(sync): add Java protocol codecs verified against shared vectors"
```

---

### Task 3: Java merge engine

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/sync/merge/{SyncRecord,MergeResult,UserDataMerger}.java`
- Test: `app/src/test/java/com/psthetech/swara/sync/MergeVectorsTest.java`

**Interfaces:**
- Produces: `SyncRecord(String kind, String key, String deviceId, long updatedAt, boolean deleted, String dataJson)` where `kind` is `favorite`, `playlist`, `play_history` or `listen_event`; `MergeResult` with `List<SyncRecord> toApplyLocally` and `toSendRemote`; `UserDataMerger.merge(List<SyncRecord> local, List<SyncRecord> remote, long nowMs) -> MergeResult`.
- Rules: for `favorite` and `playlist`, the record with larger `updatedAt` (after clamping to `nowMs + 300_000`) wins; ties go to the lexicographically larger `deviceId`; a tombstone is a record with `deleted=true` and competes the same way. `play_history` and `listen_event` are append-only unions by `key`.

- [ ] **Step 1: Write the failing test** `MergeVectorsTest` running every case in `merge.json`, plus `identicalListsProduceNoChanges` and `farFutureTimestampDoesNotWinForever` (remote record stamped `now + 1 year` loses to a local edit made at `now + 6 minutes`).
- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*MergeVectorsTest'`
Expected: FAIL.

- [ ] **Step 3: Implement `UserDataMerger`** per the rules.
- [ ] **Step 4: Run the test**

Run: `./gradlew testDebugUnitTest --tests '*MergeVectorsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat(sync): add last-writer-wins merge with tombstones"
```

---

### Task 4: Room schema v7

**Files:**
- Create: entities `PairedDevice`, `SongHash`, `SyncTombstone` in `data/db/entity/`; DAOs `PairedDeviceDao`, `SongHashDao`, `SyncTombstoneDao` in `data/db/dao/`
- Modify: `data/db/AppDatabase.java` (version 7, `MIGRATION_6_7`), `entity/Playlist.java` (add `syncId`), `entity/ListenEvent.java` and `entity/PlayHistory.java` (add `syncUid`)
- Test: `app/src/androidTest/java/com/psthetech/swara/MigrationV7Test.java` (model on `MigrationV6Test`), `app/schemas/.../7.json`

**Interfaces:**
- Produces: `PairedDevice(String deviceId @PrimaryKey, String name, String certFingerprintHex, String beaconKeyHex, long pairedAt, long lastSeenAt)`; `SongHash(long songId @PrimaryKey, String hash, long size, long modifiedAt)`; `SyncTombstone(String kind, String key, String deviceId, long deletedAt)` with composite primary key `(kind, key)`; `Playlist.syncId` (String, UUID); `ListenEvent.syncUid` and `PlayHistory.syncUid` (String, unique, UUID). `PairedDeviceDao`: `insert`, `all()`, `byId(String)`, `delete(String)`, `touch(String id, long lastSeen)`.
- Migration backfills a random UUID into every existing `playlists`, `listen_events` and `play_history` row.

- [ ] **Step 1: Write the failing instrumented test** `MigrationV7Test`: `migrate6To7KeepsRowsAndBackfillsUuids` (insert 2 playlists, 3 listen events, 2 history rows at v6; after migration all have non-empty distinct UUIDs and counts unchanged) and `newTablesExistAfterMigration`.
- [ ] **Step 2: Run to verify failure**

Run: `./gradlew connectedDebugAndroidTest --tests '*MigrationV7Test'` (needs a device or emulator)
Expected: FAIL.

- [ ] **Step 3: Implement entities, DAOs and `MIGRATION_6_7`**; register the migration in `addMigrations`; set `version = 7`; update the schema history comment in `AppDatabase`. New rows must get UUIDs at insert time (default in the entity constructors or DAO wrappers).
- [ ] **Step 4: Run the migration test and `./gradlew testDebugUnitTest`**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(sync): add Room schema v7 for paired devices, song hashes and tombstones"
```

---

### Task 5: Song hashing

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/sync/data/SongHasher.java`
- Test: `app/src/test/java/com/psthetech/swara/sync/SongHasherTest.java`

**Interfaces:**
- Consumes: `SongHashDao` from Task 4.
- Produces: `SongHasher(SongHashDao dao, StreamOpener opener)` with `interface StreamOpener { InputStream open(long songId) throws IOException; long size(long songId); long modifiedAt(long songId); }`; `hashFor(long songId) -> String` (SHA-256 hex, cached by `(size, modifiedAt)`); `songIdForHash(String) -> Long` (null if unknown).

- [ ] **Step 1: Write the failing test** with in-memory fakes: `hashesKnownBytes` (SHA-256 of "abc" matches the standard value), `cachedWhenSizeAndMtimeUnchanged` (opener called once for two calls), `rehashesWhenModifiedTimeChanges`, `rehashesWhenSizeChanges`, `unknownHashReturnsNull`.
- [ ] **Step 2: Run to verify failure**, then implement (streaming 64 KiB reads, no whole-file buffer), then run to verify pass.

Run: `./gradlew testDebugUnitTest --tests '*SongHasherTest'`
Expected: FAIL, then PASS (5 tests).

- [ ] **Step 3: Commit**

```bash
git add app/src
git commit -m "feat(sync): add content hasher with stale-hash detection"
```

---

### Task 6: Room-backed user data adapter

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/sync/data/UserDataAdapter.java`
- Test: `app/src/test/java/com/psthetech/swara/sync/UserDataAdapterTest.java`

**Interfaces:**
- Consumes: `UserDataMerger`, `SongHasher`, the existing DAOs.
- Produces: `UserDataAdapter(AppDatabase db, SongHasher hasher)`; `export(long since) -> List<SyncRecord>` (favorites and playlists keyed by content hash and `syncId`, history and events keyed by `syncUid`; tombstones from `sync_tombstone`); `apply(List<SyncRecord>)` writes remote winners into Room in one transaction. A record naming a song hash that does not exist on this device is skipped and counted in `apply`'s return value (`int skipped`).
- Local deletes of favorites and playlists (existing repositories) now also write a `SyncTombstone`; modify `FavoritesRepository` and `PlaylistRepository`.

- [ ] **Step 1: Write the failing test** with an in-memory Room database (Robolectric is not in the project; use the same approach as existing DAO tests, or an instrumented test if none exists): `exportsFavoriteByHash`, `applyInsertsRemoteFavoriteWhenSongKnown`, `applySkipsFavoriteForUnknownHash`, `localFavoriteDeleteWritesTombstone`, `applyTombstoneRemovesFavorite`.
- [ ] **Step 2: Run to verify failure**, implement, run to verify pass.

Run: `./gradlew testDebugUnitTest --tests '*UserDataAdapterTest'` (or `connectedDebugAndroidTest` if instrumented)
Expected: FAIL, then PASS (5 tests).

- [ ] **Step 3: Commit**

```bash
git add app/src
git commit -m "feat(sync): bridge Room user data to sync records"
```

---

### Task 7: Identity, pinned TLS, server and pairing

**Files:**
- Create: `sync/net/{DeviceIdentity,PinnedTrustManager,TlsContexts,SyncServer,SyncSession,PairingClient,PairingTokenGuard}.java`, test resources `app/src/test/resources/tls/{a,b}.pem` and `.key` (two throwaway self-signed certs)
- Test: `app/src/test/java/com/psthetech/swara/sync/{PinnedTrustManagerTest,SyncServerTest,PairingTest}.java`

**Interfaces:**
- Produces: `DeviceIdentity` (Android Keystore EC P-256 key; the self-signed certificate comes from `KeyGenParameterSpec`) with `keyManagers() -> KeyManager[]`, `fingerprintHex() -> String`; `PinnedTrustManager(Set<String> fingerprintHexes) extends X509ExtendedTrustManager` (accepts a chain only if the leaf's SHA-256 is pinned); `TlsContexts.server(KeyManager[], Set<String> pinned) -> SSLContext` and `client(...)`, both TLS 1.3 only; `SyncServer(SSLContext, LibraryIndexSource, FileSource, UserDataAdapter)` with `start(int port) -> int` (returns bound port), `stop()`; `interface LibraryIndexSource { List<IndexEntry> page(long since, int limit); }` and `interface FileSource { long size(String hash); InputStream open(String hash, long offset) throws IOException; }` (both must return not-found for a hash whose bytes no longer match); `PairingTokenGuard(Clock)` with `issue() -> String`, `consume(String token) -> boolean` (single-use, valid 5 minutes); `PairingClient.pair(PairingPayload, DeviceIdentity, Confirm) -> PairedDevice` where `interface Confirm { boolean confirmSas(String code); }`.
- Because `AndroidKeystore` is unavailable in JVM tests, `SyncServer` and `PairingClient` take an `SSLContext`/`KeyManager[]`, and tests build them from the PEM resources.

- [ ] **Step 1: Write the failing tests:**
  - `PinnedTrustManagerTest`: `acceptsPinnedLeaf`, `rejectsUnpinnedLeaf`, `rejectsEmptyPinSet`.
  - `SyncServerTest` (loopback, two test certs): `unpinnedClientIsRefused`, `indexPagesOf500WithNextCursor` (fake source of 1,200 songs gives pages of 500, 500, 200), `fileRequestStreamsChunksAndEnd`, `fileRequestWithOffsetResumes`, `rangePastEndReturnsErrorFrame`, `fileWhoseBytesChangedReturnsNotFound`, `userDataPullReturnsRecords`.
  - `PairingTest`: `tokenIsSingleUse`, `tokenExpiresAfterFiveMinutes`, `sasMatchesOnBothSides`, `rejectedSasStoresNothing`, `pairedDeviceStoresPeerFingerprint`.
- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*sync*'`
Expected: FAIL.

- [ ] **Step 3: Implement.** `SyncSession` is a frame loop over one TLS socket handling HELLO, INDEX_REQ, USERDATA_PULL, USERDATA_PUSH and FILE_REQ with 256 KiB chunks; any protocol error sends an ERROR frame and closes. Merge incoming USERDATA_PUSH with `UserDataMerger` through `UserDataAdapter`.
- [ ] **Step 4: Run the tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(sync): add pinned TLS server and pairing client"
```

---

### Task 8: Discovery (mDNS and BLE beacon)

**Files:**
- Create: `app/src/main/java/com/psthetech/swara/sync/discovery/{NsdAdvertiser,BleBeacon}.java`

**Interfaces:**
- Produces: `NsdAdvertiser(Context)` with `start(int port, String deviceId)` and `stop()` registering `_swara._tcp`; `BleBeacon(Context, byte[] beaconKey, String serviceUuid)` with `start()` and `stop()`, re-advertising `BeaconToken.compute` every 300 seconds (the slot boundary) and carrying the phone's current IPv4 address in the scan response as a fallback when mDNS is blocked.

- [ ] **Step 1: Implement both classes** using `NsdManager` and `BluetoothLeAdvertiser`. They hold no logic beyond platform calls; the token maths is already tested in Task 2.
- [ ] **Step 2: Manual check on a device**

Run: `./gradlew installDebug`, start the advertiser from a debug entry point, then `avahi-browse -rt _swara._tcp` on the laptop and a BLE scanner app.
Expected: the service appears with the device ID, and the BLE advertisement carries the 8-byte token that changes at the 5-minute boundary.

- [ ] **Step 3: Commit**

```bash
git add app/src
git commit -m "feat(sync): advertise over mDNS and BLE presence beacon"
```

---

### Task 9: Foreground sync service, permissions and privacy text

**Files:**
- Create: `sync/service/{SyncService,SyncPolicy}.java`, `data/preference/SyncPreferences.java`
- Modify: `app/src/main/AndroidManifest.xml`, `README.md`
- Test: `app/src/test/java/com/psthetech/swara/sync/SyncPolicyTest.java`

**Interfaces:**
- Produces: `SyncPolicy.allows(boolean charging, boolean unmetered, boolean requireCharging, boolean requireUnmetered) -> boolean`; `SyncPreferences` with `enabled(Context)` (default false), `requireCharging`, `requireUnmetered`; `SyncService` (foreground, type `connectedDevice`) that, when enabled and the policy allows, starts `SyncServer`, `NsdAdvertiser` and `BleBeacon` and stops them otherwise.
- Manifest: add permissions `CAMERA`, `NEARBY_WIFI_DEVICES` (`usesPermissionFlags="neverForLocation"`), `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `INTERNET`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `CHANGE_WIFI_MULTICAST_STATE`; remove the `ACCESS_NETWORK_STATE` removal rule and replace its comment; declare `SyncService` non-exported.

- [ ] **Step 1: Write the failing test** `SyncPolicyTest`: `allowsWhenNoConstraints`, `blocksWhenChargingRequiredAndNotCharging`, `blocksWhenUnmeteredRequiredAndMetered`, `allowsWhenAllRequiredConditionsMet`.
- [ ] **Step 2: Run to verify failure**, implement, run to verify pass.

Run: `./gradlew testDebugUnitTest --tests '*SyncPolicyTest'`
Expected: FAIL, then PASS (4 tests).

- [ ] **Step 3: Implement the service and manifest changes; update the README** privacy statement and Permissions table to the wording in Global Constraints.
- [ ] **Step 4: Build and lint**

Run: `./gradlew assembleDebug lint`
Expected: BUILD SUCCESSFUL with no new lint errors.

- [ ] **Step 5: Commit**

```bash
git add app README.md
git commit -m "feat(sync): add opt-in foreground sync service and permissions"
```

---

### Task 10: Linked devices screen

**Files:**
- Create: `ui/linked/{LinkedDevicesFragment,ScanQrFragment,SasConfirmDialog}.java`, layouts, `PairedDeviceRepository.java` in `data/repository/`
- Modify: `gradle/libs.versions.toml` and `app/build.gradle` (add `com.journeyapps:zxing-android-embedded`, which works offline), `SettingsFragment.java` (entry "Linked devices"), navigation graph
- Test: `app/src/test/java/com/psthetech/swara/sync/PairedDeviceRepositoryTest.java`

**Interfaces:**
- Produces: `PairedDeviceRepository` with `list() -> List<PairedDevice>`, `revoke(String deviceId)` (deletes the row, clears its pinned fingerprint from the server's trust set, closes any open session), `pinnedFingerprints() -> Set<String>`.

- [ ] **Step 1: Write the failing test** `PairedDeviceRepositoryTest`: `revokeRemovesFingerprintFromPinnedSet`, `listIsOrderedByLastSeenDescending`.
- [ ] **Step 2: Run to verify failure**, implement the repository, run to verify pass.

Run: `./gradlew testDebugUnitTest --tests '*PairedDeviceRepositoryTest'`
Expected: FAIL, then PASS.

- [ ] **Step 3: Build the screens.** The list shows name, last seen and a Revoke button; "Link a device" asks for camera permission, scans with ZXing, parses with `PairingPayload.parse`, calls `PairingClient.pair`, and shows the SAS code in `SasConfirmDialog`. Include a "Sync now" button that starts `SyncService`.
- [ ] **Step 4: Manual check** (after Task 13 gives a laptop-side pairing host): scan the laptop's QR, confirm both screens show the same six digits, see the device in the list, revoke it, and confirm the laptop's next connection is refused.
- [ ] **Step 5: Commit**

```bash
git add app gradle
git commit -m "feat(sync): add linked devices screen with QR pairing"
```

---

### Task 11: Rust crate, protocol codecs and CI

**Files:**
- Create: `desktop/Cargo.toml` (workspace), `desktop/swara-sync/Cargo.toml`, `src/{lib,protocol,pairing}.rs`, `desktop/swara-sync/tests/vectors.rs`, `.github/workflows/desktop.yml`

**Interfaces:**
- Produces (module `protocol`): `Frame { ty: u8, payload: Vec<u8> }`; `encode(&Frame) -> Vec<u8>`; `decode<R: Read>(&mut R) -> Result<Frame, ProtocolError>`; `FileChunk { hash: [u8; 32], offset: u64, data: Vec<u8> }` with `encode` and `parse`; `PairingPayload::parse(&str) -> Result<PairingPayload>` and `to_uri`; `sas_code(laptop_fp: &[u8], phone_fp: &[u8], token: &[u8]) -> String`; `beacon_token(key: &[u8], epoch_secs: u64) -> [u8; 8]` and `beacon_matches(key, seen, now_secs) -> bool`.

- [ ] **Step 1: Write the failing test** `tests/vectors.rs` running every vector in `../../docs/protocol/vectors/` with the same case names as `ProtocolVectorsTest`.
- [ ] **Step 2: Run to verify failure**

Run: `cd desktop && cargo test -p swara-sync --test vectors`
Expected: FAIL to compile.

- [ ] **Step 3: Implement the module**, and add `desktop.yml` running `cargo test --workspace` on Ubuntu.
- [ ] **Step 4: Run the test**

Run: `cd desktop && cargo test -p swara-sync --test vectors`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop .github/workflows/desktop.yml
git commit -m "feat(desktop): add swara-sync crate with protocol codecs"
```

---

### Task 12: Rust merge

**Files:**
- Create: `desktop/swara-sync/src/merge.rs`, `desktop/swara-sync/tests/merge_vectors.rs`

**Interfaces:**
- Produces: `SyncRecord { kind: String, key: String, device_id: String, updated_at: i64, deleted: bool, data_json: String }`; `merge(local: &[SyncRecord], remote: &[SyncRecord], now_ms: i64) -> MergeResult { to_apply_locally, to_send_remote }`, same rules as Task 3.

- [ ] **Step 1: Write the failing test** running `merge.json`, plus `far_future_timestamp_does_not_win_forever`.
- [ ] **Step 2: Run to verify failure**, implement, run to verify pass.

Run: `cd desktop && cargo test -p swara-sync --test merge_vectors`
Expected: FAIL, then PASS.

- [ ] **Step 3: Commit**

```bash
git add desktop
git commit -m "feat(desktop): add merge matching shared vectors"
```

---

### Task 13: Rust pinned TLS and pairing host

**Files:**
- Create: `desktop/swara-sync/src/{identity,tls,pairing_host}.rs`, `desktop/swara-sync/tests/pairing.rs`

**Interfaces:**
- Produces: `Identity::generate() -> Identity` (rcgen self-signed EC P-256) with `fingerprint_hex()`, `cert_der()`, `key_der()`; `PinnedVerifier::new(HashSet<String>)` implementing both rustls `ServerCertVerifier` and `ClientCertVerifier`; `PairingHost::start(identity, addr) -> PairingHost` with `qr_uri() -> String`, `await_phone(confirm: impl FnOnce(&str) -> bool) -> Result<PairedPhone>` where `PairedPhone { device_id, name, cert_fingerprint_hex, beacon_key: [u8; 32] }`.

- [ ] **Step 1: Write the failing tests** with a Rust test client acting as the phone: `qr_uri_contains_fingerprint_and_token`, `pairing_completes_when_sas_matches`, `rejected_sas_returns_error_and_stores_nothing`, `token_is_single_use`, `token_expires_after_five_minutes` (injectable clock), `unpinned_client_is_refused_after_pairing`.
- [ ] **Step 2: Run to verify failure**, implement, run to verify pass.

Run: `cd desktop && cargo test -p swara-sync --test pairing`
Expected: FAIL, then PASS (6 tests).

- [ ] **Step 3: Commit**

```bash
git add desktop
git commit -m "feat(desktop): add pinned TLS and QR pairing host"
```

---

### Task 14: Rust discovery

**Files:**
- Create: `desktop/swara-sync/src/discovery.rs`

**Interfaces:**
- Produces: `discover_mdns(timeout: Duration, wanted_device_id: &str) -> Option<SocketAddr>` (browses `_swara._tcp`); `BeaconWatcher::new(beacon_key: [u8; 32], ble_uuid: Uuid)` with `next_seen() -> Option<BeaconSighting { ip: Option<Ipv4Addr> }>` using btleplug, accepting only tokens that pass `beacon_matches`.

- [ ] **Step 1: Write the failing unit test** for the pure part: `sighting_with_wrong_token_is_ignored` and `sighting_with_adjacent_slot_token_is_accepted`, against a function `accept_sighting(key, advertised, now) -> bool`.
- [ ] **Step 2: Run to verify failure**, implement `accept_sighting`, mDNS and BLE glue, run to verify pass.

Run: `cd desktop && cargo test -p swara-sync discovery`
Expected: FAIL, then PASS.

- [ ] **Step 3: Manual check:** with the phone advertising (Task 8), `discover_mdns` returns its address, and a BLE sighting is reported within about 10 seconds.
- [ ] **Step 4: Commit**

```bash
git add desktop
git commit -m "feat(desktop): add mDNS discovery and BLE beacon watcher"
```

---

### Task 15: Rust cache store

**Files:**
- Create: `desktop/swara-sync/src/cache.rs`, `desktop/swara-sync/tests/cache.rs`

**Interfaces:**
- Produces: `Cache::open(dir: &Path, cap_bytes: u64) -> Cache`; `begin(hash: &str, size: u64) -> PartialFile` (resumable, `offset()` reports bytes already on disk); `PartialFile::write(&mut self, bytes: &[u8])`; `PartialFile::finish(self) -> Result<(), CacheError>` (verifies SHA-256, else deletes the partial file and returns `HashMismatch`); `contains(hash) -> bool`; `open(hash) -> Option<File>` (marks recently used); `pin(hash)` and `unpin(hash)`; `evict_to_cap()` removes least-recently-used unpinned files only.

- [ ] **Step 1: Write the failing tests:** `finish_accepts_matching_hash`, `finish_rejects_and_deletes_on_mismatch`, `resume_continues_from_existing_partial`, `duplicate_hash_is_stored_once`, `eviction_removes_least_recently_used`, `pinned_files_are_never_evicted`, `eviction_stops_when_only_pinned_files_remain_over_cap`.
- [ ] **Step 2: Run to verify failure**, implement (content-addressed layout `dir/ab/abcdef….audio`, `.part` suffix for partials), run to verify pass.

Run: `cd desktop && cargo test -p swara-sync --test cache`
Expected: FAIL, then PASS (7 tests).

- [ ] **Step 3: Commit**

```bash
git add desktop
git commit -m "feat(desktop): add content-addressed cache with pinning and eviction"
```

---

### Task 16: Rust sync client and end-to-end test

**Files:**
- Create: `desktop/swara-sync/src/{client,library}.rs`, `desktop/swara-sync/tests/e2e.rs`, `desktop/swara-sync/tests/support/fake_phone.rs`

**Interfaces:**
- Consumes: Tasks 11 to 15.
- Produces: `Client::connect(addr, identity, pinned_phone_fp) -> Client`; `index(since) -> Vec<IndexEntry>` (follows `next` cursors); `sync_user_data(&mut self, local: &[SyncRecord], now_ms) -> MergeResult`; `fetch(hash) -> FetchOutcome` (`Cached`, `Fetched`, `Unavailable`) using range requests and resuming from `PartialFile::offset()`; `open_stream(hash) -> impl Read` that yields bytes as they arrive and finishes when the file does (play-while-copying); `Library::availability(hash) -> Availability { Cached, Remote, Unavailable }` and `Library::queue_for_next_sync(hash)`; `retry_with_backoff` with delays 1, 2, 4, 8 and 16 seconds, capped at 60.
- The fake phone is a test-only server in Rust speaking the protocol over rustls with pinned certs.

- [ ] **Step 1: Write the failing end-to-end tests:**
  - `pair_then_index_then_play_while_copying` (full flow: pairing host, fake phone, index with 1,200 songs in three pages, `open_stream` yields the first bytes before the fake phone has sent the whole file).
  - `connection_drop_resumes_from_offset`.
  - `hash_mismatch_discards_file_and_reports_error`.
  - `phone_disappears_mid_transfer_leaves_partial_and_retries`.
  - `unavailable_song_is_queued_and_fetched_at_next_sync`.
  - `range_past_end_returns_error_not_hang` (5-second test timeout).
  - `revoked_phone_cannot_connect` (pinned set emptied).
  - `user_data_merge_round_trips_with_tombstone`.
- [ ] **Step 2: Run to verify failure**

Run: `cd desktop && cargo test -p swara-sync --test e2e`
Expected: FAIL.

- [ ] **Step 3: Implement `client.rs` and `library.rs`.**
- [ ] **Step 4: Run all desktop tests**

Run: `cd desktop && cargo test --workspace`
Expected: PASS.

- [ ] **Step 5: Interop check on real hardware.** Start `SyncService` on a phone, pair with a small Rust example (`cargo run -p swara-sync --example pair_and_sync`, added in this task, prints the QR URI and the SAS), and confirm the laptop fetches one song whose SHA-256 equals the phone's. This is the only test that exercises the Java and Rust implementations against each other.
- [ ] **Step 6: Commit**

```bash
git add desktop
git commit -m "feat(desktop): add sync client, library availability and end-to-end tests"
```
