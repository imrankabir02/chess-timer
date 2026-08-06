package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.chess.GameEndReason
import com.example.chess.GameOutcome
import com.example.chess.Piece
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.Square
import com.example.model.GameTimeControl
import com.example.model.TimingStyle
import com.example.viewmodel.ChessGameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChessGameViewModelTest {

    private lateinit var viewModel: ChessGameViewModel

    private val untimed = GameTimeControl.UNLIMITED

    @Before
    fun setUp() {
        viewModel = ChessGameViewModel(ApplicationProvider.getApplicationContext<Application>())
    }

    /** Taps a move the way a player would: piece first, then destination. */
    private fun tapMove(from: String, to: String) {
        viewModel.onSquareTapped(Square.fromName(from))
        viewModel.onSquareTapped(Square.fromName(to))
    }

    @Test
    fun tappingAPieceShowsItsLegalDestinations() {
        viewModel.newGame(untimed)
        viewModel.onSquareTapped(Square.fromName("e2"))

        val state = viewModel.state.value
        assertEquals(Square.fromName("e2"), state.selectedSquare)
        assertEquals(
            setOf(Square.fromName("e3"), Square.fromName("e4")),
            state.legalTargets
        )

        // Tapping it again clears the selection.
        viewModel.onSquareTapped(Square.fromName("e2"))
        assertEquals(Square.NONE, viewModel.state.value.selectedSquare)
    }

    @Test
    fun theOpponentsPiecesCannotBeSelected() {
        viewModel.newGame(untimed)
        viewModel.onSquareTapped(Square.fromName("e7"))
        assertEquals(Square.NONE, viewModel.state.value.selectedSquare)
        assertTrue(viewModel.state.value.legalTargets.isEmpty())
    }

    @Test
    fun aMoveUpdatesTheBoardAndHandsOverTheTurn() {
        viewModel.newGame(untimed)
        tapMove("e2", "e4")

        val state = viewModel.state.value
        assertEquals(PieceColor.BLACK, state.sideToMove)
        assertEquals(Piece(PieceColor.WHITE, PieceType.PAWN), state.position.pieceAt(Square.fromName("e4")))
        assertNull(state.position.pieceAt(Square.fromName("e2")))
        assertEquals(listOf("e4"), state.game.history.map { it.san })
        assertEquals(Square.NONE, state.selectedSquare)
    }

    @Test
    fun illegalDestinationsAreIgnored() {
        viewModel.newGame(untimed)
        tapMove("e2", "e5")
        assertEquals(0, viewModel.state.value.game.moveCount)
    }

    @Test
    fun checkmateEndsTheGame() {
        viewModel.newGame(untimed)
        tapMove("f2", "f3")
        tapMove("e7", "e5")
        tapMove("g2", "g4")
        tapMove("d8", "h4")

        val state = viewModel.state.value
        assertTrue(state.isOver)
        assertEquals(GameEndReason.CHECKMATE, state.game.result?.reason)
        assertEquals(GameOutcome.BLACK_WINS, state.game.result?.outcome)

        // The board is frozen once the game is decided.
        viewModel.onSquareTapped(Square.fromName("e1"))
        assertEquals(Square.NONE, viewModel.state.value.selectedSquare)
    }

    @Test
    fun promotingAPawnAsksForAPieceAndThenPlacesIt() {
        viewModel.newGame(untimed)
        tapMove("h2", "h4")
        tapMove("g7", "g5")
        tapMove("h4", "g5")
        tapMove("h7", "h6")
        tapMove("g5", "h6")
        tapMove("a7", "a6")
        tapMove("h6", "h7")
        tapMove("a6", "a5")

        // h7 takes the knight on g8 and must promote.
        tapMove("h7", "g8")
        val pending = viewModel.state.value.pendingPromotion
        assertNotNull("reaching the last rank must ask for a piece", pending)
        assertEquals(PieceColor.WHITE, pending!!.color)
        assertEquals(Square.fromName("g8"), pending.to)
        assertEquals(8, viewModel.state.value.game.moveCount)

        viewModel.choosePromotion(PieceType.ROOK)
        val state = viewModel.state.value
        assertNull(state.pendingPromotion)
        assertEquals(9, state.game.moveCount)
        assertEquals(Piece(PieceColor.WHITE, PieceType.ROOK), state.position.pieceAt(Square.fromName("g8")))
        // The bishop still on f8 blocks the rank, so the new rook does not give check.
        assertEquals("hxg8=R", state.game.history.last().san)
    }

    @Test
    fun cancellingAPromotionLeavesThePawnWhereItWas() {
        viewModel.newGame(untimed)
        tapMove("h2", "h4")
        tapMove("g7", "g5")
        tapMove("h4", "g5")
        tapMove("h7", "h6")
        tapMove("g5", "h6")
        tapMove("a7", "a6")
        tapMove("h6", "h7")
        tapMove("a6", "a5")
        tapMove("h7", "g8")

        viewModel.cancelPromotion()
        val state = viewModel.state.value
        assertNull(state.pendingPromotion)
        assertEquals(8, state.game.moveCount)
        assertEquals(Piece(PieceColor.WHITE, PieceType.PAWN), state.position.pieceAt(Square.fromName("h7")))
    }

    @Test
    fun fischerIncrementIsAddedToTheClockOfWhoeverMoved() {
        val control = GameTimeControl(
            label = "Test 1+3",
            initialTimeMs = 60_000L,
            timingStyle = TimingStyle.INCREMENT,
            incrementSeconds = 3
        )
        viewModel.newGame(control)
        assertEquals(60_000L, viewModel.state.value.whiteTimeMs)
        assertFalse("the clock waits for the first move", viewModel.state.value.clockStarted)

        tapMove("e2", "e4")
        viewModel.pauseClock()

        val state = viewModel.state.value
        // White is no longer on the clock, so their time is exactly the start plus the increment.
        assertEquals(63_000L, state.whiteTimeMs)
        assertTrue(state.clockStarted)
        assertTrue(state.isPaused)
    }

    @Test
    fun theBoardIsFrozenWhileTheClockIsPaused() {
        viewModel.newGame(GameTimeControl.DEFAULT)
        tapMove("e2", "e4")
        viewModel.pauseClock()

        viewModel.onSquareTapped(Square.fromName("e7"))
        assertEquals(Square.NONE, viewModel.state.value.selectedSquare)
        assertEquals(1, viewModel.state.value.game.moveCount)
    }

    @Test
    fun undoRewindsTheBoardAndTheClocks() {
        val control = GameTimeControl(
            label = "Test 1+3",
            initialTimeMs = 60_000L,
            timingStyle = TimingStyle.INCREMENT,
            incrementSeconds = 3
        )
        viewModel.newGame(control)
        tapMove("e2", "e4")
        viewModel.undo()

        val state = viewModel.state.value
        assertEquals(0, state.game.moveCount)
        assertEquals(PieceColor.WHITE, state.sideToMove)
        assertEquals(60_000L, state.whiteTimeMs)
        assertEquals(60_000L, state.blackTimeMs)
        assertFalse(state.clockRunning)
        assertEquals(
            Piece(PieceColor.WHITE, PieceType.PAWN),
            state.position.pieceAt(Square.fromName("e2"))
        )
    }

    @Test
    fun resigningAndAgreeingADrawEndTheGame() {
        viewModel.newGame(untimed)
        tapMove("e2", "e4")
        viewModel.resign(PieceColor.BLACK)

        assertEquals(GameOutcome.WHITE_WINS, viewModel.state.value.game.result?.outcome)
        assertEquals(GameEndReason.RESIGNATION, viewModel.state.value.game.result?.reason)

        viewModel.newGame(untimed)
        viewModel.agreeDraw()
        assertEquals(GameOutcome.DRAW, viewModel.state.value.game.result?.outcome)
    }

    @Test
    fun autoFlipTurnsTheBoardForWhoeverIsOnMove() {
        viewModel.newGame(untimed)
        assertFalse(viewModel.state.value.boardFlipped)

        viewModel.toggleAutoFlip()
        assertTrue(viewModel.state.value.autoFlip)
        assertFalse("white starts, so white stays at the bottom", viewModel.state.value.boardFlipped)

        tapMove("e2", "e4")
        assertTrue("black to move, board turns around", viewModel.state.value.boardFlipped)

        tapMove("e7", "e5")
        assertFalse(viewModel.state.value.boardFlipped)
    }

    @Test
    fun manualFlipIsIndependentOfTheGame() {
        viewModel.newGame(untimed)
        viewModel.flipBoard()
        assertTrue(viewModel.state.value.boardFlipped)
        viewModel.flipBoard()
        assertFalse(viewModel.state.value.boardFlipped)
    }

    @Test
    fun startingANewGameClearsEverything() {
        viewModel.newGame(untimed)
        tapMove("e2", "e4")
        tapMove("e7", "e5")
        viewModel.newGame(GameTimeControl.DEFAULT)

        val state = viewModel.state.value
        assertEquals(0, state.game.moveCount)
        assertEquals(PieceColor.WHITE, state.sideToMove)
        assertEquals(GameTimeControl.DEFAULT.initialTimeMs, state.whiteTimeMs)
        assertEquals(GameTimeControl.DEFAULT.initialTimeMs, state.blackTimeMs)
        assertNull(state.game.result)
        assertFalse(state.clockRunning)
    }
}
