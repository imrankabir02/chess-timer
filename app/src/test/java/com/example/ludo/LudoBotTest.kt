package com.example.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoBotTest {

    private fun gameOf(
        vararg placements: Pair<LudoColor, List<Int>>,
        turn: LudoColor = placements.first().first,
        dice: Int? = null
    ): LudoGame {
        val players = placements.map { (color, progresses) ->
            val padded = (progresses + List(LudoBoard.TOKENS_PER_PLAYER) { LudoBoard.YARD })
                .take(LudoBoard.TOKENS_PER_PLAYER)
            LudoPlayer(color, true, padded.mapIndexed { i, p -> LudoToken(color, i, p) })
        }
        return LudoGame(players, players.indexOfFirst { it.color == turn }, dice)
    }

    @Test
    fun itTakesTheCaptureOverAQuietMove() {
        // Token 0 can knock Green off square 2; token 1 can only shuffle forward.
        val game = gameOf(
            LudoColor.RED to listOf(1, 20),
            LudoColor.GREEN to listOf(42),
            turn = LudoColor.RED,
            dice = 2
        )
        val chosen = LudoBot.chooseMove(game)!!
        assertEquals(0, chosen.token.index)
        assertTrue(chosen.isCapture)
    }

    @Test
    fun itSendsATokenHomeWhenItCan() {
        val game = gameOf(
            LudoColor.RED to listOf(55, 20),
            LudoColor.GREEN to emptyList(),
            turn = LudoColor.RED,
            dice = 2
        )
        val chosen = LudoBot.chooseMove(game)!!
        assertTrue(chosen.finishes)
    }

    @Test
    fun itGetsATokenOutWhenTheBoardIsEmpty() {
        val game = gameOf(
            LudoColor.RED to emptyList(),
            LudoColor.GREEN to emptyList(),
            turn = LudoColor.RED,
            dice = 6
        )
        val chosen = LudoBot.chooseMove(game)!!
        assertTrue(chosen.entersBoard)
    }

    @Test
    fun itPrefersASafeSquareToAnExposedOne() {
        // Token 0 lands on the star at shared square 8; token 1 lands in front of a Green token.
        val game = gameOf(
            LudoColor.RED to listOf(7, 30),
            LudoColor.GREEN to listOf(20),
            turn = LudoColor.RED,
            dice = 2
        )
        val safeMove = game.moveForToken(0)!!
        val exposedMove = game.moveForToken(1)!!
        assertTrue(
            "the star square should score higher",
            LudoBot.score(game, safeMove) > LudoBot.score(game, exposedMove)
        )
    }

    @Test
    fun itAvoidsParkingInFrontOfAnOpponent() {
        // Green sits on shared square 20; Red landing on 22 would be three steps in front of it.
        val exposedTarget = 23 // Red progress 23 is shared square 22
        val game = gameOf(
            LudoColor.RED to listOf(20),
            LudoColor.GREEN to listOf(60 - 52 + 1),
            turn = LudoColor.RED,
            dice = 3
        )
        val move = game.moveForToken(0)!!
        assertEquals(exposedTarget, move.to)
        // The score is dragged down by the risk, so it stays below the same move with no threat.
        val unthreatened = gameOf(
            LudoColor.RED to listOf(20),
            LudoColor.GREEN to emptyList(),
            turn = LudoColor.RED,
            dice = 3
        )
        assertTrue(
            LudoBot.score(game, move) < LudoBot.score(unthreatened, unthreatened.moveForToken(0)!!)
        )
    }

    @Test
    fun itAlwaysReturnsALegalMoveOrNothingAtAll() {
        val stuck = gameOf(
            LudoColor.RED to emptyList(),
            LudoColor.GREEN to emptyList(),
            turn = LudoColor.RED,
            dice = 3
        )
        assertNull(LudoBot.chooseMove(stuck))

        val open = gameOf(
            LudoColor.RED to listOf(4, 9, 30, 44),
            LudoColor.GREEN to listOf(12),
            turn = LudoColor.RED,
            dice = 5
        )
        val chosen = LudoBot.chooseMove(open)
        assertNotNull(chosen)
        assertTrue(chosen in open.legalMoves)
    }
}
