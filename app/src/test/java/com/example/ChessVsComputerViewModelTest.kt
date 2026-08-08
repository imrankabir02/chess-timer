package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.chess.ChessDifficulty
import com.example.chess.PieceColor
import com.example.chess.Square
import com.example.model.ChessOpponent
import com.example.model.ChessSetup
import com.example.model.ChessSide
import com.example.model.GameTimeControl
import com.example.viewmodel.ChessGameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random

/**
 * Playing against the computer. The opponent is seeded and its search is run inline on the test
 * scheduler, so every game here plays out the same way every time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChessVsComputerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ChessGameViewModel

    private val untimed = GameTimeControl.UNLIMITED

    private fun vsComputer(
        difficulty: ChessDifficulty = ChessDifficulty.EASY,
        side: ChessSide = ChessSide.WHITE
    ) = ChessSetup(
        opponent = ChessOpponent.COMPUTER,
        difficulty = difficulty,
        side = side
    )

    private val twoPlayers = ChessSetup(opponent = ChessOpponent.HUMAN)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ChessGameViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModel.useBotRandom(Random(7))
        // Run the search on the test scheduler instead of a background thread, so a test can wait
        // for the computer simply by letting the scheduler run dry.
        viewModel.botSearchContext = EmptyCoroutineContext
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun tapMove(from: String, to: String) {
        viewModel.onSquareTapped(Square.fromName(from))
        viewModel.onSquareTapped(Square.fromName(to))
    }

    @Test
    fun theComputerRepliesToYourMove() = runTest(dispatcher) {
        viewModel.newGame(untimed, vsComputer())
        advanceUntilIdle()

        // White is the human, so nothing happens until a move is played.
        assertEquals(0, viewModel.state.value.game.moveCount)
        assertFalse(viewModel.state.value.isComputerTurn)

        tapMove("e2", "e4")
        assertTrue("the computer should be on move now", viewModel.state.value.isComputerTurn)

        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals("the computer should have answered", 2, state.game.moveCount)
        assertEquals(PieceColor.WHITE, state.sideToMove)
        assertFalse(state.computerThinking)
        assertTrue(state.boardEnabled)
    }

    @Test
    fun theBoardIsLockedWhileTheComputerIsOnMove() = runTest(dispatcher) {
        viewModel.newGame(untimed, vsComputer())
        advanceUntilIdle()
        tapMove("e2", "e4")

        // The computer has not replied yet: the board must not accept anything.
        assertFalse(viewModel.state.value.boardEnabled)
        viewModel.onSquareTapped(Square.fromName("e7"))
        assertEquals(Square.NONE, viewModel.state.value.selectedSquare)
        assertEquals(1, viewModel.state.value.game.moveCount)

        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.game.moveCount)
    }

    @Test
    fun playingBlackMeansTheComputerOpens() = runTest(dispatcher) {
        viewModel.newGame(untimed, vsComputer(side = ChessSide.BLACK))
        assertEquals(PieceColor.BLACK, viewModel.state.value.setup.humanColor)
        assertTrue("black plays from the near side", viewModel.state.value.boardFlipped)

        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals("the computer opens the game", 1, state.game.moveCount)
        assertEquals(PieceColor.BLACK, state.sideToMove)
        assertTrue(state.boardEnabled)
    }

    @Test
    fun aTakeBackRewindsTheComputersReplyAsWellAsYourMove() = runTest(dispatcher) {
        viewModel.newGame(untimed, vsComputer())
        advanceUntilIdle()
        tapMove("e2", "e4")
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.game.moveCount)

        viewModel.undo()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("both half-moves come back", 0, state.game.moveCount)
        assertEquals(PieceColor.WHITE, state.sideToMove)
        assertFalse("the computer must not immediately move again", state.isComputerTurn)
        assertTrue(state.boardEnabled)
    }

    @Test
    fun aTakeBackAsBlackLeavesTheComputersOpeningMoveAlone() = runTest(dispatcher) {
        viewModel.newGame(untimed, vsComputer(side = ChessSide.BLACK))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.game.moveCount)

        // There is nothing of yours to take back yet, so undo is not on offer.
        assertFalse(viewModel.state.value.canUndo)
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.game.moveCount)
    }

    @Test
    fun twoPlayersOnOneDeviceGetNoComputerReply() = runTest(dispatcher) {
        viewModel.newGame(untimed, twoPlayers)
        advanceUntilIdle()

        tapMove("e2", "e4")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("nobody should answer for black", 1, state.game.moveCount)
        assertEquals(PieceColor.BLACK, state.sideToMove)
        assertFalse(state.vsComputer)
        assertTrue(state.boardEnabled)
    }

    @Test
    fun theChosenDifficultyIsTheOneThatPlays() = runTest(dispatcher) {
        viewModel.newGame(untimed, vsComputer(difficulty = ChessDifficulty.HARD))
        advanceUntilIdle()
        assertEquals(ChessDifficulty.HARD, viewModel.state.value.setup.difficulty)

        viewModel.newGame(untimed, vsComputer(difficulty = ChessDifficulty.EASY))
        advanceUntilIdle()
        assertEquals(ChessDifficulty.EASY, viewModel.state.value.setup.difficulty)
    }

    @Test
    fun aRandomSideIsSettledOnceAndThenStaysPut() = runTest(dispatcher) {
        viewModel.newGame(untimed, vsComputer(side = ChessSide.RANDOM))
        advanceUntilIdle()

        val dealt = viewModel.state.value.setup.humanColor
        assertEquals(ChessSide.RANDOM, viewModel.state.value.setup.side)

        // Whichever colour came up, it is still that colour a few moves in.
        repeat(2) {
            val human = viewModel.state.value.setup.humanColor
            val move = viewModel.state.value.position.legalMoves().first { it.piece.color == human }
            viewModel.onSquareTapped(move.from)
            viewModel.onSquareTapped(move.to)
            advanceUntilIdle()
        }
        assertEquals(dealt, viewModel.state.value.setup.humanColor)
    }

    @Test
    fun autoFlipIsTurnedOffWhenTheComputerSitsDown() = runTest(dispatcher) {
        viewModel.newGame(untimed, twoPlayers)
        viewModel.toggleAutoFlip()
        assertTrue(viewModel.state.value.autoFlip)

        viewModel.newGame(untimed, vsComputer())
        advanceUntilIdle()
        assertFalse("there is nobody to pass the phone to", viewModel.state.value.autoFlip)
    }

    @Test
    fun theComputerWeighsUpADrawOfferRatherThanJustTakingIt() = runTest(dispatcher) {
        viewModel.newGame(untimed, vsComputer(difficulty = ChessDifficulty.HARD))
        advanceUntilIdle()

        viewModel.offerDraw()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse("nothing is decided from an even position", state.isOver)
        assertNotEquals("the player should be told it was turned down", null, state.notice)
        assertFalse(state.computerThinking)
        assertTrue("and the game carries on", state.boardEnabled)

        // The notice clears itself once play resumes.
        tapMove("e2", "e4")
        assertEquals(null, viewModel.state.value.notice)
    }

    @Test
    fun betweenTwoPlayersADrawIsSimplyAgreed() = runTest(dispatcher) {
        viewModel.newGame(untimed, twoPlayers)
        advanceUntilIdle()

        viewModel.offerDraw()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isOver)
        assertEquals(null, viewModel.state.value.game.result?.winner)
    }

    @Test
    fun startingAGameMarksTheSetupAsSeen() = runTest(dispatcher) {
        assertFalse("the setup is offered when the screen opens", viewModel.state.value.setupSeen)

        viewModel.newGame(untimed, vsComputer())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.setupSeen)
    }
}
