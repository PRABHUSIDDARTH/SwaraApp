# Deck widget and Deck standby: design

Approved 2026-11-26 ("widget + A"), including the widget limits below. Items marked
**(builder's call)** were not discussed and may change after testing.

## Intent

Bring the Deck look to the home screen, and give a deck with animation and controls for when the
phone is not in use. Android offers apps no public way to draw on the Always On Display, so
"standby" is Swara's own dim full-screen view with the screen still on.

## Approaches considered

| | Trade-off | Outcome |
|---|---|---|
| A. Own standby screen over the lock screen | Works any time; screen stays on, so more battery | **Chosen** |
| B. Screensaver (`DreamService`) | Official, but only while charging idle or docked | Not chosen |
| C. System media controls on AOD / lock screen | No code, but not Deck | Already there, unchanged |

## Widget

A third widget, "Swara Deck". The two existing widget looks are unchanged.

- Matte body with screws, the reel with a still cover, title and artist, self-ticking elapsed
  time, a progress bar, and keys: previous, play/pause, next, favourite, Karaoke, standby.
- Colours from `DesignTokens`. Rendering reuses the Deck views drawn into bitmaps.
- **Spin:** `RemoteViews` cannot run custom views. While playing, two indeterminate
  `ProgressBar`s with rotating drawables (tape windows, marker dot) turn over a still reel and
  cover. Needs Android 12 for theme tinting; older versions show a still reel. With "remove
  animations" the system does not animate it. **This must be checked on the phone's launcher.**
- No spectrum: a widget cannot be updated fast enough, and fake bars are not wanted.
- Reuses the existing widget broadcast actions and the service's one-second tick.
- One layout, 4x3 cells by default, resizable **(builder's call)**.

## Standby screen

`DeckStandbyActivity`: pure black, forced-dark Deck colours, reel, live spectrum, dot-matrix
time, fader, previous / play / next, title and artist, and a close button. Portrait and
landscape.

- **Entry (builder's call):** a standby button on the Deck player, and the widget's standby key.
  It never opens by itself.
- Shows over the lock screen, so it is what you see on waking while it is open. It offers
  playback controls only: nothing in it opens the rest of the app or edits the library.
- Keeps the screen on at low brightness **(builder's call: 10%)**; the power button still turns
  the screen off. Animation and the audio tap stop while the screen is off.
- Closes by itself after 15 minutes without a touch, or after 1 minute paused **(builder's call)**.
- Burn-in: the whole content moves a few dp once a minute.
- "Remove animations": still reel, flat bars, no tap, as in the Deck player.

## Out of scope

Drawing on the Always On Display, opening standby automatically, a screensaver, Phase 2 controls.

## Testing

Unit tests for the pure policy (burn-in offsets, timeout) and the widget state mapping.
`testDebugUnitTest`, `assembleDebug`, `lintDebug` must pass. A temporary Robolectric harness for
rendering and lifecycle checks. The user tests the APK on a Galaxy S21+ before any push.

## Left to the builder

Class layout, sizes and drawing details.
