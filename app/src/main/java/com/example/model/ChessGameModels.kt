package com.example.model

import com.example.chess.ChessBotLevel
import com.example.chess.ChessGame
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.Square

/** A pawn reached the last rank and is waiting for the player to pick a piece. */
data class PendingPromotion(val from: Int, val to: Int, val color: PieceColor)

/**
 * Who is on the other side of the board: a second player sharing the device, or the computer at
 * one of its three levels.
 */
data class ChessOpponent(
    val isComputer: Boolean = false,
    val level: ChessBotLevel = ChessBotLevel.MEDIUM,
    /** The side the computer plays. Meaningless — and ignored — in a two-player game. */
    val computerColor: PieceColor = PieceColor.BLACK
) {
    /** The colour the computer plays, or null when two people are playing. */
    val botColor: PieceColor?
        get() = if (isComputer) computerColor else null

    fun isBot(color: PieceColor): Boolean = botColor == color

    companion object {
        val TWO_PLAYERS = ChessOpponent()

        fun computer(level: ChessBotLevel, computerColor: PieceColor = PieceColor.BLACK) =
            ChessOpponent(isComputer = true, level = level, computerColor = computerColor)
    }
}

/** The time control a game is played with. */
data class GameTimeControl(
    val label: String,
    val initialTimeMs: Long,
    val timingStyle: TimingStyle = TimingStyle.NONE,
    val incrementSeconds: Int = 0,
    val delaySeconds: Int = 0,
    val isUnlimited: Boolean = false
) {
    companion object {
        val UNLIMITED = GameTimeControl(
            label = "No clock",
            initialTimeMs = 0L,
            isUnlimited = true
        )

        val DEFAULT = GameTimeControl(
            label = "Blitz 5+3",
            initialTimeMs = 300_000L,
            timingStyle = TimingStyle.INCREMENT,
            incrementSeconds = 3
        )
    }
}

/** Everything the board screen renders. */
data class ChessGameUiState(
    val game: ChessGame = ChessGame.new(),
    val timeControl: GameTimeControl = GameTimeControl.DEFAULT,
    val opponent: ChessOpponent = ChessOpponent.TWO_PLAYERS,
    /** True while the computer is working out its move. */
    val isThinking: Boolean = false,
    val whiteTimeMs: Long = GameTimeControl.DEFAULT.initialTimeMs,
    val blackTimeMs: Long = GameTimeControl.DEFAULT.initialTimeMs,
    val delayBufferMs: Long = 0L,
    val clockRunning: Boolean = false,
    /** False until the first move is played; the clock idles while the players get set. */
    val clockStarted: Boolean = false,
    val selectedSquare: Int = Square.NONE,
    val legalTargets: Set<Int> = emptySet(),
    val pendingPromotion: PendingPromotion? = null,
    val boardFlipped: Boolean = false,
    val autoFlip: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val resultDismissed: Boolean = false
) {
    val position get() = game.position

    val sideToMove: PieceColor get() = game.sideToMove

    val isOver: Boolean get() = game.isOver

    /** The clock that is counting down right now, or null when nothing is running. */
    val activeColor: PieceColor?
        get() = if (clockRunning && !isOver) sideToMove else null

    /** A started game whose clock has been halted: the board is frozen until it resumes. */
    val isPaused: Boolean
        get() = clockStarted && !clockRunning && !isOver && !timeControl.isUnlimited

    /** The computer is on move, so the board belongs to it until it has played. */
    val isComputerToMove: Boolean
        get() = !isOver && opponent.isBot(sideToMove)

    fun isComputer(color: PieceColor): Boolean = opponent.isBot(color)

    fun timeFor(color: PieceColor): Long =
        if (color == PieceColor.WHITE) whiteTimeMs else blackTimeMs

    /** Material the given colour has won, as a positive point count. */
    fun materialLead(color: PieceColor): Int {
        val balance = game.materialBalance()
        return if (color == PieceColor.WHITE) maxOf(balance, 0) else maxOf(-balance, 0)
    }

    fun capturedBy(color: PieceColor): List<PieceType> = game.capturedFrom(color.opposite)
}
