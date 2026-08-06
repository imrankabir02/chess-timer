package com.example.ludo

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoRulesTest {

    /** Builds a position directly: each colour is given the progress of its four tokens. */
    private fun gameOf(
        vararg placements: Pair<LudoColor, List<Int>>,
        turn: LudoColor = placements.first().first,
        dice: Int? = null,
        rules: LudoRules = LudoRules(),
        bots: Set<LudoColor> = emptySet()
    ): LudoGame {
        val players = placements.map { (color, progresses) ->
            val padded = (progresses + List(LudoBoard.TOKENS_PER_PLAYER) { LudoBoard.YARD })
                .take(LudoBoard.TOKENS_PER_PLAYER)
            LudoPlayer(
                color = color,
                isBot = color in bots,
                tokens = padded.mapIndexed { index, progress -> LudoToken(color, index, progress) }
            )
        }
        return LudoGame(
            players = players,
            turnIndex = players.indexOfFirst { it.color == turn },
            dice = dice,
            rules = rules
        )
    }

    // region Getting on the board

    @Test
    fun aTokenOnlyLeavesTheYardOnASix() {
        val game = LudoGame.new(playerCount = 2)
        (1..5).forEach { roll ->
            assertTrue("a $roll must not release a token", game.roll(roll).legalMoves.isEmpty())
        }

        val rolledSix = game.roll(6)
        assertEquals(4, rolledSix.legalMoves.size)
        assertTrue(rolledSix.legalMoves.all { it.entersBoard && it.to == 1 })
    }

    @Test
    fun leavingTheYardLandsOnTheStartSquare() {
        val game = LudoGame.new(playerCount = 2).roll(6)
        val after = game.play(game.legalMoves.first())
        val token = after.player(LudoColor.RED).tokens.first()
        assertEquals(1, token.progress)
        assertEquals(LudoBoard.startIndex(LudoColor.RED), token.trackIndex)
        assertTrue(token.isSafe)
    }

    // endregion

    // region Moving and finishing

    @Test
    fun aTokenAdvancesByTheDiceValue() {
        val game = gameOf(LudoColor.RED to listOf(5), LudoColor.YELLOW to emptyList(), dice = 3)
        val move = game.moveForToken(0)!!
        assertEquals(8, move.to)
        assertEquals(8, game.play(move).player(LudoColor.RED).tokens[0].progress)
    }

    @Test
    fun theLastStepHasToBeExact() {
        val game = gameOf(LudoColor.RED to listOf(55), LudoColor.YELLOW to emptyList(), dice = 3)
        assertNull("57 is home, 58 does not exist", game.moveForToken(0))

        val exact = gameOf(LudoColor.RED to listOf(55), LudoColor.YELLOW to emptyList(), dice = 2)
        val move = exact.moveForToken(0)!!
        assertTrue(move.finishes)
        assertTrue(exact.play(move).player(LudoColor.RED).tokens[0].isHome)
    }

    @Test
    fun enteringTheHomeColumnIsFlagged() {
        val game = gameOf(LudoColor.RED to listOf(50), LudoColor.YELLOW to emptyList(), dice = 3)
        val move = game.moveForToken(0)!!
        assertEquals(53, move.to)
        assertTrue(move.entersHomeColumn)
        assertFalse(move.finishes)
        assertTrue(game.play(move).player(LudoColor.RED).tokens[0].isInHomeColumn)
    }

    @Test
    fun aFinishedTokenNeverMovesAgain() {
        val game = gameOf(
            LudoColor.RED to listOf(LudoBoard.HOME, 10),
            LudoColor.YELLOW to emptyList(),
            dice = 4
        )
        assertNull(game.moveForToken(0))
        assertNotNull(game.moveForToken(1))
    }

    // endregion

    // region Captures

    @Test
    fun landingOnAnOpponentSendsItBackToTheYard() {
        // Green's token at progress 42 stands on shared square 2, which Red reaches from 1 with a 2.
        val game = gameOf(
            LudoColor.RED to listOf(1),
            LudoColor.GREEN to listOf(42),
            turn = LudoColor.RED,
            dice = 2
        )
        val move = game.moveForToken(0)!!
        assertEquals(1, move.captured.size)
        assertEquals(LudoColor.GREEN, move.captured.first().color)

        val after = game.play(move)
        assertTrue(after.player(LudoColor.GREEN).tokens[0].isInYard)
        assertEquals(3, after.player(LudoColor.RED).tokens[0].progress)
    }

    @Test
    fun nobodyIsCapturedOnASafeSquare() {
        // Shared square 8 is a star: Red arrives there from 7 with a 2, Green is already sitting on it.
        val game = gameOf(
            LudoColor.RED to listOf(7),
            LudoColor.GREEN to listOf(48),
            turn = LudoColor.RED,
            dice = 2
        )
        val move = game.moveForToken(0)!!
        assertEquals(8, LudoBoard.trackIndexOf(LudoColor.RED, move.to))
        assertTrue("the star square protects both tokens", move.captured.isEmpty())
        assertFalse(game.play(move).player(LudoColor.GREEN).tokens[0].isInYard)
    }

    @Test
    fun twoTokensOnOneSquareAreBothCaptured() {
        val game = gameOf(
            LudoColor.RED to listOf(1),
            LudoColor.GREEN to listOf(42, 42),
            turn = LudoColor.RED,
            dice = 2
        )
        val after = game.play(game.moveForToken(0)!!)
        assertTrue(after.player(LudoColor.GREEN).tokens.none { it.progress == 42 })
        assertTrue("both tokens on the square go back", after.player(LudoColor.GREEN).hasFinished.not())
        assertEquals(4, after.player(LudoColor.GREEN).tokensInYard)
    }

    @Test
    fun aTokenInTheHomeColumnCannotBeTouched() {
        val game = gameOf(
            LudoColor.RED to listOf(52),
            LudoColor.GREEN to listOf(40),
            turn = LudoColor.GREEN,
            dice = 6
        )
        assertTrue(game.legalMoves.none { it.isCapture })
    }

    // endregion

    // region Extra turns and forfeits

    @Test
    fun aSixEarnsAnotherRoll() {
        val game = LudoGame.new(playerCount = 2).roll(6)
        val after = game.play(game.legalMoves.first())
        assertEquals(LudoColor.RED, after.currentColor)
        assertNull("the dice is cleared, ready for the next roll", after.dice)
        assertTrue(after.lastEvents.any { it.kind == LudoEventKind.EXTRA_TURN })
    }

    @Test
    fun aCaptureEarnsAnotherRoll() {
        val game = gameOf(
            LudoColor.RED to listOf(1),
            LudoColor.GREEN to listOf(42),
            turn = LudoColor.RED,
            dice = 2
        )
        val after = game.play(game.moveForToken(0)!!)
        assertEquals(LudoColor.RED, after.currentColor)
    }

    @Test
    fun sendingATokenHomeEarnsAnotherRoll() {
        val game = gameOf(
            LudoColor.RED to listOf(55, 10),
            LudoColor.YELLOW to emptyList(),
            dice = 2
        )
        val after = game.play(game.moveForToken(0)!!)
        assertEquals(LudoColor.RED, after.currentColor)
    }

    @Test
    fun anOrdinaryMoveHandsTheTurnOn() {
        val game = gameOf(
            LudoColor.RED to listOf(5),
            LudoColor.YELLOW to listOf(5),
            turn = LudoColor.RED,
            dice = 3
        )
        val after = game.play(game.moveForToken(0)!!)
        assertEquals(LudoColor.YELLOW, after.currentColor)
        assertNull(after.dice)
    }

    @Test
    fun threeSixesInARowVoidTheTurn() {
        var game = LudoGame.new(playerCount = 2)
        game = game.roll(6)
        assertEquals(1, game.consecutiveSixes)
        game = game.play(game.legalMoves.first())

        game = game.roll(6)
        assertEquals(2, game.consecutiveSixes)
        game = game.play(game.legalMoves.first())

        game = game.roll(6)
        assertEquals("the third six is thrown away", LudoColor.YELLOW, game.currentColor)
        assertNull(game.dice)
        assertEquals(0, game.consecutiveSixes)
    }

    @Test
    fun theSixCounterResetsOnAnyOtherNumber() {
        var game = gameOf(LudoColor.RED to listOf(5), LudoColor.YELLOW to listOf(5), dice = null)
        game = game.roll(6)
        game = game.play(game.legalMoves.first())
        assertEquals(1, game.consecutiveSixes)
        game = game.roll(3)
        assertEquals(0, game.consecutiveSixes)
    }

    @Test
    fun aRollWithNothingToPlayPassesTheTurn() {
        val game = LudoGame.new(playerCount = 2).roll(4)
        assertTrue(game.legalMoves.isEmpty())

        val passed = game.skipTurn()
        assertEquals(LudoColor.YELLOW, passed.currentColor)
        assertNull(passed.dice)
        assertTrue(passed.lastEvents.any { it.kind == LudoEventKind.NO_MOVES })
    }

    @Test
    fun theTurnCannotBeSkippedWhileAMoveExists() {
        val game = LudoGame.new(playerCount = 2).roll(6)
        assertEquals(game, game.skipTurn())
    }

    // endregion

    // region Blockades

    @Test
    fun aPairOfEnemyTokensBlocksThePathWhenTheRuleIsOn() {
        val blocked = gameOf(
            LudoColor.RED to listOf(1),
            LudoColor.GREEN to listOf(42, 42),
            turn = LudoColor.RED,
            dice = 3,
            rules = LudoRules(blockades = true)
        )
        assertNull("Red cannot pass through the pair on square 2", blocked.moveForToken(0))

        val open = blocked.copy(rules = LudoRules(blockades = false))
        assertNotNull("without the rule the token walks straight past", open.moveForToken(0))
    }

    @Test
    fun aBlockadeCannotBeLandedOnEither() {
        val game = gameOf(
            LudoColor.RED to listOf(1),
            LudoColor.GREEN to listOf(42, 42),
            turn = LudoColor.RED,
            dice = 2,
            rules = LudoRules(blockades = true)
        )
        assertNull(game.moveForToken(0))
    }

    @Test
    fun yourOwnTokensNeverBlockYou() {
        val game = gameOf(
            LudoColor.RED to listOf(1, 3, 3),
            LudoColor.GREEN to emptyList(),
            turn = LudoColor.RED,
            dice = 4,
            rules = LudoRules(blockades = true)
        )
        assertNotNull(game.moveForToken(0))
    }

    // endregion

    // region Finishing the game

    @Test
    fun gettingAllFourHomeFinishesAPlayer() {
        val game = gameOf(
            LudoColor.RED to listOf(LudoBoard.HOME, LudoBoard.HOME, LudoBoard.HOME, 56),
            LudoColor.YELLOW to listOf(10),
            turn = LudoColor.RED,
            dice = 1
        )
        val after = game.play(game.moveForToken(3)!!)

        assertTrue(after.player(LudoColor.RED).hasFinished)
        assertEquals(listOf(LudoColor.RED), after.finishOrder)
        assertTrue(after.isOver)
        assertEquals(LudoColor.RED, after.winner)
        assertEquals(listOf(LudoColor.RED, LudoColor.YELLOW), after.standings)
        assertTrue(after.lastEvents.any { it.kind == LudoEventKind.GAME_OVER })
    }

    @Test
    fun aFourPlayerGameRunsOnUntilOnlyOnePlayerIsLeft() {
        val game = gameOf(
            LudoColor.RED to listOf(LudoBoard.HOME, LudoBoard.HOME, LudoBoard.HOME, 56),
            LudoColor.GREEN to listOf(10),
            LudoColor.YELLOW to listOf(10),
            LudoColor.BLUE to listOf(10),
            turn = LudoColor.RED,
            dice = 1
        )
        val after = game.play(game.moveForToken(3)!!)
        assertFalse("two more players are still racing", after.isOver)
        assertEquals(listOf(LudoColor.RED), after.finishOrder)
        assertEquals("the finished player is skipped", LudoColor.GREEN, after.currentColor)
    }

    @Test
    fun finishedPlayersAreSkippedWhenTheTurnComesRound() {
        val game = gameOf(
            LudoColor.RED to listOf(5),
            LudoColor.GREEN to List(4) { LudoBoard.HOME },
            LudoColor.YELLOW to listOf(5),
            turn = LudoColor.RED,
            dice = 3
        )
        val after = game.play(game.moveForToken(0)!!)
        assertEquals(LudoColor.YELLOW, after.currentColor)
    }

    // endregion

    // region Whole games

    @Test
    fun completeGamesFinishWithValidState() {
        repeat(30) { seed ->
            val random = Random(seed)
            var game = LudoGame.new(playerCount = if (seed % 3 == 0) 2 else 4)
            var guard = 0

            while (!game.isOver && guard < 100_000) {
                guard++
                game = game.roll(random.nextInt(1, 7))
                if (game.isOver) break
                if (game.dice == null) continue // the roll was voided by three sixes
                game = if (game.legalMoves.isEmpty()) {
                    game.skipTurn()
                } else {
                    game.play(LudoBot.chooseMove(game)!!)
                }

                game.players.forEach { player ->
                    assertEquals(LudoBoard.TOKENS_PER_PLAYER, player.tokens.size)
                    player.tokens.forEach { token ->
                        assertTrue(
                            "progress ${token.progress} is out of range",
                            token.progress in LudoBoard.YARD..LudoBoard.HOME
                        )
                    }
                }
            }

            assertTrue("game $seed never finished (took $guard turns)", game.isOver)
            assertNotNull(game.winner)
            assertTrue(game.player(game.winner!!).hasFinished)
            assertEquals(game.players.size, game.standings.size)
        }
    }

    @Test
    fun blockadeGamesAlsoFinish() {
        val random = Random(7)
        var game = LudoGame.new(playerCount = 4, rules = LudoRules(blockades = true))
        var guard = 0
        while (!game.isOver && guard < 100_000) {
            guard++
            game = game.roll(random.nextInt(1, 7))
            if (game.dice == null) continue
            game = if (game.legalMoves.isEmpty()) game.skipTurn() else game.play(LudoBot.chooseMove(game)!!)
        }
        assertTrue("a game with blockades deadlocked", game.isOver)
    }

    // endregion
}
