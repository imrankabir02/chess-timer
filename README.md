# Chess Clock ⏱️

A beautiful, modern, and material-designed Chess Clock application for Android. Built with Jetpack Compose, Kotlin, and Room Database to keep track of your chess matches with customizable time control presets.

[![Android CI/CD Pipeline](https://github.com/imrankabir02/chess-timer/actions/workflows/android-ci.yml/badge.svg)](https://github.com/imrankabir02/chess-timer/actions/workflows/android-ci.yml)
[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen.svg?style=flat-square)](https://github.com/imrankabir02/chess-timer/actions/runs/26327206712/artifacts/7175400862)

---

## 📥 Download

Get the latest build artifact directly from the CI pipeline:
* **[Download Latest Release APK](https://github.com/imrankabir02/chess-timer/actions/runs/26327206712/artifacts/7175400862)**

---

## ✨ Features

- **Dual Clock Timers**: Responsive tap areas for both players, styled with material design themes.
- **Custom Time Controls**: Create, edit, and save your favorite time controls (e.g., Blitz 3+2, Rapid 10+0, Bullet 1+0).
- **Preset Management**: Built-in SQLite storage (powered by Room Database) to persist presets across sessions.
- **Audio Feedback**: Subtle alerts and sound effects for turns, low time, and flags (game over).
- **Haptic Feedback**: Responsive vibration cues for taps and state transitions.

---

## 🛠️ Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/compose) (declarative UI toolkit)
- **Programming Language**: [Kotlin](https://kotlinlang.org/)
- **Database**: [Room SQLite Database](https://developer.android.com/training/data-storage/room)
- **Architecture Pattern**: MVVM (Model-View-ViewModel) with Kotlin Coroutines
- **Testing**: Robolectric, Roborazzi (Screenshot Testing), and JUnit

---

## 🚀 Run Locally

### Prerequisites
- [Android Studio Jellyfish+](https://developer.android.com/studio)
- JDK 17

### Steps
1. **Clone the Repository**:
   ```bash
   git clone https://github.com/imrankabir02/chess-timer.git
   cd chess-timer
   ```
2. **Open in Android Studio**:
   - Go to `File` > `Open...` and select the cloned directory.
   - Let Gradle sync and resolve project configurations.
3. **Configure Environment (Optional)**:
   - Create a `.env` file in the root directory (refer to `.env.example`).
4. **Build and Run**:
   - Select your target device/emulator and click the **Run** button (or press `Shift + F10`).

---

## 🧪 Running Tests

To run local unit and screenshot tests:
```bash
./gradlew testDebugUnitTest
```
