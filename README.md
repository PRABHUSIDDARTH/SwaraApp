# Swara

## Overview
Swara is an offline Android music player built in Java. It scans songs available on the device, plays audio smoothly in the foreground/background, and lets users save favorites locally.

## Features
- Device song discovery using MediaStore
- Offline playback with `MediaPlayer`
- Background playback using a bound music service
- Song list UI with RecyclerView
- Favorites storage with Room database
- Seek controls for playback progress

## Tech Stack
- **Language:** Java
- **Playback:** Android `MediaPlayer`
- **Service Layer:** Bound Services
- **Persistence:** Room Database
- **UI:** RecyclerView, Material Components

## Architecture Overview
Swara follows a straightforward Android layered flow: activities handle UI interactions, a bound `MusicService` controls playback state with `MediaPlayer`, and Room persists favorite songs. This keeps playback lifecycle handling separate from UI rendering and local data storage.

## Screenshots
Add screenshots under `docs/screenshots/` and replace the placeholder filenames below with your actual image files:

- ![Home Screen](docs/screenshots/home.png)
- ![Now Playing](docs/screenshots/now-playing.png)
- ![Favorites](docs/screenshots/favorites.png)
- ![Queue](docs/screenshots/queue.png)

## Setup and Build
### Requirements
- Android Studio (latest stable recommended)
- Android SDK with:
  - **minSdk:** 24
  - **targetSdk / compileSdk:** 36
- JDK 17 for CI (project source/target compatibility is Java 11)

### Run Locally
1. Clone the repository:
   ```bash
   git clone https://github.com/PRABHUSIDDARTH/SwaraApp.git
   ```
2. Open the project in Android Studio.
3. Let Gradle sync complete.
4. Connect an Android device or start an emulator.
5. Click **Run** to install and launch the app.

### Command-Line Build
```bash
./gradlew test
./gradlew assembleDebug
```

## License
This project is licensed under the Apache-2.0 License. See [LICENSE](LICENSE) for details.
