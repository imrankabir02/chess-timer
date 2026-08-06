package com.example.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The computer opponent. The levels are checked for the things they are sold on: all three play
 * legal chess and can finish a game off, the two thinking levels see a move ahead of the board,
 * and the easy one is loose enough to be beaten.
 */
class ChessBotTest {

    private val thinkingLevels = listOf(ChessBotLevel.MEDIUM, ChessBotLevel.HARD)

    /** A queen is hanging on g4 after 1.f3 e5 2.g4?? Qxg4 — here White can just take it. */
    private val hangingBlackQueen =
        "rnb1kbnr/pppp1ppp/8/4p3/6q1/5P2/PPPPP1PP/RNBQKBNR w KQkq - 0 3"

    /** After 1.e4 e5 2.Qh5 Nc6, taking on e5 with the queen drops it to Nxe5. */
    private val queenGrabTrap =
        "r1bqkbnr/pppp1ppp/2n5/4p2Q/4P3/8/PPPP1PPP/RNB1KBNR w KQkq - 4 3"

    /** Two rooks against a bare king: 1.Rb1 (or Rb2) and mate next move. */
    private val mateInTwo = "7K/8/8/k7/8/8/7R/7R w - - 0 1"

    private fun playOut(
        start: ChessGame,
        white: ChessBotLevel,
        black: ChessBotLevel,
        maxPlies: Int,
        seed: Int = 1
    ): ChessGame {
        val random = Random(seed)
        var game = start
        while (!game.isOver && game.moveCount < maxPlies) {
            val level = if (game.sideToMove == PieceColor.WHITE) white else black
            val move = ChessBot.chooseMove(game, level, random)
            assertNotNull("a level must always have a move while the game is on", move)
            assertTrue(
                "$level played ${move!!.uci}, which is not legal in ${game.position.toFen()}",
                game.legalMoves.any { it.from == move.from && it.to == move.to && it.promotion == move.promotion }
            )
            game = game.play(move) ?: error("the engine rejected its own move ${move.uci}")
        }
        return game
    }

    @Test
    fun everyLevelPlaysALegalMove() {
        val positions = listOf(
            Position.initial(),
            Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"),
            Position.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 b - - 0 1")
        )
        for (position in positions) {
            for (level in ChessBotLevel.entries) {
                val move = ChessBot.chooseMove(position, level, Random(4))
                assertNotNull("$level found no move in ${position.toFen()}", move)
                assertTrue(
                    "$level played an illegal move in ${position.toFen()}",
                    position.legalMoves().contains(move)
                )
            }
        }
    }

    @Test
    fun everyLevelFinishesTheGameOffWhenMateIsThere() {
        // Rook to the eighth rank is mate; the king is walled in by its own pawns.
        val position = Position.fromFen("6k1/5ppp/8/8/8/8/8/R3K3 w Q - 0 1")
        for (level in ChessBotLevel.entries) {
            val move = ChessBot.chooseMove(position, level, Random(7))
            assertEquals("$level missed mate in one", "a1a8", move?.uci)
        }
    }

    @Test
    fun theThinkingLevelsForceAMateInTwo() {
        for (level in thinkingLevels) {
            val game = playOut(ChessGame.fromFen(mateInTwo), white = level, black = level, maxPlies = 4)
            assertEquals(
                "$level did not mate in two: ${game.history.joinToString(" ") { it.san }}",
                GameEndReason.CHECKMATE,
                game.result?.reason
            )
            assertEquals(GameOutcome.WHITE_WINS, game.result?.outcome)
        }
    }

    @Test
    fun theThinkingLevelsTakeAPieceThatIsGivenAway() {
        val position = Position.fromFen(hangingBlackQueen)
        for (level in thinkingLevels) {
            for (seed in 1..5) {
                assertEquals(
                    "$level left the queen on g4 with seed $seed",
                    "f3g4",
                    ChessBot.chooseMove(position, level, Random(seed))?.uci
                )
            }
        }
    }

    @Test
    fun theThinkingLevelsDoNotGrabAPawnAndLoseTheQueenForIt() {
        val position = Position.fromFen(queenGrabTrap)
        for (level in thinkingLevels) {
            for (seed in 1..5) {
                val move = ChessBot.chooseMove(position, level, Random(seed))
                assertTrue(
                    "$level played Qxe5 with seed $seed and hangs the queen to Nxe5",
                    move?.uci != "h5e5"
                )
            }
        }
    }

    @Test
    fun theEasyLevelIsLooseEnoughToVaryItsPlay() {
        val choices = (1..12)
            .mapNotNull { seed -> ChessBot.chooseMove(Position.initial(), ChessBotLevel.EASY, Random(seed))?.uci }
            .distinct()
        assertTrue("the easy level always played $choices", choices.size > 1)
    }

    @Test
    fun theHardLevelMatesWithAQueenAgainstABareKing() {
        // A won endgame has to be converted, not shuffled into the fifty-move rule or a stalemate.
        val game = playOut(
            start = ChessGame.fromFen("7k/8/8/8/8/8/5Q2/6K1 w - - 0 1"),
            white = ChessBotLevel.HARD,
            black = ChessBotLevel.HARD,
            maxPlies = 60
        )
        assertEquals(
            "the queen endgame ended as ${game.result?.reason} after ${game.moveCount} half-moves",
            GameEndReason.CHECKMATE,
            game.result?.reason
        )
        assertEquals(GameOutcome.WHITE_WINS, game.result?.outcome)
    }

    @Test
    fun aWholeGameBetweenTwoLevelsStaysLegal() {
        val game = playOut(
            start = ChessGame.new(),
            white = ChessBotLevel.MEDIUM,
            black = ChessBotLevel.EASY,
            maxPlies = 80,
            seed = 12
        )
        // playOut asserts every move as it goes; what is left is that the game is still coherent.
        assertEquals(game.position.toFen(), Position.fromFen(game.position.toFen()).toFen())
        assertTrue("the game made no progress", game.moveCount > 0)
    }

    @Test
    fun aDecidedGameHasNoMoveToMake() {
        val mated = ChessGame.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertTrue("the test position should already be mate", mated.isOver)
        for (level in ChessBotLevel.entries) {
            assertNull(ChessBot.chooseMove(mated, level, Random(1)))
        }
    }

    @Test
    fun theEvaluationCountsMaterialFromTheSideToMovesPointOfView() {
        assertEquals(0, ChessBot.evaluate(Position.initial()))

        // Black is a rook down, so the score is positive for White and negative for Black.
        val whiteAhead = Position.fromFen("1nbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQk - 0 1")
        assertTrue(ChessBot.evaluate(whiteAhead) > 400)
        val blackToMove = Position.fromFen("1nbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQk - 0 1")
        assertTrue(ChessBot.evaluate(blackToMove) < -400)
    }
}
