# Swara Wrapped Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Monthly and yearly listening recaps plus an always-available stats screen. They are shown as Rhythm Pop story slides that can be shared as PNG images or exported as a silent MP4.

**Architecture:**
- A `ListenTracker` in the playback service records real playing time into never-trimmed daily aggregates (Room v6).
- Pure-Java `WrappedStatsEngine`, `Personality` and `SlidePlan` turn those rows into `WrappedStats`.
- One `SlideRenderer` draws every slide. The story screen, the PNG share and the MP4 encoder all use it.
- A daily WorkManager job stores frozen recaps and sends a notification.

**Tech Stack:** Java 11, Room 2.6.1, Media3 1.4.1, WorkManager 2.9.1, Android MediaCodec/MediaMuxer, org.json, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-17-swara-wrapped-design.md`

## Global Constraints
- No network, no new third-party libraries, Java only, no Room access on the main thread.
- Every user-facing string goes in `res/values/strings.xml`. No health claims: mood words describe the music.
- The video is silent: 1080×1920, 30 fps, H.264, about 3 s per slide. Fallback 720×1280. Saved to `Movies/Swara`.
- A recap or story needs at least 30 minutes (1 800 000 ms) in the period.
- The Wrapped tables are never trimmed or pruned. Song deletion leaves them untouched.
- Days are `yyyymmdd` ints in device local time at listen time.
- Slide animations last 600–900 ms with ease-out. Stories auto-advance after 6 s. The canvas is 1080×1920 logical.
- One movie = 150 minutes, for the minutes comparison.
- Mood buckets, evaluated in this order: valence < 0.35 → SAD; energy ≥ 0.70 → ENERGETIC; valence ≥ 0.60 → HAPPY; otherwise CALM. The mood is resolved with `MoodResolver`.
- Personality rules are evaluated in order, and the first match wins:
  1. Night Owl: ≥ 40% of playedMs between 22:00 and 03:59.
  2. Early Riser: ≥ 40% between 05:00 and 08:59.
  3. Mood Lifter: ≥ 5 Vibe starts.
  4. Loyalist: the top song is ≥ 15% of playedMs.
  5. Explorer: ≥ 30% of distinct songs are new in the period.
  6. Energy Seeker: ENERGETIC is ≥ 40% of mood-known minutes.
  7. Balanced Listener: none of the above.
- Rediscovered: the song with the most playedMs in the period that was played before the period but had no plays in the 60 days before it started.
- The tracker checkpoints every 30 s while playing.
- Notification channel "Wrapped" (low importance). The POST_NOTIFICATIONS request happens once, only after the first recap banner has been seen.
- The banner shows for 14 days or until dismissed.

## Review Focus
1. **Upgrade with real data.** Backfill must not double-count when it runs again after a crash. A one-shot flag is set only on success, and the inserts are idempotent: backfill only writes days with no existing `listen_daily` rows for that song. Pinned in Task 3.
2. **Midnight and month boundaries while playing.** A song that plays across midnight splits its playedMs into the correct two days and hours. Pinned in Task 2.
3. **Ties in top lists and the busiest weekday.** Results must be deterministic, with ties broken by songId and then weekday order (Mon first). Pinned in Task 4.
4. **Video export on a device without a 1080p encoder, or with full storage.** It falls back or fails cleanly, and partial files are deleted. Pinned in Task 7.
5. **Story opened from a notification after the process was killed.** The story loads from the stored recap with no live service needed. Pinned in Task 8.

---

## File Structure
```
app/src/main/java/com/psthetech/swara/wrapped/
  Day.java                   pure: yyyymmdd helpers (of(epochMs, TimeZone), weekday, addDays, monthStart/End)
  Period.java                pure: start/end yyyymmdd, kind MONTH/YEAR/ALL, key(), label
  DailyRow.java, HourlyRow.java, SongInfo.java       pure value types used by the engine
  ListenClock.java           pure: playing-time accounting with injectable clock, splits across hours/days
  ListenTracker.java         Android: owns ListenClock, writes Room (DB executor)
  HistoryBackfill.java       pure converter + Android runner
  WrappedStats.java          immutable value + JSON (org.json)
  WrappedStatsEngine.java    pure compute
  MoodBucket.java            pure enum + of(double[] mood)
  Personality.java           pure enum + of(WrappedStats)
  SlideType.java, SlidePlan.java   pure
  render/RhythmPopPalette.java, render/SlideRenderer.java
  export/SlideImageExporter.java, export/RecapVideoExporter.java
  ui/WrappedStoryActivity.java, ui/StoryView.java, ui/StatsFragment.java
  RecapWorker.java, WrappedNotifier.java, RecapRepository.java
data/db/entity/ListenDaily.java, ListenHourly.java, SongSnapshot.java, Recap.java
data/db/dao/WrappedDao.java
```

---

### Task 1: Room v6 schema
**Files:**
- Create: the 4 entities and `WrappedDao`.
- Modify: `data/db/AppDatabase.java` (version 6, `MIGRATION_5_6`, register `wrappedDao()`, add it to `addMigrations`).
- Test: `app/src/androidTest/java/com/psthetech/swara/MigrationV6Test.java`.

**Interfaces (produces):**
- Entities as in spec §2. `listen_daily` has PK (day, songId), INDEX(songId), and `vibeStarts INTEGER NOT NULL DEFAULT 0`.
- `WrappedDao`, with the upserts adding to existing rows (implement each as a @Transaction default method: read, add, write):
  - `void addDaily(int day, long songId, long playedMs, int plays, int finishes, int skips, int repeats, int vibeStarts)`
  - `void addHourly(int day, int hour, long playedMs)`
  - `void upsertSnapshot(SongSnapshot s)`, which keeps the existing `firstHeardDay` if the row already exists.
  - `List<ListenDaily> daily(int fromDay, int toDay)`
  - `List<ListenHourly> hourly(int fromDay, int toDay)`
  - `List<ListenDaily> dailyBefore(int day)`
  - `List<SongSnapshot> snapshots()`
  - `int countDailyFor(long songId, int day)`
  - `long totalPlayedMs(int fromDay, int toDay)`
  - Recap: `void upsertRecap(Recap r)`, `Recap recap(String key)`, `LiveData<Recap> latestUndismissedLive(int sinceCreatedDay)`, `void markSeen(String key)`, `void dismissBanner(String key)`.
- [ ] **Step 1:** Write `MigrationV6Test.migrate5To6_keepsDataAndCreatesWrappedTables`. Create v5 with a playlist, a song_mood row and a mood_override row, migrate, assert they are kept, then insert one row into each new table.
- [ ] **Step 2:** Run it on the device (adb `--user 0` instrument). Expected: FAIL (no MIGRATION_5_6).
- [ ] **Step 3:** Implement. Copy the CREATE SQL from the exported `app/schemas/.../6.json`.
- [ ] **Step 4:** The test passes on the device, and `./gradlew testDebugUnitTest assembleDebug` is green. Commit `6.json`.
- [ ] **Step 5:** Commit `feat(wrapped): schema v6 for listening aggregates and recaps`.

### Task 2: ListenClock and ListenTracker
**Files:**
- Create: `wrapped/Day.java`, `wrapped/ListenClock.java`, `wrapped/ListenTracker.java`.
- Modify:
  - `service/SwaraPlaybackService.java`: own a ListenTracker. Call `onIsPlayingChanged`, `onLeaveItem(songId, eventType)` in the existing leave branch, and `onItemStarted(song)`.
  - `service/VibeController.java` `beginSession`: call `tracker.onVibeStart(songId)`.
  - `ui/metadata/EditSongInfoBottomSheet.java` (or `SongMetadataRepository` after a successful save): refresh the snapshot.
- Test: `app/src/test/java/com/psthetech/swara/wrapped/ListenClockTest.java`, `DayTest.java`.

**Interfaces (produces):**
- `Day`: `static int of(long epochMs, TimeZone tz)`, `static int weekdayIndex(int day /*Mon=0*/)`, `static int addDays(int day, int n)`, `static int monthStart(int y, int m)`, `static int monthEnd(int y, int m)`.
- `ListenClock(LongSupplier elapsedMs, LongSupplier wallMs, TimeZone tz)`:
  - `void setPlaying(boolean playing)`
  - `List<Slice> drain()`. `Slice` is `{int day; int hour; long ms}`. It returns the accumulated time split at each wall-clock hour boundary and clears it.
- `ListenTracker(Context)`:
  - `void onItemStarted(Song s)`, which upserts the snapshot and records a play on the current day.
  - `void onIsPlayingChanged(boolean)`
  - `void onLeaveItem(long songId, int listenEventType)`, which drains the clock and writes daily (playedMs plus finish/skip/repeat) and hourly rows.
  - `void checkpoint()`, which drains the clock and writes playedMs only. It is called every 30 s while playing via the service's main handler.
  - `void onVibeStart(long songId)`
- [ ] **Step 1:** Write the failing tests:
  - `DayTest`: yyyymmdd for a fixed epoch in Asia/Kolkata; weekday; month ends including Feb 2028.
  - `ListenClockTest`:
    - `pausedTimeNotCounted`.
    - `resumeAccumulates`.
    - `crossesMidnight_splitsIntoTwoDays` (Review Focus 2): 23:59:30 → 00:00:30 gives 30 000 ms on each of the two days, at hours 23 and 0.
    - `crossesHour_splits`.
    - `drainClears`.
- [ ] **Step 2:** Run `--tests '*ListenClockTest' --tests '*DayTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement and wire the service, Vibe and metadata hooks. All DB writes go through `SwaraApplication.getDbExecutor()`.
- [ ] **Step 4:** JVM suite and assembleDebug are green. On the device: play about 1 minute, skip, pull the DB (`run-as`), and check that the `listen_daily` playedMs is within ±2 s.
- [ ] **Step 5:** Commit `feat(wrapped): track real listening time into daily aggregates`.

### Task 3: History backfill
**Files:**
- Create: `wrapped/HistoryBackfill.java`.
- Modify: `SwaraApplication.java`, to run the backfill once on the DB executor after DB init.
- Test: `app/src/test/java/com/psthetech/swara/wrapped/HistoryBackfillTest.java`.

**Interfaces (produces):**
- `static List<DailyRow> convert(List<PlayHistory> plays, List<ListenEvent> events, Map<Long, Long> durationBySong, TimeZone tz)`. This is the pure conversion:
  - A FINISH counts the full duration.
  - A SKIP or leave counts its positionMs.
  - A PLAY with no matching leave counts min(duration, 30 000).
  - `play_history` rows not matched by events count min(duration, 30 000) and one play.
- `static void runOnce(Context)`. It uses the preference flag `wrapped_backfill_done`. It skips any (day, songId) that already has a daily row, to stay idempotent (Review Focus 1). It sets the flag only on success.
- [ ] **Step 1:** Write the failing tests:
  - `finishCountsFullDuration`.
  - `skipCountsPosition`.
  - `unmatchedPlayCapped30s`.
  - `rerunDoesNotDoubleCount`, which runs the pure decision function `rowsToInsert(converted, existingKeys)` twice.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement. The hourly rows come from event timestamps. Snapshots are built from MusicRepository canonical songs where available, falling back to the history's stored title, artist and album.
- [ ] **Step 4:** Run. Expected: PASS. Then install on the device and verify that `listen_daily` has rows dated before today.
- [ ] **Step 5:** Commit `feat(wrapped): backfill aggregates from existing history`.

### Task 4: WrappedStats, engine and mood buckets
**Files:**
- Create: `Period.java`, `DailyRow.java`, `HourlyRow.java`, `SongInfo.java`, `MoodBucket.java`, `WrappedStats.java`, `WrappedStatsEngine.java`.
- Test: `WrappedStatsEngineTest.java`, `WrappedStatsJsonTest.java`, `MoodBucketTest.java`.

**Interfaces (produces):**
- `Period`: `static Period month(int y, int m)`, `static Period year(int y)`, `static Period yearToDate(int y, int endDay)`, `static Period allTime(int firstDay, int today)`, plus `start`, `end`, `kind`, `key()` (for example `"month-2026-10"` or `"year-2026"`).
- `MoodBucket { SAD, CALM, HAPPY, ENERGETIC; static MoodBucket of(@Nullable double[] mood) }`. Returns null for an unknown mood.
- `WrappedStats` has public final fields, a `toJson()` method and a `static fromJson(String)` method:
  - `Period period; long totalMs`
  - `List<Long> topSongIds /*≤5*/; List<String> topArtistKeys /*≤5*/; long topAlbumId /*-1*/`
  - `Map<MoodBucket, Long> moodMs; long moodKnownMs`
  - `int vibeStarts; long topVibeSongId /*-1*/`
  - `int peakHour /*-1*/; int busiestWeekday /*-1*/; int longestStreakDays; int listeningDays`
  - `long mostRepeatedSongId /*-1, needs ≥3*/; long mostSkippedSongId /*-1, needs ≥3*/`
  - `int newSongs; long rediscoveredSongId /*-1*/; int distinctSongs`
  - `long topSongMs; long nightMs; long morningMs`
  - `Map<Long, SongInfo> songs` (info for every referenced id)
- `static WrappedStats compute(Period p, List<DailyRow> dailyInPeriod, List<DailyRow> dailyBefore, List<HourlyRow> hourlyInPeriod, Map<Long, SongInfo> snapshots, Map<Long, double[]> resolvedMoods)`
- [ ] **Step 1:** Write the failing tests, each using fixed rows:
  - `totalMinutes`.
  - `topSongs_tiesBySongId` (Review Focus 3).
  - `topArtists_byCanonicalKey`.
  - `topAlbum_needsThreePlays`.
  - `moodMix_bucketsAndUnknownExcluded`, with the bucket boundaries from Global Constraints checked at exactly 0.35, 0.70 and 0.60.
  - `peakHour`.
  - `busiestWeekday_tieMondayFirst` (Review Focus 3).
  - `longestStreak_acrossMonthAndYear`.
  - `newSongs_byFirstHeardDay`.
  - `rediscovered_needs60DayGap`.
  - `repeatAndSkip_needThree`.
  - `json_roundTrip_equal`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement as pure Java, O(rows). `nightMs` is the hours 22, 23 and 0–3 total; `morningMs` is hours 5–8.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(wrapped): stats engine`.

### Task 5: Personality and SlidePlan
**Files:**
- Create: `Personality.java`, `SlideType.java`, `SlidePlan.java`.
- Test: `PersonalityTest.java`, `SlidePlanTest.java`.

**Interfaces:**
- Consumes: `WrappedStats` from Task 4.
- Produces:
  - `enum Personality { NIGHT_OWL, EARLY_RISER, MOOD_LIFTER, LOYALIST, EXPLORER, ENERGY_SEEKER, BALANCED; static Personality of(WrappedStats s) }`, with the rules and order from Global Constraints.
  - `enum SlideType { INTRO, MINUTES, TOP_SONGS, TOP_ARTISTS, TOP_ALBUM, MOOD_MIX, MOOD_OF_PERIOD, VIBE, HABITS, REPEAT_SKIP, DISCOVERY, PERSONALITY, SUMMARY }`
  - `static List<SlideType> SlidePlan.plan(WrappedStats s)`, with the skip rules from spec §4. Returns an empty list when totalMs < 1 800 000.
- [ ] **Step 1:** Write the failing tests:
  - Each personality rule at its boundary: just at the threshold matches, just below does not.
  - The rule order: a Night Owl who also has 5 Vibe starts is still NIGHT_OWL.
  - `SlidePlan`:
    - `under30Minutes_empty`.
    - `alwaysSlidesPresent`.
    - `moodSkippedBelow50Percent`.
    - `topSongsNeedThree`.
    - `vibeSkippedWhenZero`.
    - `habitsNeedThreeDays`.
    - `orderMatchesSpec`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(wrapped): personality and slide plan`.

### Task 6: Rhythm Pop SlideRenderer
**Files:**
- Create: `render/RhythmPopPalette.java` and `render/SlideRenderer.java`.
- Modify: `strings.xml` (all slide copy: titles, tags, personality names and one-line reasons, the "that's %d movies" line, footer "SWARA WRAPPED").
- Test: `app/src/androidTest/java/com/psthetech/swara/wrapped/SlideRendererDeviceTest.java`. It renders every SlideType at progress 0, 0.5 and 1 from a fixed `WrappedStats`, asserts no exception, checks the bitmaps are not blank (non-uniform pixels), and saves PNGs to the app cache for screenshots.

**Interfaces:**
- Consumes: `WrappedStats`, `SlideType`, `Personality`.
- Produces:
  - `SlideRenderer(Context ctx)`
  - `void draw(Canvas c, int widthPx, int heightPx, SlideType t, WrappedStats s, float progress, @Nullable Bitmap art)`. It scales from the 1080×1920 logical size, and `progress` is 0..1 through the slide's entry animation.
  - `static Bitmap renderFinalFrame(Context, SlideType, WrappedStats, @Nullable Bitmap art)`.
- **Style** (from the chosen mockup):
  - One gradient pair per slide, from the palette `[#FF3D7F→#FF8A00, #00D2C3→#3A47D5, #C6FF00→#00C853, #FFC300→#FF5733, #8E2DE2→#4A00E0, #FF5F6D→#FFC371]`, cycling by slide index.
  - Huge numbers in black-weight sans with a −0.04em letter spacing.
  - A pill tag (black background, white text, rounded 99).
  - 2–3 translucent white or accent blobs drifting by `progress` and time.
  - Footer "SWARA WRAPPED" at 0.18em letter spacing.
  - Album art comes from `ArtworkHelper` / `ArtworkRepository`, loaded by the caller.
  - Count-up uses `ease-out(progress)`, and list rows stagger at 120 ms each.
- [ ] **Step 1:** Write the device test. It fails, because the class doesn't exist.
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** Run the device test, pull the PNGs (`adb exec-out run-as … cat`), and view them. Check that every slide is legible, nothing overflows at 5 long Tamil titles (ellipsize), and that the colours match the Rhythm Pop mockup.
- [ ] **Step 4:** Commit `feat(wrapped): Rhythm Pop slide renderer`.

### Task 7: Image share and video export
**Files:**
- Create: `export/SlideImageExporter.java`, `export/RecapVideoExporter.java`.
- Test:
  - `app/src/androidTest/java/com/psthetech/swara/wrapped/RecapVideoExporterDeviceTest.java`: export a 3-slide recap and assert with MediaExtractor that a video track exists, width×height is 1080×1920 or 720×1280, the duration is 9 s ±0.5 s, and there is no audio track.
  - A JVM test `VideoPlanTest` for the pure frame schedule: `framesForSlides(n, fps=30, secondsPerSlide=3)` and `progressAt(frame)`.

**Interfaces:**
- Consumes: `SlideRenderer`, `SlidePlan`, `WrappedStats`, the existing `SongShareHelper` (FileProvider under `cache/share_cards`).
- Produces:
  - `SlideImageExporter.share(Activity, SlideType, WrappedStats, Bitmap art)`.
  - `RecapVideoExporter`:
    - `interface Listener { void onProgress(float p); void onDone(Uri uri); void onError(String msg); }`
    - `Cancellable export(Context, WrappedStats, List<SlideType>, Map<Long,Bitmap> art, Listener)`. It runs on a background thread and posts callbacks to main.
  - Encoder setup: `MediaFormat` AVC with COLOR_FormatSurface, 1080×1920 at 30 fps and about 8 Mbps, I-frame interval 1. It uses `createInputSurface()` plus `lockHardwareCanvas()`. If `isSizeSupported` fails or `configure` throws, it retries at 720×1280 (Review Focus 4).
  - Output: an MP4 written to a temp file in the cache, then copied into MediaStore `Movies/Swara/Swara-Wrapped-<key>.mp4` (IS_PENDING pattern).
  - On any error or cancel it deletes the temp file and the pending MediaStore row (Review Focus 4). No audio track.
- [ ] **Step 1:** Write `VideoPlanTest`. It fails.
- [ ] **Step 2:** Implement the pure schedule; the test passes.
- [ ] **Step 3:** Write the device test. It fails.
- [ ] **Step 4:** Implement both exporters. The device test passes. Pull the MP4 and confirm that `ffprobe` shows h264, the right dimensions and no audio.
- [ ] **Step 5:** Commit `feat(wrapped): share slides as images and export a silent video`.

### Task 8: Story screen
**Files:**
- Create: `ui/WrappedStoryActivity.java` (manifest entry, exported=false, full-screen theme), `ui/StoryView.java`, `wrapped/RecapRepository.java`, and layout `activity_wrapped_story.xml`.
- Test: `StoryNavigationTest.java` (JVM, pure `StoryState`: next/prev/hold/auto-advance at 6 000 ms and bounds), and a device check.

**Interfaces:**
- Consumes: `SlideRenderer`, `SlidePlan`, the exporters, and `WrappedDao`.
- Produces:
  - `RecapRepository`:
    - `@WorkerThread WrappedStats statsFor(Period p)`, which computes live: it loads the rows, resolves moods with `RecommendationService.moodOf` or `MoodResolver` over stored moods, and calls the engine.
    - `@WorkerThread @Nullable WrappedStats stored(String key)`
    - `@WorkerThread void store(String key, WrappedStats s)`
  - The intent extras `EXTRA_RECAP_KEY` or `EXTRA_PERIOD_KIND` + `EXTRA_PERIOD_START`/`END`.
  - `StoryState` (pure): `next()`, `prev()`, `pause()`, `resume()`, `tick(long dtMs)`, `index()`, `slideProgress()`.
- **Behavior:**
  - Tap the right 2/3 for next and the left 1/3 for previous. Long-press pauses. Progress bars run along the top.
  - The share button on each slide calls `SlideImageExporter`. "Save video" on SUMMARY shows a progress dialog with Cancel.
  - The activity loads stats off the main thread, from the stored recap when it was launched with a key (Review Focus 5: it works with no service running), and marks the recap seen.
  - Artwork bitmaps are preloaded off the main thread via ArtworkHelper's synchronous decode path, or `ContentResolver.loadThumbnail` on API 29+.
- [ ] **Step 1:** Write `StoryNavigationTest`. It fails.
- [ ] **Step 2:** Implement `StoryState`; the test passes.
- [ ] **Step 3:** Implement the activity and view.
- [ ] **Step 4:** Install on the device and check:
  - Launch the story via `adb shell am start` with a stored key after `am force-stop`, for Review Focus 5.
  - Screenshot 3 slides.
  - Tap navigation works.
  - Sharing opens the share sheet.
  - The video export completes.
- [ ] **Step 5:** Commit `feat(wrapped): story screen`.

### Task 9: Stats screen and Home entry points
**Files:**
- Create: `ui/StatsFragment.java` and `fragment_wrapped_stats.xml`.
- Modify:
  - `nav_graph.xml`: add `statsFragment` and `action_home_to_stats`.
  - `ui/home/HomeFragment.java` + `fragment_home.xml`: a recap banner above "For you", and a "Your stats" link in the header row near `btnSettings`.
  - strings.
- Test: `BannerPolicyTest.java` (JVM, pure: `showBanner(Recap r, int today)`, true when not dismissed and today − createdDay ≤ 14).

**Interfaces:**
- Consumes: `RecapRepository`, `WrappedDao.latestUndismissedLive`, `WrappedStoryActivity` extras.
- Produces:
  - `StatsFragment`: a period picker (This month / This year / All time) and one scrolling page that reuses `WrappedStats` (minutes, top songs, top artists, mood mix bars, habits, personality).
  - A "Play my Wrapped" button opens the story for the selected period.
  - Under 30 minutes it shows the text `wrapped_keep_listening` ("Keep listening — your stats will appear here."). It is themed with DesignTokens like Settings.
- [ ] **Step 1:** Write `BannerPolicyTest`. It fails.
- [ ] **Step 2:** Implement the policy; the test passes.
- [ ] **Step 3:** Implement the fragment, banner and link.
- [ ] **Step 4:** Install on the device and screenshot Home with the banner (insert a test recap via the debug path or a temporary test) and the stats screen for each period.
- [ ] **Step 5:** Commit `feat(wrapped): stats screen and Home entry points`.

### Task 10: RecapWorker and notifications
**Files:**
- Create: `wrapped/RecapWorker.java` and `wrapped/WrappedNotifier.java`.
- Modify:
  - `SwaraApplication.java`: enqueue the daily periodic job, unique "wrapped_recap", KEEP.
  - `HomeFragment`: request POST_NOTIFICATIONS once, after the first banner is shown, on API 33+ only, using a preference flag `wrapped_notif_asked`.
  - strings, and the manifest if needed.
- Test: `RecapSchedulePolicyTest.java` (JVM, pure: `List<String> recapsDue(int today, Set<String> existingKeys, LongFunction<Long> totalMsForPeriod)`).

**Interfaces:**
- Produces the policy:
  - **Monthly:** on or after the 1st, `month-<prev>` is due if it's missing and ≥ 30 min.
  - **Yearly:** on or after 1 Dec, `year-YYYY` (Jan 1 – Nov 30) is due if missing. On or after 1 Jan, `year-(Y−1)` is rebuilt with the full year if the stored period end is before Dec 31; this rebuild sends no notification.
  - `WrappedNotifier.notifyReady(Context, String key, String title)`: channel "wrapped", low importance, opens WrappedStoryActivity with the key, and the PendingIntent is FLAG_IMMUTABLE.
- [ ] **Step 1:** Write the failing tests:
  - `monthlyDueOnFirst`.
  - `monthlySkippedUnder30Min`.
  - `notDueTwice`.
  - `yearlyDueOnDec1_coversJanToNov`.
  - `yearRebuiltOnJan1_noNotify`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement the worker and notifier.
- [ ] **Step 4:** Run the JVM suite. On the device:
  - Run the worker via a temporary test with a fake "today" of the 1st of next month.
  - Check that the notification appears and tapping it opens the story.
  - Check that the permission prompt appears once.
- [ ] **Step 5:** Commit `feat(wrapped): monthly and yearly recaps with notification`.

### Task 11: Docs and release check
- Modify: `README.md` Features: Swara Wrapped, with health-claim-free copy.
- [ ] **Step 1:** Run `./gradlew testDebugUnitTest assembleDebug assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease`. Everything is green and R8 builds.
- [ ] **Step 2:** `grep -rniE "depress|treat|therap|heal" app/src/main/res` finds no user-facing hits.
- [ ] **Step 3:** Install the final build on the device.
- [ ] **Step 4:** Commit `docs: Swara Wrapped in README`.
