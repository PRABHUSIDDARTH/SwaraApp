# 🎵 Swara V2.2 — Premium Offline Android Music Player

> **An exquisite, offline-first Android music player engineered purely in modern Java — zero Kotlin, zero bloat, uncompromising craftsmanship.**

[![Android CI](https://github.com/PRABHUSIDDARTH/SwaraApp/actions/workflows/gradle.yml/badge.svg)](https://github.com/PRABHUSIDDARTH/SwaraApp/actions/workflows/gradle.yml)
[![API](https://img.shields.io/badge/API-24%2B%20(Android%207.0%2B)-brightgreen.svg?style=flat-square)](https://android-arsenal.com/api?level=24)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-blue.svg?style=flat-square)](https://developer.android.com/about/versions/14)
[![Media3](https://img.shields.io/badge/Jetpack-Media3%201.2.1-orange.svg?style=flat-square)](https://developer.android.com/media/media3)
[![Room](https://img.shields.io/badge/Room-v3%20Migration-purple.svg?style=flat-square)](https://developer.android.com/training/data-storage/room)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](LICENSE)

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Design System & Themes](#-design-system--themes)
  - [Liquid Glass Morphism](#liquid-glass-morphism)
  - [The 7 ColorThemes](#the-7-colorthemes)
  - [Light Mode & Dark Mode Semantic Tokens](#light-mode--dark-mode-semantic-tokens)
- [Key Features](#-key-features)
- [Core Architecture](#-core-architecture)
  - [MVVM + Repository Layering](#mvvm--repository-layering)
  - [Authoritative Media3 Audio Engine](#authoritative-media3-audio-engine)
  - [Room Database & Schema v3 Migration](#room-database--schema-v3-migration)
- [Specialized Subsystems](#-specialized-subsystems)
  - [Authoritative Playlist Artwork Engine](#authoritative-playlist-artwork-engine)
  - [Themed Dialog System & Entrance Animations](#themed-dialog-system--entrance-animations)
  - [Accessible Current-Playing Indicator](#accessible-current-playing-indicator)
  - [RecyclerView Recycling Safety Contract](#recyclerview-recycling-safety-contract)
- [Project Directory Structure](#-project-directory-structure)
- [Building & Installation](#-building--installation)
- [Test Suite & Quality Verification](#-test-suite--quality-verification)
- [License](#-license)

---

## 🌟 Overview

**Swara** is a production-grade offline music player for Android designed to deliver an audiophile-grade playback experience wrapped in an authentic **Liquid Glass** aesthetic. 

Built strictly using modern Java 17, Swara leverages Android Jetpack architecture components (Media3, Navigation, Room, LiveData, ViewModel, DiffUtil) without relying on heavy third-party framework overhead. Every animation, dialog, color token, and RecyclerView cell is crafted with strict performance and memory recycling guarantees.

---

## 🎨 Design System & Themes

### Liquid Glass Morphism
Swara’s signature interface is built on **Liquid Glass**:
- Translucent, soft-lit surfaces with subtle accent strokes.
- Controlled background blurs and atmospheric glows.
- Authentic materials that preserve content integrity (e.g. user album and playlist artwork are never artificially tinted).
- Elevation and corner radii calibrated dynamically for each device DPI.

### The 7 ColorThemes
Swara V2 introduces seven curated color themes, each offering tailored Light Mode and Dark Mode palettes:

| ColorTheme | Accent | Mood / Identity | Light Accent & Distinction | Dark Obsidian Tone |
|---|---|---|---|---|
| **SWARA** | Royal Gold (`#E6A23C`) | Signature imperial purple & obsidian gold | Warm Bronze Gold (`#946E14`) | Deep Obsidian Violet (`#0D0B14`) |
| **MIDNIGHT** | Sky Cyan (`#38BDF8`) | Deep navy glass, modern cyber aesthetic | Ocean Azure (`#0284C7`) | Abyss Slate (`#070B12`) |
| **LAVENDER** | Amethyst (`#C084FC`) | Soft violet dream, royal and calm | Royal Purple (`#7E22CE`) | Nightshade Black (`#0C0714`) |
| **CHAMPAGNE**| Amber Gold (`#FBBF24`) | Warm candlelight, vintage sophistication | Rich Warm Amber (`#B45309`) | Smoked Espresso (`#120D06`) |
| **ROSE** | Blush Rose (`#F472B6`) | Velvet crimson, high contrast & luxury | Deep Crimson Rose (`#C2185B`) | Midnight Wine (`#14070B`) |
| **OCEAN** | Seafoam Teal (`#2DD4BF`) | Coastal depths, crisp and refreshing | Deep Sea Teal (`#0D9488`) | Mariana Obsidian (`#051012`) |
| **FOREST** | Emerald Mint (`#34D399`) | Lush botanical, serene organic tones | Deep Pine Emerald (`#059669`)| Deep Woods Black (`#051209`) |

### Light Mode & Dark Mode Semantic Tokens
Every screen element references dynamic semantic tokens from `DesignTokens` and `MorphismThemeManager`.
- **WCAG AAA Compliance:** Primary text contrast exceeds **7.0:1** on both light and dark surfaces across all seven themes.
- **Selective Hierarchy:** Accents are reserved for primary calls-to-action (e.g. "Play All", dialog confirms, active sliders), leaving body text crisp and readable.
- **Surface Variant Blending:** Light mode surfaces use soft pastel variants (`#EDE6F7`, `#FCE4EC`, `#DCF0E3`) rather than harsh stark whites, reducing eye strain.

---

## ⚡ Key Features

- **Media3 Playback Core:** Continuous background playback powered by `MediaSessionService` and ExoPlayer. Survives device orientation, lock screen, and process backgrounding.
- **Lockscreen & Notification Media Controls:** High-resolution notification seekbar, artwork, playback actions, and favorite toggling via Android `MediaSession`.
- **MediaStore Discovery:** Automatic indexing of on-device audio files with real-time metadata parsing and album art caching.
- **Collapsible Mini-Player:** Edge-to-edge floating glass bar with live progress tracker, play/pause toggle, and gesture-driven bottom sheet expansion.
- **Now Playing Experience:** Full-screen dialog with swipe-down dismissal, fluid waveform/progress scrubbing, repeat/shuffle modes, equalizer intent launch, and sleep timer.
- **Playlist Management & Custom Artwork:** Full CRUD support, track reordering with `ItemTouchHelper`, custom photo picker artwork, automatic 2x2 collage generation, and instant runtime UI refresh.
- **Queue System:** Live queue with drag-to-reorder, swipe-to-delete, play next, and queue clearing.
- **Sleep Timer:** Countdown presets (15, 30, 45, 60 minutes) or intelligent **End-of-Song** sleep mode.
- **Multi-Attribute Search:** Real-time multi-criteria filtering across songs, albums, artists, and user playlists.
- **Favorite Songs:** Fluid heart bounce micro-animation with immediate Room persistence and quick Play/Shuffle all.

---

## 🏛 Core Architecture

### MVVM + Repository Layering

```
┌──────────────────────────────────────────────────────────────────────────┐
│                                 UI Layer                                 │
│  MainActivity ── NavHostFragment ── NavController                        │
│  HomeFragment │ LibraryFragment │ SearchFragment │ FavoritesFragment     │
│  PlaylistsFragment │ PlaylistDetailFragment │ QueueFragment              │
│  MiniPlayerFragment │ NowPlayingFragment │ SettingsFragment              │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ observes LiveData
┌────────────────────────────────────▼─────────────────────────────────────┐
│                             ViewModel Layer                              │
│  PlaybackViewModel │ LibraryViewModel │ FavoritesViewModel               │
│  PlaylistViewModel                                                       │
└────────────┬───────────────────────────────────┬─────────────────────────┘
             │ MediaController                   │ Repository calls
┌────────────▼──────────────┐      ┌─────────────▼─────────────────────────┐
│   SwaraPlaybackService    │      │           Repository Layer            │
│  (MediaSessionService +   │      │  MusicRepository │ ArtworkRepository  │
│   ExoPlayer Engine)       │      │  PlaylistRepository │ FavRepository   │
└───────────────────────────┘      │  PlaylistArtworkStore │ HistoryRepo   │
                                   └─────────────┬─────────────────────────┘
                                                 │
                                   ┌─────────────▼─────────────────────────┐
                                   │              Data Layer               │
                                   │  Room AppDatabase (v3 Schema)         │
                                   │  PlaylistDao │ FavoriteDao │ History  │
                                   │  MediaStore ContentResolver           │
                                   └───────────────────────────────────────┘
```

### Authoritative Media3 Audio Engine
- Playback state is strictly centralized in `SwaraPlaybackService`.
- All fragments and adapters observe `PlaybackViewModel.getCurrentSong()` and `PlaybackViewModel.getPlaybackState()`.
- State updates dispatch atomically; no UI component maintains its own decoupled playing state.

### Room Database & Schema v3 Migration
- Seamless migration from schema version 2 to 3:
  ```sql
  ALTER TABLE playlists ADD COLUMN artworkPath TEXT DEFAULT NULL;
  ```
- Added `modifiedAt` timestamp column with automatic `touchModifiedAt(playlistId)` on every artwork and song list change to facilitate `DiffUtil` detection and Glide cache busting.

---

## 🛠 Specialized Subsystems

### Authoritative Playlist Artwork Engine
Every playlist UI surface (Home cards, Playlists tab, Playlist Detail header, Search results) resolves artwork via `PlaylistArtworkHelper`:

$$\mathbf{Priority\ 1:\ Custom\ Artwork} \longrightarrow \mathbf{Priority\ 2:\ 2\times2\ Collage} \longrightarrow \mathbf{Priority\ 3:\ Fallback\ Icon}$$

1. **Custom Artwork:** User-selected image stored in internal application sandbox (`filesDir/playlist_artwork/<id>.jpg`). Never tinted or modified.
2. **Dynamic 2x2 Collage:** Automatically constructed from the album art of the first four songs in the playlist.
3. **Swara Fallback:** High-definition vector fallback icon (`ic_playlist`).
4. **Instant Invalidation:** Handled through Glide signature keys:
   ```java
   .signature(new ObjectKey(file.lastModified() + "_" + playlist.modifiedAt))
   ```
   Ensures changes are immediately visible across all screens without restarting the app.

### Themed Dialog System & Entrance Animations
Centralized in `ThemedDialogHelper`:
- **Liquid Glass Container:** Dynamically styled background with rounded corners, elevated theme background, and accent-derived stroke.
- **Visual Input Focus:** Input text container with responsive focus listeners that highlight the border with the active theme accent on user touch.
- **Entrance Animation:** Subtle scale ($0.95 \to 1.0$) and opacity ($0 \to 1.0$) entrance transition using a decelerate curve over 220ms.
- **Button Contrast:** Positive actions render with solid theme accent and high-contrast text; negative actions render in muted secondary typography.

### Accessible Current-Playing Indicator
Designed to overcome visibility issues on high-brightness AMOLED displays:
- **Light Mode Highlight:** Blends 18% theme accent into `surfaceVariantColor` ($>1.32:1$ card separation from background, $>10:1$ WCAG AAA text contrast).
- **Light Mode Glow Stroke:** High-definition 55% alpha (`0x8C`) border stroke.
- **Dark Mode Highlight:** 14% accent blended into `surfaceElevatedColor` with soft 31% alpha glow.
- **Non-Motion Accessible Cue:** The playing track title automatically switches to `Typeface.BOLD`, providing instant identification even under extreme ambient sunlight.
- **State Invariance:** Indicator remains clearly visible when playback is paused.

### RecyclerView Recycling Safety Contract
To prevent visual leakage, ghost glows, or stale artwork when scrolling rapidly:
```java
@Override
public void onViewRecycled(@NonNull ViewHolder holder) {
    super.onViewRecycled(holder);
    // 1. Clear Glide image decoding tasks
    Glide.with(holder.itemView).clear(holder.ivArtwork);
    holder.ivArtwork.setImageDrawable(null);
    
    // 2. Reset view transformations
    holder.itemView.setScaleX(1.0f);
    holder.itemView.setScaleY(1.0f);
    holder.itemView.setAlpha(1.0f);
    holder.itemView.setTranslationX(0f);
    holder.itemView.setTranslationY(0f);
    
    // 3. Clear playback highlights & reset typography
    holder.itemView.setBackground(defaultBackgroundDrawable);
    holder.tvTitle.setTypeface(Typeface.DEFAULT);
}
```

---

## 📂 Project Directory Structure

```
SwaraApp/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/psthetech/swara/
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/              # Room database, DAOs, entities, migrations
│   │   │   │   │   ├── preference/      # ThemePreferences, ColorThemes
│   │   │   │   │   └── repository/      # MusicRepo, PlaylistRepo, PlaylistArtworkStore
│   │   │   │   ├── domain/              # Models (Song, Album, Artist, ColorTheme)
│   │   │   │   ├── service/             # SwaraPlaybackService (Media3 MediaSessionService)
│   │   │   │   ├── ui/
│   │   │   │   │   ├── adapter/         # SongAdapter, PlaylistAdapter, QueueAdapter, SearchAdapter
│   │   │   │   │   ├── home/            # HomeFragment, HomePlaylistAdapter
│   │   │   │   │   ├── library/         # SongsFragment, AlbumsFragment, ArtistsFragment
│   │   │   │   │   ├── playlists/       # PlaylistsFragment, PlaylistDetailFragment, Dialogs
│   │   │   │   │   ├── nowplaying/      # NowPlayingFragment, SleepTimerDialog
│   │   │   │   │   ├── miniplayer/      # MiniPlayerFragment
│   │   │   │   │   ├── theme/           # DesignTokens, MorphismThemeManager, ThemedDialogHelper
│   │   │   │   │   └── viewmodel/       # Playback, Library, Playlist, Favorites ViewModels
│   │   │   │   └── util/                # PlaylistArtworkHelper, FavoriteAnimationHelper
│   │   │   └── res/
│   │   │       ├── layout/              # All Liquid Glass XML screens & custom dialogs
│   │   │       ├── values/              # Semantic colors, strings, themes
│   │   │       └── values-night/        # Obsidian night mode themes
│   │   └── test/java/com/psthetech/swara/
│   │       ├── PlaylistPolishPass2Test.java       # 12-criteria comprehensive test suite
│   │       ├── ThemeContrastAndSemanticsTest.java # WCAG AA/AAA contrast validation
│   │       ├── PlaylistArtworkTest.java           # Artwork priority, collage & cache invalidation
│   │       ├── MorphismThemeTest.java             # ColorTheme & Liquid Glass tokens test
│   │       └── SearchLogicTest.java               # Search filtering verification
└── gradle/                                        # Gradle wrapper and version catalog
```

---

## 🚀 Building & Installation

### Prerequisites
- **JDK:** Java 17 or higher
- **Android SDK:** Platform 34 (Android 14)
- **Build Tools:** 34.0.0
- **Gradle:** 8.4+

### Command-Line Build
```bash
# Clone repository
git clone https://github.com/PRABHUSIDDARTH/SwaraApp.git
cd SwaraApp

# Run complete unit test suite
./gradlew test

# Build debug APK
./gradlew assembleDebug

# Install directly to connected device (e.g. Samsung Galaxy S21+)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Test Suite & Quality Verification

Swara V2 includes extensive unit testing across all layers:
- **`PlaylistPolishPass2Test`**: Verifies 12 critical criteria:
  1. Multi-tier artwork resolution priority
  2. Custom artwork precedence over collage
  3. Dynamic collage generation fallback
  4. Home playlist adapter wiring & album ID lookup
  5. Artwork refresh and Glide signature cache busting
  6. Light Mode playback colors and contrast ratios ($>1.32:1$)
  7. Dark Mode obsidian playback colors and luminance
  8. All 7 ColorThemes contrast compliance
  9. Create Playlist theme semantics and button contrast
  10. Playlist Detail visual hierarchy (Play vs Shuffle)
  11. RecyclerView cell recycling safety contract
  12. Media3 playing/paused state lifecycle invariants
- **`ThemeContrastAndSemanticsTest`**: Formulaic WCAG AAA verification across all light and dark palettes.
- **`PlaylistArtworkTest`**: File lifecycle, collision avoidance, and disk cleanup verification.

All tests run cleanly via `./gradlew test` with zero warnings or failures.

---

## 📄 License

```
Copyright (C) 2026 PRABHUSIDDARTH (PSTHEECH)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
