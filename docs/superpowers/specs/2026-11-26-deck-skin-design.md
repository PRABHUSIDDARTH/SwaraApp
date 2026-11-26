# Deck player skin: design (approved 2026-11-26)

An optional Now Playing skin that feels like a physical tape deck. Off by default, switched in
Settings. The 8 colour themes and the standard player are unchanged. Original hardware design.

## Approaches

1. **Separate Deck layouts, one fragment (chosen).** A preference picks
   `fragment_now_playing_deck` (portrait and `layout-land`). `NowPlayingFragment` keeps every
   action; widgets that reuse a view id also keep its Java type.
2. A second fragment. Cleaner separation, but duplicates all the action wiring.
3. A new `MorphismStyle`/`ColorTheme` entry. Rejected: it would change the existing themes.

## Screen

Matte body with screws, one large reel with a marker dot (cover art as the hub label),
dot-matrix time readout, spectrum bars, chunky seek fader, transport and action keys with press
travel. Header and song info are the existing `view_np_header` / `view_np_info` includes.
Both orientations scroll if large text does not fit. Touch targets are at least 48dp.

Colours come only from `DesignTokens` (one mapping class, `DeckPalette`), and follow the app's
light/dark mode. The standard player stays always-dark.

The reel reuses `DiscSpinPhysics` and `CircularSeekHelper`; drag seeks, tap on the cover edits
artwork. The fader extends `AppleScrubberView`, keeping its touch and TalkBack behaviour.

## Order

Static Deck screen (not connected to anything), then screenshots in portrait and landscape,
light and dark. **Stop for approval.** Then tap, maths, wiring, Settings switch.

## Spectrum tap

A pass-through `BaseAudioProcessor` placed after Korokae. It stays in the chain (changing
`isActive` would re-initialise the AudioTrack); a volatile flag turns copying on and off. The
audio thread only copies bytes. Downmix, window, FFT, log bands and smoothing run on the UI
thread into preallocated arrays. The UI reads behind the newest sample by about one AudioTrack
buffer, so bars match what is heard. EQ, volume and Bluetooth delay are not reflected.

### Thread safety (no locks, no allocation on the audio thread)

One writer (audio thread), one reader (UI thread), one preallocated ring of raw PCM.

- The writer publishes two counters: `claimed` (about to write up to here) and `committed`
  (written up to here). It raises `claimed` with an atomic read-modify-write *before* touching
  the data, so data writes cannot move above it, and sets `committed` with a release store
  *after* it.
- The reader loads `committed`, copies its window into its own array, then re-reads `claimed`
  with `getAndAdd(0)`. That read-modify-write stops the copy's loads from moving below it. If
  the writer has claimed past `windowStart + capacity`, the window may be torn and is dropped
  (the bars keep decaying for that frame).
- Neither side ever waits. The ring holds several seconds, so drops should be very rare.
- Format (sample rate, channels) and the start of the current epoch are published under the
  same check: a window is used only if the epoch did not change while it was read.

### Never show old audio

- Seek, skip and format change flush the audio sink. `onFlush` starts a new epoch; the reader
  rejects anything written before it. Bars reset to zero.
- Gapless auto-advance does not flush, and the ring already holds the *next* track's start
  (written ahead of playback). On a track-change event the UI raises a floor to the audible
  position and resets the bars: everything older is discarded, queued new audio is kept.

### Format changes

16-bit PCM with any channel count and sample rate. Mono and stereo, and a rate change between
tracks, are covered by unit tests; band edges are recomputed when the rate changes. Other
encodings leave the tap inactive and the bars flat.

## Stopping

Animation and tap stop when paused (the reel may spin down; copying stops at once), when the
fragment stops (screen off), or when the view is not visible. With "remove animations": still
reel, flat bars, no tap, instant key presses; time and seek position still update.

## Phase 2 (separate branch, after Phase 1 approval)

Equalizer as studio faders with level meters, volume as a rotary knob, metal toggle switches.

## Known baseline

`lintDebug` already fails on `main` (2026-11-26) with 4 `RestrictedApi` errors in
`MorphismThemeManager.java:330-332`. `testDebugUnitTest` (683 tests) and `assembleDebug` pass.
