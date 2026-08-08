package com.example.model

import com.example.chess.ChessDifficulty
import com.example.chess.ChessGame
import com.example.chess.PieceColor
import com.example.chess.PieceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChessGameStateTest {

    private fun gameAfter(vararg moves: String): ChessGame {
        var game = ChessGame.new()
        moves.forEach { game = game.playSan(it) ?: error("$it should be legal") }
        return game
    }

    @Test
    fun theClockOnlyRunsForTheSideToMove() {
        val idle = ChessGameUiState()
        assertNull("nothing ticks before the first move", idle.activeColor)

        val running = idle.copy(clockRunning = true, clockStarted = true)
        assertEquals(PieceColor.WHITE, running.activeColor)
        assertEquals(PieceColor.BLACK, running.copy(game = gameAfter("e4")).activeColor)
    }

    @Test
    fun aStartedGameWithAStoppedClockCountsAsPaused() {
        val state = ChessGameUiState(clockStarted = true, clockRunning = false)
        assertTrue(state.isPaused)
        assertFalse(state.copy(clockRunning = true).isPaused)
        assertFalse("an untimed game is never paused", state.copy(timeControl = GameTimeControl.UNLIMITED).isPaused)
        assertFalse(
            "a finished game is over, not paused",
            state.copy(game = gameAfter("f3", "e5", "g4", "Qh4")).isPaused
        )
    }

    @Test
    fun capturedPiecesAreAttributedToTheCapturingSide() {
        val state = ChessGameUiState(game = gameAfter("e4", "d5", "exd5", "Qxd5", "Nc3", "Qxa2"))
        assertEquals(listOf(PieceType.PAWN), state.capturedBy(PieceColor.WHITE))
        assertEquals(listOf(PieceType.PAWN, PieceType.PAWN), state.capturedBy(PieceColor.BLACK))
        assertEquals(0, state.materialLead(PieceColor.WHITE))
        assertEquals(1, state.materialLead(PieceColor.BLACK))
    }

    @Test
    fun timeForReadsTheRightClock() {
        val state = ChessGameUiState(whiteTimeMs = 61_000, blackTimeMs = 42_000)
        assertEquals(61_000L, state.timeFor(PieceColor.WHITE))
        assertEquals(42_000L, state.timeFor(PieceColor.BLACK))
    }

    @Test
    fun theComputerOwnsTheBoardOnItsOwnTurn() {
        val vsComputer = ChessSetup(
            opponent = ChessOpponent.COMPUTER,
            difficulty = ChessDifficulty.MEDIUM,
            humanColor = PieceColor.WHITE
        )
        val yourMove = ChessGameUiState(setup = vsComputer, timeControl = GameTimeControl.UNLIMITED)
        assertFalse(yourMove.isComputerTurn)
        assertTrue(yourMove.boardEnabled)
        assertTrue(yourMove.isComputer(PieceColor.BLACK))
        assertFalse(yourMove.isComputer(PieceColor.WHITE))

        val itsMove = yourMove.copy(game = gameAfter("e4"))
        assertTrue(itsMove.isComputerTurn)
        assertFalse("the board is locked while it thinks", itsMove.boardEnabled)

        val twoPlayers = yourMove.copy(setup = ChessSetup(opponent = ChessOpponent.HUMAN))
        assertFalse(twoPlayers.copy(game = gameAfter("e4")).isComputerTurn)
    }

    @Test
    fun aTakeBackAgainstTheComputerCoversBothHalfMoves() {
        val asWhite = ChessGameUiState(
            setup = ChessSetup(opponent = ChessOpponent.COMPUTER, humanColor = PieceColor.WHITE),
            timeControl = GameTimeControl.UNLIMITED
        )
        assertEquals("nothing has been played yet", 0, asWhite.undoPlies)
        assertFalse(asWhite.canUndo)

        // Your move is in but the computer has not answered: only your move comes back.
        assertEquals(1, asWhite.copy(game = gameAfter("e4")).undoPlies)
        // Both are in: the pair comes back, so it is your move again.
        assertEquals(2, asWhite.copy(game = gameAfter("e4", "e5")).undoPlies)

        val asBlack = ChessGameUiState(
            setup = ChessSetup(opponent = ChessOpponent.COMPUTER, humanColor = PieceColor.BLACK),
            timeControl = GameTimeControl.UNLIMITED
        )
        val computerOpened = asBlack.copy(game = gameAfter("e4"))
        assertEquals("there is nothing of yours to take back", 0, computerOpened.undoPlies)
        assertFalse(computerOpened.canUndo)
        assertEquals(2, asBlack.copy(game = gameAfter("e4", "e5", "Nf3")).undoPlies)

        // Between two players it is always a single half-move.
        val twoPlayers = ChessGameUiState(timeControl = GameTimeControl.UNLIMITED)
        assertEquals(1, twoPlayers.copy(game = gameAfter("e4")).undoPlies)
        assertEquals(1, twoPlayers.copy(game = gameAfter("e4", "e5")).undoPlies)
    }

    @Test
    fun aSideChoiceOfRandomStillLandsOnAColour() {
        assertEquals(PieceColor.WHITE, ChessSide.WHITE.resolve())
        assertEquals(PieceColor.BLACK, ChessSide.BLACK.resolve())

        val dealt = (1..20).map { seed -> ChessSide.RANDOM.resolve(Random(seed)) }.toSet()
        assertEquals("random should reach both colours", 2, dealt.size)

        val setup = ChessSetup(opponent = ChessOpponent.COMPUTER, side = ChessSide.RANDOM)
        val started = setup.dealt(Random(1))
        assertEquals("the choice is kept, so a rematch re-rolls", ChessSide.RANDOM, started.side)
        assertEquals(started.humanColor.opposite, started.computerColor)
    }

    @Test
    fun aTwoPlayerSetupHasNoComputerSeat() {
        val setup = ChessSetup(opponent = ChessOpponent.HUMAN)
        assertFalse(setup.vsComputer)
        assertNull(setup.computerColor)
        assertFalse(setup.isComputer(PieceColor.WHITE))
        assertFalse(setup.isComputer(PieceColor.BLACK))
    }

    @Test
    fun timeControlsCarryTheirTimingStyle() {
        assertTrue(GameTimeControl.UNLIMITED.isUnlimited)
        assertEquals(0L, GameTimeControl.UNLIMITED.initialTimeMs)

        val blitz = GameTimeControl.DEFAULT
        assertFalse(blitz.isUnlimited)
        assertEquals(TimingStyle.INCREMENT, blitz.timingStyle)
        assertEquals(300_000L, blitz.initialTimeMs)
        assertEquals(3, blitz.incrementSeconds)
    }
}
