# Deck Widget and Standby Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Swara Deck" home-screen widget and a dim Deck standby screen over the lock screen.

**Architecture:** The widget is a second `AppWidgetProvider` fed by the existing
`SwaraWidgetUpdater` pushes, drawing the Deck reel into bitmaps and spinning vector overlays in
indeterminate `ProgressBar`s. Standby is one activity reusing the Deck views and
`PlaybackViewModel`; its timing and burn-in rules are pure Java.

**Tech Stack:** Java, RemoteViews, Media3, existing Deck Canvas views. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-26-deck-widget-standby-design.md`

## Global Constraints

- Java only, minSdk 24, no new libraries, no new permissions.
- Colours from `DesignTokens`; the existing widget and the standard player unchanged.
- No fake spectrum. No allocations in `onDraw`. Respect "remove animations".
- Standby: playback controls only; 10% brightness; close after 15 min untouched or 1 min paused.
- Gates: `testDebugUnitTest assembleDebug lintDebug`, no new lint errors or warnings.
- No push or merge before the user has tested.

## Review Focus

1. The one-second tick must not re-send the whole widget (spin restart, battery): same content gives a progress-only update (Task 2 test `onlyTheProgressMovingNeedsNoFullRedraw`).
2. No song, or zero duration: idle widget, no spin, empty progress (Task 2 test).
3. Paused or screen off in standby: no animation and no tap (Task 3 runtime check).
4. A phone clock change or a long screen-off must not break the timeout: elapsed-realtime based, expired on return (Task 1 test).
5. Standby over the lock screen must not open the rest of the app (Task 3 runtime check).

---

### Task 1: Standby timing and burn-in rules

**Files:** Create `ui/deck/StandbyPolicy.java`; test `ui/deck/StandbyPolicyTest.java`.

**Interfaces:** Produces `static boolean shouldClose(long nowMs, long lastTouchMs, long pausedSinceMs)`
(`pausedSinceMs < 0` means playing), `static int shiftX(long minute, int max)`,
`static int shiftY(long minute, int max)`, `IDLE_LIMIT_MS = 15 min`, `PAUSE_LIMIT_MS = 1 min`.

- [ ] Write tests: both limits at limit-1 and limit; a pause older than the last touch counts from the touch; shifts stay within ±max, change every minute, are 0 when max is 0.
- [ ] See them fail, implement, see them pass, commit.

### Task 2: Deck widget

**Files:** Create `widget/DeckWidgetModel.java` (pure), `widget/DeckWidgetRenderer.java`,
`widget/SwaraDeckWidgetProvider.java`, `res/layout/widget_swara_deck.xml`,
`res/xml/widget_swara_deck_info.xml`, `res/drawable/widget_deck_*.xml`; modify
`widget/SwaraWidgetUpdater.java`, `ui/deck/DeckReelView.java`, `AndroidManifest.xml`,
`strings.xml`; test `widget/DeckWidgetModelTest.java`.

**Interfaces:** `DeckWidgetModel.spins(boolean hasSong, boolean playing, int sdk)`,
`progress(long positionMs, long durationMs)` in 0..1000, `signature(long songId, boolean playing,
boolean favorite, boolean korokae, long durationMs, int artKey, int paletteKey)`;
`DeckReelView.setRotatingParts(boolean)`; `DeckWidgetRenderer.push(Context, Song,
SwaraWidgetUpdater.State, Bitmap)`, `hasPlaced(Context)`.

- [ ] Tests: spin only with a song, playing, SDK 31+; progress clamps and is 0 without duration; `onlyTheProgressMovingNeedsNoFullRedraw` (equal signatures), and each other input changes the signature. Fail, implement, pass.
- [ ] Renderer, layout, provider, manifest. Overlay geometry (112dp square): radius 50, window radius 0.795r, width 0.13r, sweep 78° centred 90/210/330°, marker r 0.045r at 12 o'clock.
- [ ] Updater: push the Deck widget even when no other widget is placed; `hasPlacedWidgets` counts it.
- [ ] Gates, commit.

### Task 3: Standby screen

**Files:** Create `ui/deck/DeckStandbyActivity.java`, `res/layout/activity_deck_standby.xml`,
`res/layout-land/activity_deck_standby.xml`, `res/drawable/ic_standby.xml`; modify
`AndroidManifest.xml`, both `fragment_now_playing_deck.xml` (`btnStandby`),
`NowPlayingFragment.java`, `DeckStyler.java`, `DeckWidgetRenderer.java` (standby key).

**Interfaces:** Consumes `StandbyPolicy`, `PlaybackViewModel`, Deck views.
Produces `DeckStandbyActivity.intent(Context)`; `static void DeckStyler.paint(View, DeckPalette)`.

- [ ] Activity: not exported, own task, excluded from recents, `setShowWhenLocked`, keep screen on, brightness 0.1, immersive, forced-night tokens on black; start/stop drive reel, bars and tap; 1 s check runs `StandbyPolicy`.
- [ ] Standby buttons in the Deck player and widget.
- [ ] Gates, commit.

### Task 4: Runtime checks and APK

- [ ] Temporary, uncommitted Robolectric harness: render the widget (playing, paused, idle, light, dark) and standby (portrait, landscape); standby lifecycle, tap gating, timeouts. Look at every render.
- [ ] Remove the harness, run gates, copy the APK, install with `adb install -r --user 0` if a device is connected. Never uninstall.
