package com.example.model

import com.example.ludo.LudoColor
import com.example.ludo.LudoGame
import com.example.ludo.LudoRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoSetupTest {

    @Test
    fun twoPlayersSitOppositeEachOther() {
        val seats = LudoSetup(playerCount = 2).seats
        assertEquals(listOf(LudoColor.RED, LudoColor.YELLOW), seats)
    }

    @Test
    fun threeAndFourPlayerTablesFillTheBoardInOrder() {
        assertEquals(
            listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW),
            LudoSetup(playerCount = 3).seats
        )
        assertEquals(LudoColor.entries.toList(), LudoSetup(playerCount = 4).seats)
    }

    @Test
    fun everySeatCountBuildsAMatchingGame() {
        (2..4).forEach { count ->
            val setup = LudoSetup(playerCount = count)
            val game = LudoGame.new(count, setup.activeBots, setup.rules)
            assertEquals("$count players", count, game.players.size)
            assertEquals(setup.seats, game.players.map { it.color })
        }
    }

    @Test
    fun botsAreIgnoredForColoursThatAreNotPlaying() {
        // Green and Blue are not at a two player table, so only Yellow is a computer.
        val setup = LudoSetup(
            playerCount = 2,
            botColors = setOf(LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
        )
        assertEquals(setOf(LudoColor.YELLOW), setup.activeBots)
        assertEquals(1, setup.humanCount)

        val game = LudoGame.new(setup.playerCount, setup.activeBots, setup.rules)
        assertFalse(game.player(LudoColor.RED).isBot)
        assertTrue(game.player(LudoColor.YELLOW).isBot)
    }

    @Test
    fun theTableTurnsRoundOnlyWhenSeveralPeopleArePlaying() {
        val soloVersusComputers = LudoSetup(
            playerCount = 4,
            botColors = setOf(LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
        )
        assertFalse("one human should see everything upright", soloVersusComputers.tableMode)

        val twoFriends = LudoSetup(
            playerCount = 4,
            botColors = setOf(LudoColor.YELLOW, LudoColor.BLUE)
        )
        assertTrue("two people round a phone need facing panels", twoFriends.tableMode)

        assertTrue(LudoSetup(playerCount = 2, botColors = emptySet()).tableMode)
    }

    @Test
    fun theSummaryLineDescribesTheTable() {
        val summary = LudoSetup(
            playerCount = 3,
            botColors = setOf(LudoColor.GREEN, LudoColor.YELLOW)
        ).describe()
        assertTrue(summary, summary.startsWith("3 players"))
        assertTrue(summary, summary.contains("1 human"))
        assertTrue(summary, summary.contains("2 computer"))

        val withBlockades = LudoSetup(rules = LudoRules(blockades = true)).describe()
        assertTrue(withBlockades, withBlockades.contains("blockades on"))
    }

    @Test
    fun onlySeatsInPlayHaveACorner() {
        val setup = LudoSetup(playerCount = 2)
        val state = LudoUiState(
            game = LudoGame.new(setup.playerCount, setup.activeBots, setup.rules),
            setup = setup
        )
        assertNotNull(state.seat(LudoColor.RED))
        assertNotNull(state.seat(LudoColor.YELLOW))
        assertNull("Green is not at a two player table", state.seat(LudoColor.GREEN))
        assertNull(state.seat(LudoColor.BLUE))
    }

    @Test
    fun theCornerOnTurnIsTheOneHoldingTheDie() {
        val state = LudoUiState(game = LudoGame.new(playerCount = 4))
        assertTrue(state.isOnTurn(LudoColor.RED))
        assertFalse(state.isOnTurn(LudoColor.GREEN))

        val afterRed = state.copy(game = state.game.copy(turnIndex = 1))
        assertFalse(afterRed.isOnTurn(LudoColor.RED))
        assertTrue(afterRed.isOnTurn(LudoColor.GREEN))
    }

    @Test
    fun theSetupIsOfferedOnceWhenTheTableOpens() {
        assertFalse(LudoUiState().setupSeen)
        assertTrue(LudoUiState().copy(setupSeen = true).setupSeen)
    }
}
