<img width="1918" height="952" alt="Swara App" src="https://github.com/user-attachments/assets/b6fd1711-7e4e-4621-9834-d3b6c854216a" />

# Swara V2.1

> A modern, offline-first Android music player built entirely in Java — no Kotlin, no compromises.

[![Android CI](https://github.com/PRABHUSIDDARTH/SwaraApp/actions/workflows/gradle.yml/badge.svg)](https://github.com/PRABHUSIDDARTH/SwaraApp/actions/workflows/gradle.yml)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Build & Run](#build--run)
- [Known Issues & Fixes](#known-issues--fixes)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

Swara V2.1 is a production-ready offline Android music player that reads from the device's `MediaStore`,
plays audio through Jetpack Media3 ExoPlayer, and persists user data (playlists, favourites, play
history) with Room. The UI is a **single-activity** architecture driven entirely by the Jetpack
Navigation Component.

The design language is *Royal Purple & Obsidian Gold* — dark-mode first, edge-to-edge, with smooth
Material 3 transitions.

---

## Features

| Feature | Detail |
|---|---|
| **Audio playback** | Jetpack Media3 ExoPlayer with `MediaSessionService` — background playback survives app backgrounding and screen-off |
| **Lock-screen & notification controls** | System media notification with play/pause/skip, artwork, and seek bar via `MediaSession` |
| **MediaStore discovery** | Automatic indexing of all on-device audio files; album art extracted from embedded tags via `ContentResolver` |
| **Mini-Player** | Persistent collapsible bar with live progress line, artwork crossfade animation, and controls |
| **Now Playing** | Full-screen bottom-sheet dialog with swipe-down dismiss gesture, seek bar, repeat/shuffle, sleep timer, equalizer |
| **Queue management** | Drag-to-reorder via `ItemTouchHelper` and swipe-to-remove, backed by `PlaybackViewModel` |
| **Sleep Timer** | Singleton count-down (15, 30, 45, 60 minutes) and automatic **End-of-Song** sleep mode |
| **Library Sorting** | Multi-attribute sorting for songs: Title (A-Z / Z-A), Artist (A-Z), Duration, and Date Added |
| **Equalizer Integration** | System AudioEffect intent integration with fallback |
| **Favourites** | One-tap toggle with Play All & Shuffle buttons, persisted in Room `FavoriteSong` table |
| **Custom Playlists** | Full CRUD — create, rename, delete, add/remove songs, drag reorder, total playlist duration formatting |
| **Play History** | Threshold-based (30s / 40% duration) automatic timestamp recording with a **Clear Play History** option |
| **Search** | Real-time multi-criteria search across songs, albums, artists, and custom playlists |
| **Library tabs** | Songs · Albums · Artists with Play All & Shuffle detail screens |

---

## Architecture

Swara V2 follows the **MVVM + Repository** pattern recommended by Android Jetpack.

```
┌─────────────────────────────────────────────────────────────────────┐
│                          UI Layer                                   │
│  MainActivity (NavHost) ── NavController ── Fragments               │
│  MiniPlayerFragment │ NowPlayingFragment │ QueueFragment            │
│  HomeFragment │ LibraryFragment │ SearchFragment                    │
│  FavoritesFragment │ PlaylistsFragment │ PlaylistDetailFragment      │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ observes LiveData
┌──────────────────────────────▼──────────────────────────────────────┐
│                       ViewModel Layer                               │
│  PlaybackViewModel │ LibraryViewModel │ FavoritesViewModel          │
│  PlaylistViewModel                                                  │
└──────────┬───────────────────────┬──────────────────────────────────┘
           │ MediaController       │ Repository calls
┌──────────▼──────────┐  ┌────────▼─────────────────────────────────┐
│ SwaraPlaybackService│  │           Repository Layer                │
│ (MediaSessionService│  │  MusicRepository    │ ArtworkRepository   │
│  + ExoPlayer)       │  │  FavoritesRepository│ PlaylistRepository  │
└─────────────────────┘  │  PlayHistoryRepository                    │
                         └──────────────────┬──────────────────────────┘
                                            │
                         ┌──────────────────▼──────────────────────────┐
                         │              Data Layer                     │
                         │  Room AppDatabase                           │
                         │  FavoriteDao │ PlaylistDao │ PlayHistoryDao │
                         │  MediaStore ContentResolver                 │
                         └─────────────────────────────────────────────┘
```

### Key Design Decisions

- **Single-Activity**: `MainActivity` is the sole `Activity`. All screen transitions are
  `FragmentTransaction`s managed by `NavController`. The back stack is owned by Navigation Component.

- **NavHostFragment retrieval**: `Navigation.findNavController(Activity, id)` is **incompatible**
  with `FragmentContainerView` hosts (Navigation ≥ 2.3). The `NavController` is attached to the
  `NavHostFragment`'s child view, not the container. `MainActivity` and `MiniPlayerFragment` both
  use `getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)` to obtain the
  `NavController` synchronously after `setContentView()`.

- **No Kotlin**: The entire codebase is Java 11. No KTX extensions, no Safe Args plugin (which
  generates Kotlin), no coroutines.

- **Background playback**: `SwaraPlaybackService` extends `MediaSessionService`. The
  `foregroundServiceType="mediaPlayback"` manifest declaration satisfies Android 14's
  foreground-service restrictions.

- **Artwork loading**: `ArtworkHelper` resolves album art URIs from `MediaStore` and loads them
  into `ImageView`s via Glide with disk-LRU caching.

---

## Tech Stack

| Component | Library | Version |
|---|---|---|
| Language | Java | 11 (source/target) |
| Min SDK / Target SDK | Android | 24 / 36 |
| Build toolchain | Android Gradle Plugin | 9.2.0 |
| Playback engine | Jetpack Media3 ExoPlayer | 1.4.1 |
| Media session | Jetpack Media3 MediaSession | 1.4.1 |
| Architecture | Lifecycle ViewModel + LiveData | 2.8.7 |
| Navigation | Navigation Component (fragment + ui) | 2.8.9 |
| Persistence | Room Runtime + Compiler | 2.6.1 |
| Image loading | Glide | 4.16.0 |
| UI | Material Components | 1.12.0 |
| UI | ConstraintLayout | 2.2.1 |
| UI | RecyclerView | 1.4.0 |
| UI | ViewPager2 | 1.1.0 |
| UI | Core SplashScreen | 1.0.1 |
| Testing | JUnit 4 | 4.13.2 |
| Testing (Android) | AndroidX Test / Espresso | 1.2.1 / 3.6.1 |
| CI | GitHub Actions | — |

---

## Project Structure

```
SwaraApp/
├── app/src/main/
│   ├── AndroidManifest.xml
│   └── java/com/psthetech/swara/
│       ├── SwaraApplication.java           # Application entry point
│       ├── service/
│       │   └── SwaraPlaybackService.java   # Media3 MediaSessionService + ExoPlayer
│       ├── data/
│       │   ├── db/
│       │   │   ├── AppDatabase.java        # Room database singleton
│       │   │   ├── dao/                    # FavoriteDao, PlaylistDao, PlayHistoryDao
│       │   │   └── entity/                # FavoriteSong, Playlist, PlaylistSong, PlayHistory
│       │   └── repository/                # MusicRepository, FavoritesRepository,
│       │                                  # PlaylistRepository, PlayHistoryRepository,
│       │                                  # ArtworkRepository
│       ├── domain/model/                  # Song, Album, Artist (pure Java POJOs)
│       ├── ui/
│       │   ├── MainActivity.java          # Single Activity — NavHost, BottomNav, MiniPlayer
│       │   ├── adapter/                   # SongAdapter, AlbumAdapter, ArtistAdapter,
│       │   │                              # PlaylistAdapter, QueueAdapter, LibraryPagerAdapter
│       │   ├── home/HomeFragment.java
│       │   ├── library/                   # LibraryFragment, SongsFragment, AlbumsFragment,
│       │   │                              # ArtistsFragment, AlbumDetailFragment, ArtistDetailFragment
│       │   ├── search/SearchFragment.java
│       │   ├── favorites/FavoritesFragment.java
│       │   ├── playlists/                 # PlaylistsFragment, PlaylistDetailFragment
│       │   ├── queue/QueueFragment.java
│       │   ├── nowplaying/NowPlayingFragment.java
│       │   ├── miniplayer/MiniPlayerFragment.java
│       │   └── viewmodel/                 # PlaybackViewModel, LibraryViewModel,
│       │                                  # FavoritesViewModel, PlaylistViewModel
│       └── util/
│           ├── ArtworkHelper.java         # Glide-backed artwork loading
│           ├── PermissionHelper.java      # READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE
│           └── TimeFormatter.java         # ms → mm:ss formatting
├── app/src/main/res/
│   ├── layout/                           # activity_main, fragment_*, item_*
│   ├── navigation/nav_graph.xml          # Single nav graph — all destinations
│   ├── menu/menu_bottom_nav.xml          # Bottom nav items (IDs match nav graph)
│   └── values/                           # colors, strings, themes, dimens
├── gradle/libs.versions.toml             # Version catalog
├── gradle.properties                     # org.gradle.java.home, JVM args
└── .github/workflows/gradle.yml          # CI: test + assembleDebug on every push/PR
```

---

## Build & Run

### Prerequisites

| Requirement | Minimum version |
|---|---|
| Android Studio | Ladybug (2024.2) or later |
| JDK | 17+ with full JDK tools (not just JRE) |
| Android SDK | compileSdk 36, minSdk 24 |
| Android device / emulator | API 24+ |

> **`jlink` requirement:** AGP 9.x + compileSdk 36 generates a JDK image during compilation and
> requires the `jlink` tool, which ships with a **full JDK** but not with a standalone JRE.
> If `./gradlew` fails with:
> ```
> jlink executable … does not exist
> ```
> set `org.gradle.java.home` in `gradle.properties` to point at your full JDK installation, e.g.:
> ```properties
> org.gradle.java.home=/usr/lib/jvm/java-17-openjdk
> ```
> The project already ships this property configured for the build environment.

### Clone

```bash
git clone git@github.com:PRABHUSIDDARTH/SwaraApp.git
cd SwaraApp
```

### Command-line build

```bash
# Run unit tests
./gradlew test

# Build debug APK
# Output: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

### Install on a connected device or emulator

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Open in Android Studio

1. **File → Open** → select the `SwaraApp/` directory.
2. Wait for the Gradle sync to complete.
3. Select the `app` run configuration and press **Run ▶**.

### Runtime permissions

On first launch Swara requests **`READ_MEDIA_AUDIO`** (API 33+) or **`READ_EXTERNAL_STORAGE`**
(API ≤ 32). The permission must be granted to allow `MediaStore` to discover on-device audio files.

---

## Known Issues & Fixes

### `IllegalStateException: Activity does not have a NavController set on nav_host_fragment`

**Observed on:** Samsung Galaxy S21+ (Android 13/14), Navigation Component 2.8.9

**Root cause:** `Navigation.findNavController(Activity, @IdRes int)` is incompatible with
`FragmentContainerView` hosts when using Navigation Component ≥ 2.3. The `NavController` is
attached to the `NavHostFragment`'s child view, not to the container view itself. Calling the
Activity-scoped lookup immediately after `setContentView()` finds no controller and throws.

**Fix applied** — retrieve the `NavController` via `FragmentManager` instead:

```java
// MainActivity.java — correct pattern for FragmentContainerView hosts (Navigation ≥ 2.3)
NavHostFragment navHostFragment = (NavHostFragment)
        getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
navController = navHostFragment.getNavController();
```

The same pattern was corrected in `MiniPlayerFragment.java`, which also navigated using the
incompatible `Navigation.findNavController(requireActivity(), id)` overload.

---

## Contributing

1. Fork the repository and create a feature branch from `main`.
2. Follow the existing code style: Java 11, no Kotlin, `@NonNull`/`@Nullable` annotations,
   Javadoc on public API.
3. Keep `MainActivity` and `SwaraPlaybackService` decoupled — all playback state flows through
   `PlaybackViewModel → MediaController`.
4. Run `./gradlew test` before opening a PR. All unit tests must pass.
5. Open a pull request with a clear description of what changed and why.

### Commit message convention

```
<type>(<scope>): <short summary>

type  = fix | feat | refactor | test | docs | ci | chore
scope = ui | nav | playback | db | repo | util | build | readme
```

---

## License

Copyright 2024 PRABHUSIDDARTH

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full text.
