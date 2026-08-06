# Chess Clock ⏱️♟️🎲

A beautiful, modern, material-designed Android board-game app. **Play a complete game of chess**, **play Ludo against the computer**, or use the device as a pure tournament clock for a physical board. Built with Jetpack Compose, Kotlin, and Room Database.

[![Android CI/CD Pipeline](https://github.com/imrankabir02/chess-timer/actions/workflows/android-ci.yml/badge.svg)](https://github.com/imrankabir02/chess-timer/actions/workflows/android-ci.yml)
[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen.svg?style=flat-square)](https://github.com/imrankabir02/chess-timer/actions/runs/26327206712/artifacts/7175400862)

---

## 📥 Download

Get the latest build artifact directly from the CI pipeline:
* **[Download Latest Release APK](https://github.com/imrankabir02/chess-timer/actions/runs/26327206712/artifacts/7175400862)**

---

## ✨ Features

The home screen offers three modes.

### ♟️ Play chess

A complete, rules-correct game of chess for two players on one device.

- **Every rule implemented**: legal move generation with pins and checks, castling (including the "may not castle through check" restrictions), en passant, and under-promotion to any piece.
- **All the endings**: checkmate, stalemate, the fifty-move rule, threefold repetition, insufficient material, resignation, agreed draws, and flag fall — including the FIDE rule that a flag fall against a player who cannot possibly mate is only a draw.
- **Tap to move**: tap a piece to see its legal destinations, tap a destination to play. Last move, selection and check are highlighted on the board.
- **Notation and material**: a live move list in standard algebraic notation (with proper disambiguation), captured pieces, and the running material balance for each side.
- **Both clocks on the board**: every time control from the clock mode works here too, or play untimed.
- **Take back**: undo rewinds the board *and* both clocks, one half-move at a time.
- **Board orientation**: flip manually, or turn on auto-flip to rotate the board for whoever is on move when passing one phone back and forth.

### 🎲 Play ludo

Two to four seats on one device, any of them human or computer.

- **The full rule set**: a token needs a six to leave the yard; a six, a capture or getting a token home all earn another roll; three sixes in a row void the turn; the last step into home must be exact.
- **Captures and safe squares**: landing on an opponent knocks it back to its yard, except on the eight safe squares — the four coloured start squares and the four stars.
- **Optional blockades**: switch them on and two tokens of one colour bar everyone else from passing or landing on that square.
- **Computer opponents**: a heuristic bot that takes captures, races tokens home, gets out of the yard when the board is empty, and keeps clear of squares an opponent can reach next roll.
- **Places, not just a winner**: with three or four players the game runs on until only one player is left, and the final dialog shows the full podium.
- **Board and dice**: the 15x15 board is painted in one pass, tokens glide between squares and pulse when they are playable, and the die spins as it rolls. When there is only one legal move it is played for you.

### ⏱️ Chess clock

The original mode, for a real board on the table between you.

- **Dual Clock Timers**: Responsive tap areas for both players, styled with material design themes.
- **Custom Time Controls**: Create, edit, and save your favorite time controls (e.g., Blitz 3+2, Rapid 10+0, Bullet 1+0).
- **Preset Management**: Built-in SQLite storage (powered by Room Database) to persist presets across sessions.
- **Audio Feedback**: Subtle alerts and sound effects for turns, low time, and flags (game over).
- **Haptic Feedback**: Responsive vibration cues for taps and state transitions.

The two chess modes share the same time-control presets, the sudden-death / Fischer increment / USCF delay timing styles, and the sound and vibration cues. All three modes share the theme and the audio-haptic feedback.

---

## 🛠️ Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/compose) (declarative UI toolkit)
- **Programming Language**: [Kotlin](https://kotlinlang.org/)
- **Game rules**: dependency-free engines in `com.example.chess` and `com.example.ludo` — no third-party game libraries
- **Database**: [Room SQLite Database](https://developer.android.com/training/data-storage/room)
- **Architecture Pattern**: MVVM (Model-View-ViewModel) with Kotlin Coroutines
- **Testing**: Robolectric, Roborazzi (Screenshot Testing), and JUnit

### The chess engine

`app/src/main/java/com/example/chess/` is plain Kotlin with no Android dependencies, so it can be
unit tested directly on the JVM:

| File | Responsibility |
| --- | --- |
| `Pieces.kt` | Colours, piece types and the `0..63` square addressing (a1 = 0, h8 = 63) |
| `Move.kt` | A fully described move, plus castling-right flags |
| `Position.kt` | Immutable board, attack detection, legal move generation, FEN in and out |
| `San.kt` | Standard algebraic notation, written and parsed |
| `ChessGame.kt` | Move history, undo, and every way a game can end |

Positions are immutable — playing a move returns a new one — which is what makes undo, threefold
repetition detection and board review fall out for free.

### The Ludo engine

`app/src/main/java/com/example/ludo/` follows the same pattern — pure Kotlin, immutable state,
JVM testable:

| File | Responsibility |
| --- | --- |
| `LudoBoard.kt` | The 15x15 geometry: the 52-square ring, the four home columns, yards and centre |
| `LudoGame.kt` | Tokens, moves, captures, blockades, turn order and the end of the game |
| `LudoBot.kt` | The heuristic used by computer players |

A token's whole position is a single number — its **progress**, counted from its own start square:

```
 0          in the yard
 1 .. 51    on the shared ring
52 .. 56    in its private home column
57          home
```

Counting from each colour's own start square is what makes the four routes identical: the same
progress means the same distance travelled, whichever seat you are in. The shared square a token
actually stands on is `(startIndex + progress - 1) % 52`, which is the only place the four routes
have to be reconciled — so captures, safe squares and blockades all reduce to comparing one integer.

The dice is passed into the engine rather than rolled inside it, which keeps the rules
deterministic and lets the tests play out whole games from a seed.

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

Chess move generation is verified with **perft** — the number of leaf nodes reachable at a given
depth — against the published counts for the standard test positions (the starting position,
Kiwipete and five others). If any rule is subtly wrong, `PerftTest` fails.

Ludo is verified the same way round: as well as the rule-by-rule tests, `LudoRulesTest` plays
thirty complete games from fixed seeds and checks every one reaches a winner with the board still
in a valid state — which is what catches a rule that would deadlock the game rather than merely
misbehave.
