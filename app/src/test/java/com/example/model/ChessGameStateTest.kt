package com.example.model

import com.example.chess.ChessGame
import com.example.chess.PieceColor
import com.example.chess.PieceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
