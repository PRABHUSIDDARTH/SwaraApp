<div align="center">

# Swara

**An offline-first music player for Android, with on-device real-time vocal removal.**

[![Android CI](https://github.com/PRABHUSIDDARTH/SwaraApp/actions/workflows/gradle.yml/badge.svg)](https://github.com/PRABHUSIDDARTH/SwaraApp/actions/workflows/gradle.yml)
![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84?logo=android&logoColor=white)
![Version](https://img.shields.io/badge/version-2.1-informational)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

</div>

---

## Overview

Swara plays the music already on your device. It needs no account and no network connection, and it never sends your library anywhere. The repository contains the Android app and its vocal-removal model:

| Component | Path | Stack |
| --- | --- | --- |
| Android app | [`app/`](app) | Java (no Kotlin), Jetpack Media3, Room, Navigation, Glide |
| Korokae model | [`ml/korokae/`](ml/korokae) | PyTorch training and export of the on-device vocal-removal network |

## Features

- **Local library.** Songs, albums, and artists, indexed from MediaStore and refreshed automatically when files are added or removed. Clips under 30 seconds are hidden by default (configurable in Settings).
- **Korokae karaoke mode.** A small streaming neural network strips vocals in real time inside the audio pipeline. It renders no files and adds about 23 ms of latency.
- **Karaoke matching.** Swara detects when a library already contains a karaoke or instrumental version of a track.
- **Playlists.** Create, edit, and reorder playlists. Artwork comes from a custom photo or an automatic 2x2 collage of the playlist's songs.
- **Queue and playback.** Drag to reorder, swipe to remove, play next, shuffle, repeat, and a sleep timer with presets or an end-of-song option. The queue and position are restored after the app is closed.
- **Favorites and history.** Favorites plus a recently played list.
- **Search.** Results update as you type and cover songs, albums, artists, and playlists.
- **Background playback.** Lock-screen, notification, and headset controls (Media3 `MediaSessionService`).
- **Home-screen widgets.** Horizontal and vertical player widgets with artwork, seek, and +/-15 s skip.
- **Themes.** Eight color themes (Swara, Midnight, Lavender, Champagne, Rose, Ocean, Forest, Off-White), each with light, dark, and system modes. Swara calls this design style "Liquid Glass".
- **Vibe Mode.** Starts from the mood of the song you're playing and lifts it gently, song by song. Mood is estimated on the device from the audio itself.
- **Autoplay.** When the queue runs out, Swara keeps the music going with songs from your library that fit what you're hearing.
- **"For you" mixes.** Mixes on Home built from your own library and listening habits. They are computed on the device, and nothing leaves it.
- **Swara Wrapped.** Monthly and yearly recaps of your listening: minutes, top songs, artists and album, mood mix, habits, discoveries, and a listening personality. The recaps play as story slides you can share as images or save as a short video. A stats screen on Home shows this month, this year, or all time whenever you like. Everything is counted on the device.
- **Listening history backup.** Settings can save your Wrapped history to a file and restore it on a new phone. Songs are matched by title and artist.
- **Metadata editing.** Edit song tags and artwork from inside the app.

## Architecture

The app uses MVVM with a repository layer. All playback state lives in one Media3 service, and the UI observes it through `LiveData`.

```
UI (Fragments, Adapters, Widgets)
        |  observes LiveData
ViewModels (Playback, Library, Playlist, Favorites)
        |                         |
MediaController            Repositories
        |                         |
SwaraPlaybackService       Room AppDatabase (schema v4)
(ExoPlayer + Korokae       MediaStore ContentResolver
 AudioProcessor)           Artwork stores
```

| Package | Responsibility |
| --- | --- |
| `data` | Room database, DAOs, repositories, preferences, artwork storage |
| `domain` | Models such as `Song`, `Album`, `Artist`, and `ColorTheme` |
| `service` | `SwaraPlaybackService` (Media3 `MediaSessionService`) |
| `korokae` | `KorokaeEngine`, model loader, and Media3 `AudioProcessor` |
| `ui` | Fragments, adapters, view models, and the theme system |
| `widget` | Home-screen App Widget providers |
| `util` | Shared helpers |

### Korokae model

| Item | Detail |
| --- | --- |
| Transform | STFT with an N_FFT of 1024, a hop of 512, and a sqrt-Hann window |
| Network | Linear 513 to 256 with ReLU, a two-layer causal GRU(256), then Linear 256 to 513 with a sigmoid mask |
| Asset | `app/src/main/assets/models/korokae_v1.bin`, a "KRK1" header followed by float32 LE weights |

[`MODEL_LICENSE.md`](app/src/main/assets/models/MODEL_LICENSE.md) covers the Korokae weights, which carry non-commercial MUSDB18 terms. The recommender behind Vibe Mode ships no trained weights; it adapts to your listening on the device.

A parity fixture lets the Java tests check that the engine matches PyTorch. To retrain the model, follow [ml/korokae/README.md](ml/korokae/README.md).

## Getting Started

### Prerequisites

| Target | Requirements |
| --- | --- |
| Android | JDK 17, Android SDK 36, and a device or emulator on API 24 or later |
| Model training | Python 3, `torch`, `numpy`, and `ffmpeg` |

### Build

```bash
git clone https://github.com/PRABHUSIDDARTH/SwaraApp.git
cd SwaraApp

./gradlew testDebugUnitTest      # run the JVM unit tests
./gradlew assembleDebug          # build the debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Testing and CI

| Workflow | Runner | Steps |
| --- | --- | --- |
| [`gradle.yml`](.github/workflows/gradle.yml) | Ubuntu, JDK 17 | Unit tests, debug APK, Android lint |

The tests cover queue and shuffle logic, search, playlists and artwork resolution, theme contrast, widgets, seek math, and Korokae engine parity.

## Project Structure

```
SwaraApp/
|-- app/src/
|   |-- main/             Java sources, resources, Korokae model asset
|   `-- test/             JVM unit tests and Korokae parity fixture
|-- gradle/               Gradle wrapper and version catalog
|-- ml/korokae/           Model training, data preparation, and export
|-- docs/                 Documentation assets
`-- .github/workflows/    Android CI
```

## Permissions

| Permission | Purpose |
| --- | --- |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | Read the local music library. Which one is used depends on the API level. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playback running in the background |
| `POST_NOTIFICATIONS` | Show the media notification, and one notice when a new Wrapped recap is ready, on Android 13 and later |
| `WAKE_LOCK` | Keep playback going while the screen is off |

Swara does not request network access for playback.

## Contributing

1. Fork the repository and create a feature branch.
2. Keep the code in Java.
3. Before opening a pull request, run `./gradlew testDebugUnitTest`.
4. Write commit messages in the [Conventional Commits](https://www.conventionalcommits.org/) format, for example `fix(player): ...` or `feat(widget): ...`.

## License

Swara is licensed under the [Apache License 2.0](LICENSE).

The Korokae model is trained on MUSDB18, which is licensed for research use only. Check its terms before you distribute a model trained on it for commercial use.
