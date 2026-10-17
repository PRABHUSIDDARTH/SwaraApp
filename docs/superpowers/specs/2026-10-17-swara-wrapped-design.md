# Swara Wrapped — Design

Date: 2026-10-17
Status: Approved in brainstorming, awaiting spec review
Roadmap sub-project 2. It builds on the Vibe Mode branch (`feature/vibe-recommender`, PR #18). This work lives on branch `feature/wrapped`.

## 1. Goal

A listening recap in the style of Spotify Wrapped. Its shared images and video double as advertising for Swara.

- **Monthly recaps** for the previous month, a **yearly recap** in December, and an **always-available stats screen** with a period picker (this month / this year / all time).
- **Story-style slides** in the "Rhythm Pop" visual style: a bold gradient per slide, huge chunky numbers, playful shapes. Each slide can be shared as a 9:16 image, and the whole recap can be exported as a **silent** MP4.
- Entry points: a **Home banner**, a **"Your stats" link on Home**, and **one quiet notification** when a recap is ready.

### Constraints
- Everything is computed on the device. No network access, no account, no uploads.
- No new third-party libraries. Video export uses Android `MediaCodec` + `MediaMuxer`.
- The video has no audio. A background track (for example NCS) was ruled out, because bundling one needs a commercial licence.
- Copy makes no health claims. Mood words describe the music ("Your mood of 2026: Happy"), never the listener's wellbeing.
- Java only; no Room access on the main thread; every user-facing string lives in `strings.xml`.

### Success criteria
1. A heavy listener's December recap covers the whole year. Raw-event trimming (5 000 listen events, 200 play-history rows) must not lose any Wrapped data.
2. The story opens in under 300 ms from a stored recap.
3. A shared PNG and the exported MP4 match the on-screen slides exactly, because all three use the same drawing code.
4. Every number on a slide is reproducible from unit tests over fixed daily rows.

## 2. Data (Room v5 → v6, explicit `MIGRATION_5_6`)

```
listen_daily(
  day INTEGER NOT NULL,          -- yyyymmdd, device local time when listened
  songId INTEGER NOT NULL,
  playedMs INTEGER NOT NULL, plays INTEGER NOT NULL, finishes INTEGER NOT NULL,
  skips INTEGER NOT NULL, repeats INTEGER NOT NULL, vibeStarts INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(day, songId))      -- INDEX(songId)

listen_hourly(
  day INTEGER NOT NULL, hour INTEGER NOT NULL,   -- 0–23
  playedMs INTEGER NOT NULL,
  PRIMARY KEY(day, hour))

song_snapshot(
  songId INTEGER PRIMARY KEY NOT NULL,
  title TEXT, artist TEXT, artistKey TEXT, album TEXT, albumId INTEGER NOT NULL,
  firstHeardDay INTEGER NOT NULL)

recap(
  recapKey TEXT PRIMARY KEY NOT NULL,   -- "month-2026-10", "year-2026"
  periodStart INTEGER NOT NULL, periodEnd INTEGER NOT NULL,   -- yyyymmdd inclusive
  statsJson TEXT NOT NULL,              -- serialized WrappedStats
  createdAt INTEGER NOT NULL, seen INTEGER NOT NULL, bannerDismissed INTEGER NOT NULL)
```

- **Never trimmed.** These tables are not touched by song deletion or by the library prune: past listening is history. `song_snapshot` is refreshed whenever the song plays again, and after a metadata edit.
- **Vibe usage.** Each time Vibe Mode starts, `vibeStarts` is incremented in the `listen_daily` row for the start song and day.
- **Backfill (in the migration's first run, off the main thread).** Existing `play_history` and `listen_events` rows are converted into `listen_daily`, `listen_hourly` and `song_snapshot`:
  - `playedMs` is estimated as the position at which the song was left. For a finish, it is the song's duration.
  - A one-shot preference flag records that the backfill is done.

## 3. Components

| Unit | Responsibility |
|---|---|
| `wrapped/ListenTracker` (owned by `SwaraPlaybackService`) | Accumulates **actual playing time** with `SystemClock.elapsedRealtime()`, only while `isPlaying`. On leaving an item it records playedMs plus the event type (`EventClassifier`) into `listen_daily`, `listen_hourly` and `song_snapshot`. It checkpoints every 30 s while playing, so a process death loses at most 30 s. Writes go to the DB executor. |
| `wrapped/WrappedStats` | An immutable value object holding every number the slides need. It serializes to and from JSON (via `org.json`). |
| `wrapped/WrappedStatsEngine` (pure Java) | `WrappedStats compute(Period p, List<DailyRow> daily, List<HourlyRow> hourly, Map<Long,SongInfo> snapshots, Map<Long,double[]> resolvedMoods /* {valence, energy} via MoodResolver */)`. Has no Android dependencies. |
| `wrapped/Personality` (pure) | First matching rule (section 4). |
| `wrapped/SlidePlan` (pure) | `List<SlideType> plan(WrappedStats s)`: which slides appear, in order. |
| `wrapped/render/SlideRenderer` | `void draw(Canvas c, SlideType t, WrappedStats s, float progress /*0..1 animation*/, Bitmaps art)`. The single drawing code path for the screen, PNG export and video frames. Draws at a 1080×1920 logical size, scaled. |
| `wrapped/ui/WrappedStoryActivity` | Full-screen story: tap right for next, tap left for previous, hold to pause, top progress bars, 6 s auto-advance. Share button per slide; "Save video" on the summary slide. |
| `wrapped/ui/StatsFragment` | The always-available stats screen with a period picker (This month / This year / All time) and a "Play my Wrapped" button. |
| `wrapped/export/SlideImageExporter` | Renders the final animation frame to a PNG in `cache/share_cards/`, then hands it to the existing share helpers (`SongShareHelper` / `ShareCardBottomSheet` paths). |
| `wrapped/export/RecapVideoExporter` | Produces a silent H.264 MP4 at 1080×1920 and 30 fps, about 3 s per slide. It draws through `Surface.lockHardwareCanvas` on the encoder input surface, muxes with `MediaMuxer`, and saves to `Movies/Swara` via `MediaStore`. Falls back to 720×1280 if 1080p isn't supported. Runs in the background with progress and cancel. |
| `wrapped/RecapWorker` (WorkManager, daily) | Builds missing recaps (section 5), stores them in `recap`, and notifies. |
| Home integration | A banner ("Your October Wrapped is ready"), visible for 14 days or until dismissed. A "Your stats" link opens `StatsFragment`. |

## 4. Slides (Rhythm Pop)

Each slide uses its own gradient pair from a fixed palette, a huge black-weight number or title, a pill tag, two or three drifting translucent blobs, and the footer "SWARA WRAPPED". Animations are code-only: count-up numbers, drifting blobs, staggered list rows (ease-out, 600–900 ms).

| # | Slide | Content | Skipped when |
|---|---|---|---|
| 1 | Intro ✱ | "Your October" / "Your 2026" | never |
| 2 | Minutes ✱ | Total minutes plus a comparison ("that's N movies", 1 movie = 150 min) | never |
| 3 | Top songs ✱ | Top 5 by playedMs, with the #1 artwork large | fewer than 3 songs with playedMs > 0 |
| 4 | Top artists | Top 5 canonical artists by playedMs | fewer than 3 artists |
| 5 | Top album | Album artwork and name, top by playedMs | no album with ≥ 3 plays |
| 6 | Mood mix | Shares of SAD / CALM / HAPPY / ENERGETIC minutes as bars | < 50% of minutes have a known mood |
| 7 | Mood of the period | The largest share in slide 6 | same as 6 |
| 8 | Vibe | Vibe starts, plus the most-used start song | vibeStarts = 0 |
| 9 | Habits | Peak hour, busiest weekday, longest streak of consecutive listening days | < 3 listening days |
| 10 | On repeat / Skipped | Most repeats (≥ 3) and most skips (≥ 3) | neither qualifies |
| 11 | Discovery | Count of songs with firstHeardDay in the period, plus "rediscovered" | nothing new and nothing rediscovered |
| 12 | Personality ✱ | Listener type with a one-line reason | never |
| 13 | Summary ✱ | One image: top 3 songs, top artist, minutes, mood (if any), personality | never |

✱ = always shown once the period has ≥ 30 minutes; below that, no recap is created.

**Mood bucketing.**
- A song's mood comes from `MoodResolver` (the user's correction wins).
- Valence < 0.35 → SAD. Energy ≥ 0.70 → ENERGETIC. Valence ≥ 0.60 → HAPPY. Otherwise CALM.
- Unknown mood → not counted.

**Rediscovered.** The song with the most playedMs in the period that was played before it but not at all in the 60 days before the period starts.

**Personality.** The first matching rule wins:

| Type | Rule |
|---|---|
| Night Owl | ≥ 40% of playedMs between 22:00 and 03:59 |
| Early Riser | ≥ 40% between 05:00 and 08:59 |
| Mood Lifter | ≥ 5 Vibe starts |
| Loyalist | top song ≥ 15% of playedMs |
| Explorer | ≥ 30% of distinct songs are new in the period |
| Energy Seeker | ENERGETIC ≥ 40% of mood-known minutes |
| Balanced Listener | otherwise |

## 5. Recaps and notifications

- **Monthly.** `RecapWorker` runs daily. If the previous month has no `recap` row and has ≥ 30 min of listening, it builds `month-YYYY-MM`.
- **Yearly.**
  - On or after 1 Dec it builds `year-YYYY` covering Jan 1 – Nov 30.
  - On or after 1 Jan it rebuilds `year-(YYYY−1)` to cover the full year. The rebuild sends no second notification.
- **Notification.** Channel "Wrapped" (low importance). One notification per new recap; tapping it opens the story.
- **Permission.** `POST_NOTIFICATIONS` is requested once on Android 13+, only after the user has seen the first recap banner. If it's denied, the banner still works.
- **Stored stats are frozen.** Opening a recap reads `statsJson`. The stats screen computes live for the selected period.

## 6. Error handling and edge cases

- **Not enough listening.** Under 30 min the stats screen shows "Keep listening — your stats will appear here." and offers no story.
- **Deleted songs.** Shown using `song_snapshot`, with placeholder artwork.
- **Time zone or clock change.** Days are recorded in local time at listen time and never rewritten.
- **Export failure** (encoder error, no storage). A clear error, the partial file is deleted, and image sharing still works.
- **Process death.** ≤ 30 s of listening time is lost.
- **Backfill failure.** Logged, and the flag is not set, so the backfill retries on the next start. Missing backfill never blocks the app.

## 7. Testing

- **JVM:**
  - `WrappedStatsEngine` over fixed rows: minutes, top-N ties broken by songId, streaks across month and year ends, busiest weekday, discovery and rediscovered, mood bucketing.
  - `Personality` boundaries for each rule.
  - `SlidePlan` skip rules.
  - `ListenTracker` time accounting with a fake clock: play, pause, resume, a song change while paused, a 30 s checkpoint.
  - Backfill conversion.
  - `WrappedStats` JSON round-trip.
- **Device:**
  - `MigrationV6Test` (v5 → v6 keeps all data).
  - A `RecapVideoExporter` run producing a playable MP4 of the expected duration (checked with `MediaExtractor`).
  - Screenshots of the story slides and of the shared PNG.

## 8. Out of scope

Audio in the video; cloud backup of stats; Jam; playlist sharing (roadmap 3); comparisons with other users.
