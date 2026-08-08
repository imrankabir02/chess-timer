package com.example.model

import com.example.chess.ChessDifficulty
import com.example.chess.ChessGame
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.Square
import kotlin.random.Random

/** A pawn reached the last rank and is waiting for the player to pick a piece. */
data class PendingPromotion(val from: Int, val to: Int, val color: PieceColor)

/** Who is sitting on the other side of the board. */
enum class ChessOpponent(val label: String) {
    HUMAN("Two players"),
    COMPUTER("Vs computer")
}

/** The side a human asks to play. [RANDOM] is settled when the game starts. */
enum class ChessSide(val label: String) {
    WHITE("White"),
    BLACK("Black"),
    RANDOM("Random");

    fun resolve(random: Random = Random.Default): PieceColor = when (this) {
        WHITE -> PieceColor.WHITE
        BLACK -> PieceColor.BLACK
        RANDOM -> if (random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
    }
}

/**
 * How a game is set up before it starts: who the opponent is, how hard they play, and which colour
 * the human takes. [humanColor] is the colour actually dealt out, so a RANDOM [side] settles once
 * rather than flipping about mid-game.
 */
data class ChessSetup(
    val opponent: ChessOpponent = ChessOpponent.HUMAN,
    val difficulty: ChessDifficulty = ChessDifficulty.DEFAULT,
    val side: ChessSide = ChessSide.WHITE,
    val humanColor: PieceColor = PieceColor.WHITE
) {
    val vsComputer: Boolean get() = opponent == ChessOpponent.COMPUTER

    /** The colour the computer plays, or null in a two-player game. */
    val computerColor: PieceColor? get() = if (vsComputer) humanColor.opposite else null

    fun isComputer(color: PieceColor): Boolean = vsComputer && color != humanColor

    /** Settles a RANDOM side into a concrete colour. Called once, when a game starts. */
    fun dealt(random: Random = Random.Default): ChessSetup = copy(humanColor = side.resolve(random))

    fun describe(): String = when {
        !vsComputer -> "Two players on one device"
        else -> "Computer · ${difficulty.label} · you play ${humanColor.name.lowercase()}"
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
    val setup: ChessSetup = ChessSetup(),
    val timeControl: GameTimeControl = GameTimeControl.DEFAULT,
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
    val resultDismissed: Boolean = false,
    /** True while the computer is working out its reply. */
    val computerThinking: Boolean = false,
    /** A one-off line for the player — a declined draw offer, say. Cleared by the next move. */
    val notice: String? = null,
    /** False until the player has been shown the setup once, on opening the screen. */
    val setupSeen: Boolean = false
) {
    val position get() = game.position

    val sideToMove: PieceColor get() = game.sideToMove

    val isOver: Boolean get() = game.isOver

    val vsComputer: Boolean get() = setup.vsComputer

    /** True when the side to move is the computer, so the board must not accept taps. */
    val isComputerTurn: Boolean get() = !isOver && setup.isComputer(sideToMove)

    fun isComputer(color: PieceColor): Boolean = setup.isComputer(color)

    /** The clock that is counting down right now, or null when nothing is running. */
    val activeColor: PieceColor?
        get() = if (clockRunning && !isOver) sideToMove else null

    /** A started game whose clock has been halted: the board is frozen until it resumes. */
    val isPaused: Boolean
        get() = clockStarted && !clockRunning && !isOver && !timeControl.isUnlimited

    /** Whether the player may touch the board at all right now. */
    val boardEnabled: Boolean
        get() = !isOver && !isPaused && !isComputerTurn

    /**
     * How many half-moves a take-back removes, leaving the player to move again. Against the
     * computer that usually means rewinding its reply as well as your own move — taking back only
     * its reply would simply hand it the move straight back.
     *
     * A declared result (a resignation, a flag fall) is taken back on its own first, so this counts
     * only what happens once the board itself starts rewinding.
     */
    val undoPlies: Int
        get() = when {
            game.moveCount == 0 -> 0
            !vsComputer -> 1
            // While the computer is on move, your own last move is the only thing to take back.
            setup.isComputer(sideToMove) -> 1
            game.moveCount >= 2 -> 2
            // The computer opened the game and you have not replied yet: nothing of yours to undo.
            else -> 0
        }

    val canUndo: Boolean get() = undoPlies > 0 || isOver

    fun timeFor(color: PieceColor): Long =
        if (color == PieceColor.WHITE) whiteTimeMs else blackTimeMs

    /** Material the given colour has won, as a positive point count. */
    fun materialLead(color: PieceColor): Int {
        val balance = game.materialBalance()
        return if (color == PieceColor.WHITE) maxOf(balance, 0) else maxOf(-balance, 0)
    }

    fun capturedBy(color: PieceColor): List<PieceType> = game.capturedFrom(color.opposite)
}
