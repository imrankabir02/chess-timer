package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ludo.LudoBoard
import com.example.ludo.LudoColor
import com.example.model.LudoSetup
import com.example.viewmodel.LudoViewModel
import kotlin.random.Random
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LudoViewModelTest {

    /** A die that always shows the same number, so a turn can be stepped through predictably. */
    private class LoadedDie(private val value: Int) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(from: Int, until: Int): Int = value
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LudoViewModel

    /** Two humans: nothing moves unless the test asks it to. */
    private val twoHumans = LudoSetup(playerCount = 2, botColors = emptySet())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = LudoViewModel(ApplicationProvider.getApplicationContext<Application>())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aFreshGameWaitsForTheFirstRoll() = runTest(dispatcher) {
        viewModel.useDice(LoadedDie(6))
        viewModel.newGame(twoHumans)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(LudoColor.RED, state.currentColor)
        assertTrue(state.canRoll)
        assertNull(state.game.dice)
        assertTrue(state.movableTokens.isEmpty())
        assertEquals(2, state.game.players.size)
        assertTrue(state.game.allTokens.all { it.isInYard })
    }

    @Test
    fun rollingASixOffersEveryTokenInTheYard() = runTest(dispatcher) {
        viewModel.useDice(LoadedDie(6))
        viewModel.newGame(twoHumans)
        advanceUntilIdle()

        viewModel.rollDice()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(6, state.diceFace)
        assertEquals(6, state.game.dice)
        assertFalse(state.isRolling)
        assertTrue(state.awaitingTokenChoice)
        assertEquals(setOf(0, 1, 2, 3), state.movableTokens)
    }

    @Test
    fun tappingATokenBringsItOntoTheBoard() = runTest(dispatcher) {
        viewModel.useDice(LoadedDie(6))
        viewModel.newGame(twoHumans)
        advanceUntilIdle()
        viewModel.rollDice()
        advanceUntilIdle()

        viewModel.onTokenTapped(LudoColor.RED, 2)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.game.player(LudoColor.RED).tokens[2].progress)
        assertEquals("a six earns another roll", LudoColor.RED, state.currentColor)
        assertTrue(state.canRoll)
    }

    @Test
    fun tokensThatCannotMoveDoNotRespondToTaps() = runTest(dispatcher) {
        viewModel.useDice(LoadedDie(6))
        viewModel.newGame(twoHumans)
        advanceUntilIdle()

        // Nothing has been rolled yet, so no token is playable.
        viewModel.onTokenTapped(LudoColor.RED, 0)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.game.allTokens.all { it.isInYard })

        // The other player's tokens stay put too.
        viewModel.rollDice()
        advanceUntilIdle()
        viewModel.onTokenTapped(LudoColor.YELLOW, 0)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.game.player(LudoColor.YELLOW).tokens.all { it.isInYard })
    }

    @Test
    fun aRollWithNothingToPlayHandsTheTurnOn() = runTest(dispatcher) {
        viewModel.useDice(LoadedDie(3))
        viewModel.newGame(twoHumans)
        advanceUntilIdle()

        viewModel.rollDice()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(LudoColor.YELLOW, state.currentColor)
        assertNull(state.game.dice)
        assertTrue(state.canRoll)
    }

    @Test
    fun theDieCannotBeRolledTwiceInOneTurn() = runTest(dispatcher) {
        viewModel.useDice(LoadedDie(6))
        viewModel.newGame(twoHumans)
        advanceUntilIdle()

        viewModel.rollDice()
        advanceUntilIdle()
        assertFalse("a roll is already waiting to be used", viewModel.state.value.canRoll)

        viewModel.rollDice()
        advanceUntilIdle()
        assertEquals(6, viewModel.state.value.game.dice)
        assertEquals(0, viewModel.state.value.game.player(LudoColor.RED).tokens[0].progress)
    }

    @Test
    fun computerPlayersTakeTheirOwnTurns() = runTest(dispatcher) {
        viewModel.useDice(Random(4))
        viewModel.newGame(
            LudoSetup(playerCount = 4, botColors = LudoColor.entries.toSet())
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("a table of bots should play itself out", state.game.isOver)
        assertNotNull(state.game.winner)
        assertEquals(
            LudoBoard.TOKENS_PER_PLAYER,
            state.game.player(state.game.winner!!).tokensHome
        )
        assertFalse(state.resultDismissed)
    }

    @Test
    fun theHumanIsLeftToPlayTheirOwnTurn() = runTest(dispatcher) {
        viewModel.useDice(Random(11))
        viewModel.newGame(LudoSetup(playerCount = 4, botColors = setOf(LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)))
        advanceUntilIdle()

        // Red is human and moves first, so the bots have not started.
        val state = viewModel.state.value
        assertEquals(LudoColor.RED, state.currentColor)
        assertFalse(state.currentIsBot)
        assertTrue(state.canRoll)
    }

    @Test
    fun startingANewGameClearsTheBoard() = runTest(dispatcher) {
        viewModel.useDice(LoadedDie(6))
        viewModel.newGame(twoHumans)
        advanceUntilIdle()
        viewModel.rollDice()
        advanceUntilIdle()
        viewModel.onTokenTapped(LudoColor.RED, 0)
        advanceUntilIdle()

        viewModel.newGame(twoHumans)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.game.allTokens.all { it.isInYard })
        assertEquals(LudoColor.RED, state.currentColor)
        assertNull(state.game.dice)
        assertNull(state.lastMove)
    }

    @Test
    fun aTableCanBeSetForTwoThreeOrFourPlayers() = runTest(dispatcher) {
        listOf(
            2 to listOf(LudoColor.RED, LudoColor.YELLOW),
            3 to listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW),
            4 to LudoColor.entries.toList()
        ).forEach { (count, expectedSeats) ->
            viewModel.newGame(LudoSetup(playerCount = count, botColors = emptySet()))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("$count players", count, state.game.players.size)
            assertEquals(expectedSeats, state.game.players.map { it.color })
            assertEquals(count, state.setup.playerCount)
            // Colours that are not playing have no corner to show.
            LudoColor.entries.filter { it !in expectedSeats }.forEach {
                assertNull(state.seat(it))
            }
        }
    }

    @Test
    fun theTableSetupIsOfferedOnlyOnce() = runTest(dispatcher) {
        assertFalse("the screen should open on the setup sheet", viewModel.state.value.setupSeen)

        viewModel.markSetupSeen()
        assertTrue(viewModel.state.value.setupSeen)

        viewModel.newGame(twoHumans)
        advanceUntilIdle()
        assertTrue("starting a game does not re-open it", viewModel.state.value.setupSeen)
    }

    @Test
    fun theDieBelongsToWhoeverIsOnTurn() = runTest(dispatcher) {
        viewModel.useDice(LoadedDie(3))
        viewModel.newGame(twoHumans)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isOnTurn(LudoColor.RED))
        assertFalse(viewModel.state.value.isOnTurn(LudoColor.YELLOW))

        // A three does nothing at the start, so the die passes to the other corner.
        viewModel.rollDice()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isOnTurn(LudoColor.RED))
        assertTrue(viewModel.state.value.isOnTurn(LudoColor.YELLOW))
    }

    @Test
    fun blockadesCanBeTurnedOnForAGame() = runTest(dispatcher) {
        viewModel.newGame(twoHumans.copy(rules = twoHumans.rules.copy(blockades = true)))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.game.rules.blockades)
    }

    @Test
    fun soundAndVibrationCanBeSwitchedOff() = runTest(dispatcher) {
        assertTrue(viewModel.state.value.soundEnabled)
        viewModel.toggleSound()
        assertFalse(viewModel.state.value.soundEnabled)
        viewModel.toggleVibration()
        assertFalse(viewModel.state.value.vibrationEnabled)

        // The preferences survive a new game.
        viewModel.newGame(twoHumans)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.soundEnabled)
    }
}
