<div align="center">

# Swara

**An offline-first music player for Android and iOS, with on-device real-time vocal removal.**

[![Android CI](https://github.com/PRABHUSIDDARTH/SwaraApp/actions/workflows/gradle.yml/badge.svg)](https://github.com/PRABHUSIDDARTH/SwaraApp/actions/workflows/gradle.yml)
[![iOS CI](https://github.com/PRABHUSIDDARTH/SwaraApp/actions/workflows/ios.yml/badge.svg)](https://github.com/PRABHUSIDDARTH/SwaraApp/actions/workflows/ios.yml)
![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-17%2B-000000?logo=apple&logoColor=white)
![Version](https://img.shields.io/badge/version-2.1-informational)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

</div>

---

## Overview

Swara plays the music already on your device. It needs no account and no network connection, and it never sends your library anywhere. The repository contains two native apps and the model they share:

| Component | Path | Stack |
| --- | --- | --- |
| Android app | [`android/`](android) | Java (no Kotlin), Jetpack Media3, Room, Navigation, Glide |
| iOS app | [`ios/`](ios) | SwiftUI, AVFoundation, XcodeGen, `SwaraCore` Swift package |
| Korokae model | [`ml/korokae/`](ml/korokae) | PyTorch training and export of the on-device vocal-removal network |

## Features

- **Local library.** Songs, albums, artists, and folders, indexed from MediaStore on Android and from the app's Documents folder on iOS.
- **Korokae karaoke mode.** A small streaming neural network strips vocals in real time inside the audio pipeline. It renders no files and adds about 23 ms of latency.
- **Karaoke matching.** Swara detects when a library already contains a karaoke or instrumental version of a track.
- **Playlists.** Create, edit, and reorder playlists. Artwork comes from a custom photo or an automatic 2x2 collage of the playlist's songs.
- **Queue and playback.** Drag to reorder, swipe to remove, play next, shuffle, repeat, and a sleep timer with presets or an end-of-song option.
- **Favorites and history.** Includes recently played and most played lists.
- **Search.** Results update as you type and cover songs, albums, artists, and playlists.
- **Background playback.** Lock-screen, notification, and headset controls (Media3 `MediaSessionService` on Android, `MPRemoteCommandCenter` on iOS).
- **Home-screen widgets** (Android). Horizontal and vertical player widgets with artwork, seek, and +/-15 s skip.
- **Themes.** Eight color themes (Swara, Midnight, Lavender, Champagne, Rose, Ocean, Forest, Off-White), each with light, dark, and system modes. Swara calls this design style "Liquid Glass".
- **Metadata editing.** Edit song tags and artwork from inside the app.

## Architecture

### Android

The Android app uses MVVM with a repository layer. All playback state lives in one Media3 service, and the UI observes it through `LiveData`.

```
UI (Fragments, Adapters, Widgets)
        |  observes LiveData
ViewModels (Playback, Library, Playlist, Favorites)
        |                         |
MediaController            Repositories
        |                         |
SwaraPlaybackService       Room AppDatabase (schema v3)
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

### iOS

The SwiftUI app is split into `App`, `Data`, `Playback`, and `UI`. Logic that does not depend on the platform is in the [`SwaraCore`](ios/SwaraCore) Swift package, which builds and tests on Linux as well as macOS. `SwaraCore` covers models, shuffle, sorting, artist identity, karaoke matching, and the Korokae DSP. Korokae runs through an `MTAudioProcessingTap`. See [ios/README.md](ios/README.md) for platform-specific details.

### Korokae model

| Item | Detail |
| --- | --- |
| Transform | STFT with an N_FFT of 1024, a hop of 512, and a sqrt-Hann window |
| Network | Linear 513 to 256 with ReLU, a two-layer causal GRU(256), then Linear 256 to 513 with a sigmoid mask |
| Asset | `android/app/src/main/assets/models/korokae_v1.bin`, a "KRK1" header followed by float32 LE weights |

The iOS app bundles the same asset by reference. A shared parity fixture lets the Java and Swift test suites check that both engines match PyTorch. To retrain the model, follow [ml/korokae/README.md](ml/korokae/README.md).

## Getting Started

### Prerequisites

| Target | Requirements |
| --- | --- |
| Android | JDK 17, Android SDK 36, and a device or emulator on API 24 or later |
| iOS | macOS with Xcode 15 or later, plus [XcodeGen](https://github.com/yonaskolb/XcodeGen) |
| SwaraCore only | Swift 5.9 or later on Linux or macOS |
| Model training | Python 3, `torch`, `numpy`, and `ffmpeg` |

### Android

```bash
git clone https://github.com/PRABHUSIDDARTH/SwaraApp.git
cd SwaraApp/android

./gradlew testDebugUnitTest      # run the JVM unit tests
./gradlew assembleDebug          # build the debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### iOS

```bash
brew install xcodegen
cd SwaraApp/ios
xcodegen generate                # Swara.xcodeproj is generated and git-ignored
open Swara.xcodeproj
```

Before running on a physical device, choose your Apple team under **Signing & Capabilities**. To import music, use the in-app import button, the Files app (**On My iPhone > Swara**), Finder file sharing, or AirDrop.

### Core tests without Xcode

```bash
cd ios/SwaraCore && swift test
```

## Testing and CI

| Workflow | Runner | Steps |
| --- | --- | --- |
| [`gradle.yml`](.github/workflows/gradle.yml) | Ubuntu, JDK 17 | Unit tests, debug APK, Android lint |
| [`ios.yml`](.github/workflows/ios.yml) | Ubuntu and macOS 15 | `SwaraCore` tests on Linux; on macOS, a simulator build, XCTest, and an unsigned `.ipa` artifact |

On Android, the tests cover queue and shuffle logic, search, playlists and artwork resolution, theme contrast, widgets, seek math, and Korokae engine parity. On iOS, `SwaraCoreTests` and `SwaraTests` cover the shared logic, the database layer, and the Korokae engine.

## Project Structure

```
SwaraApp/
|-- android/              Android app (Gradle)
|   `-- app/src/
|       |-- main/         Java sources, resources, Korokae model asset
|       `-- test/         JVM unit tests and Korokae parity fixture
|-- ios/
|   |-- Swara/            SwiftUI app (App, Data, Playback, UI)
|   |-- SwaraCore/        Platform-independent Swift package and tests
|   |-- SwaraTests/       App-hosted XCTest bundle
|   `-- project.yml       XcodeGen spec
|-- ml/korokae/           Model training, data preparation, and export
|-- docs/                 Documentation assets
`-- .github/workflows/    Android and iOS CI
```

## Permissions (Android)

| Permission | Purpose |
| --- | --- |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | Read the local music library. Which one is used depends on the API level. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playback running in the background |
| `POST_NOTIFICATIONS` | Show the media notification on Android 13 and later |
| `WAKE_LOCK` | Keep playback going while the screen is off |

Swara does not request network access for playback.

## Contributing

1. Fork the repository and create a feature branch.
2. Keep the Android code in Java, and put shared iOS logic in `SwaraCore` with tests.
3. Before opening a pull request, run `./gradlew testDebugUnitTest` and `swift test`.
4. Write commit messages in the [Conventional Commits](https://www.conventionalcommits.org/) format, for example `fix(ios): ...` or `feat(android): ...`.

## License

Swara is licensed under the [Apache License 2.0](LICENSE).

The Korokae model is trained on MUSDB18, which is licensed for research use only. Check its terms before you distribute a model trained on it for commercial use.
