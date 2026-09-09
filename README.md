<img width="1918" height="952" alt="Swara App" src="https://github.com/user-attachments/assets/b6fd1711-7e4e-4621-9834-d3b6c854216a" />

# Swara V2 🎵

## Overview
Swara V2 is a modern, high-performance offline Android music player built in Java. Built on Jetpack Media3 ExoPlayer, MediaSession, Room database, Navigation Component, and ViewModel architecture, Swara V2 provides seamless audio playback, dynamic queue management, local playlist customization, favorites management, and real-time search with a premium Royal Purple & Obsidian Gold aesthetic.

## Key Features
- **Media3 & ExoPlayer Integration**: High-performance audio engine supporting background playback, lockscreen controls, and system media notifications via `MediaSession`.
- **MediaStore Discovery**: Automatic device audio indexing with automatic artwork extraction and album/artist aggregation.
- **Modern UI / UX**: Single-Activity architecture with Navigation component, collapsible Mini-Player, bottom sheet Now Playing view, swipe-to-dismiss queue, and custom animations.
- **Favorites & Custom Playlists**: Instant favorite toggling and full playlist CRUD operations powered by Room database.
- **Search & Filter**: Real-time multi-criteria searching across songs, albums, and artists.
- **Play History & Queue Management**: Automatic play history tracking and dynamic queue reordering.

## Tech Stack
- **Language**: Java 11 (Android SDK compile/target 36, minSdk 24)
- **Playback Engine**: Jetpack Media3 1.5.1 (`ExoPlayer`, `MediaSession`, `MediaSessionService`)
- **Architecture**: MVVM with Jetpack `ViewModel`, `LiveData`, and `Navigation`
- **Persistence**: Room Database (`AppDatabase`, `FavoriteDao`, `PlaylistDao`, `PlayHistoryDao`)
- **UI Components**: Material Components, `CoordinatorLayout`, `ConstraintLayout`, `RecyclerView`, `ViewPager2`
- **Image Loading**: Glide 4.16.0 for artwork fetching and caching
- **CI/CD**: GitHub Actions with JDK 17 & Gradle caching

## Architecture Overview
Swara V2 follows a clean, lifecycle-aware architecture:
- **`SwaraPlaybackService`**: A `MediaSessionService` running Jetpack Media3 ExoPlayer as the single source of playback truth.
- **ViewModels**: `PlaybackViewModel`, `LibraryViewModel`, `FavoritesViewModel`, `PlaylistViewModel` decoupling UI state from playback logic.
- **Repositories**: `MusicRepository`, `FavoritesRepository`, `PlaylistRepository`, `PlayHistoryRepository`, and `ArtworkRepository` providing unified data management.
- **Room Storage**: Asynchronous database access using `ExecutorService` background threading.

## Setup and Build
### Requirements
- Android Studio Ladybug or later
- Android SDK: `minSdk` 24, `targetSdk` / `compileSdk` 36
- JDK 17 for builds (source/target compatibility set to Java 11)

### Command-Line Build & Test
```bash
./gradlew test
./gradlew assembleDebug
```

## License
This project is licensed under the Apache-2.0 License. See [LICENSE](LICENSE) for details.
