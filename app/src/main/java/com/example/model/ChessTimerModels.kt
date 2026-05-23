package com.example.model

enum class Player {
    PLAYER_A, // Top (Rotated 180 degrees)
    PLAYER_B  // Bottom (Normal)
}

enum class GameStatus {
    SETUP,
    READY,
    RUNNING,
    PAUSED,
    FLAG_FALL
}

enum class TimingStyle {
    NONE,       // Sudden Death / standard
    INCREMENT,  // Fischer Increment
    DELAY       // Simple Delay
}

data class ChessTimerState(
    val playerATimeMs: Long = 300_000,
    val playerBTimeMs: Long = 300_000,
    val activePlayer: Player? = null,
    val gameStatus: GameStatus = GameStatus.SETUP,
    val playerAMoveCount: Int = 0,
    val playerBMoveCount: Int = 0,
    val isWhiteActive: Boolean = true, // To track move numbers or switch sides representation
    val delayBufferMs: Long = 0, // Delay buffer countdown
    val timingStyle: TimingStyle = TimingStyle.NONE,
    val initialTimeMs: Long = 300_000,
    val incrementSeconds: Int = 0,
    val delaySeconds: Int = 0,
    val winner: Player? = null,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)
