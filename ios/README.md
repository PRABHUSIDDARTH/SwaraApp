# Swara for iOS

Native SwiftUI app. It is a second codebase next to the Android app in `../android`; both share the Korokae model file at `android/app/src/main/assets/models/korokae_v1.bin`.

## Layout

- `project.yml`: XcodeGen spec (source of truth for the Xcode project; `Swara.xcodeproj` is generated and git-ignored).
- `Swara/`: the app (App, Data, Playback, UI).
- `SwaraCore/`: platform-independent Swift package (models, shuffle, sorting, artist identity, karaoke matcher, play-history rule, Korokae DSP). Builds and tests on Linux and macOS.
- `SwaraTests/`: app-hosted XCTest bundle.

## Build

Requirements: a Mac with Xcode 15 or later and XcodeGen.

    brew install xcodegen
    cd ios
    xcodegen generate
    open Swara.xcodeproj

Set your Apple team in Signing & Capabilities before running on a device. Core logic can be tested anywhere Swift is installed:

    cd ios/SwaraCore && swift test

## Music on iOS

iOS has no system-wide media library for arbitrary files, so Swara owns a folder in its Documents directory. It appears in Files as "On My iPhone > Swara". Add files through the import button, Files, Finder file sharing, or AirDrop. Playback works fully offline.

## Feature parity with Android

| Feature | iOS |
| --- | --- |
| Library, albums, artists, search | Yes |
| Playlists (custom artwork, 2x2 collage) | Yes |
| Favorites, play history, recently played | Yes |
| Queue, shuffle, repeat, sleep timer | Yes |
| Korokae vocal removal (live tap) | Yes (44.1/48 kHz stereo float PCM; other formats play unprocessed) |
| Karaoke version matching | Yes |
| Themes and light/dark/system mode | Yes |
| Lock-screen and remote controls | Yes |

## Known limits

- Metadata edits are stored as Swara overrides and are not written into the audio file's tags.
- No home-screen widget and no system equalizer intent yet.
- Korokae requires the audio track to be decodable by AVFoundation in the supported PCM format.
- The app has not been compiled in this repository's Linux environment; the macOS CI job is the first full build.
