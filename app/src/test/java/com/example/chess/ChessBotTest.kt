package com.example.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The computer opponent. These run on the JVM with no Android around them, and every one of them
 * seeds the bot so a failure can be reproduced exactly.
 */
class ChessBotTest {

    private fun bot(difficulty: ChessDifficulty, seed: Int = 1) = ChessBot(difficulty, Random(seed))

    private fun isCheckmate(position: Position, move: Move): Boolean {
        val after = position.makeMove(move)
        return after.legalMoves().isEmpty() && after.isInCheck(after.sideToMove)
    }

    /** Plays the two sides off against each other and returns the finished game. */
    private fun playOut(white: ChessBot, black: ChessBot, from: ChessGame, maxPlies: Int): ChessGame {
        var game = from
        var plies = 0
        while (!game.isOver && plies < maxPlies) {
            val mover = if (game.sideToMove == PieceColor.WHITE) white else black
            val move = mover.chooseMove(game) ?: break
            val played = game.play(move)
            assertNotNull("the bot offered a move the rules reject: $move", played)
            game = played!!
            plies++
        }
        return game
    }

    @Test
    fun everyLevelDeliversMateInOne() {
        // Ra1 takes the open a-file to a8 and the black king has no flight square.
        val position = Position.fromFen("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")
        for (difficulty in ChessDifficulty.entries) {
            val move = bot(difficulty).chooseMove(position)
            assertNotNull("$difficulty found no move at all", move)
            assertTrue("$difficulty missed mate in one, played $move", isCheckmate(position, move!!))
        }
    }

    @Test
    fun everyLevelTakesAFreeQueen() {
        // The black queen on d5 is undefended, with both the e4 pawn and the c3 knight attacking it.
        val position = Position.fromFen("4k3/8/8/3q4/4P3/2N5/8/4K3 w - - 0 1")
        for (difficulty in ChessDifficulty.entries) {
            val move = bot(difficulty).chooseMove(position)
            assertNotNull("$difficulty found no move at all", move)
            assertEquals(
                "$difficulty left the queen on the board, playing $move",
                PieceType.QUEEN,
                move!!.captured?.type
            )
        }
    }

    @Test
    fun onlyTheDeepestLevelSeesAForcedMateInTwo() {
        // Black to move: 1... Kg3 2. (anything) Rf1#. Three plies deep, so it is out of reach of a
        // two-ply search — which is exactly what separates hard from the rest.
        val fen = "7r/ppp3p1/4b2p/8/5k2/8/5r2/6K1 b - - 9 35"
        val finished = playOut(
            white = bot(ChessDifficulty.HARD, seed = 2),
            black = bot(ChessDifficulty.HARD, seed = 1),
            from = ChessGame.fromFen(fen),
            maxPlies = 3
        )
        assertEquals(GameEndReason.CHECKMATE, finished.result?.reason)
        assertEquals(GameOutcome.BLACK_WINS, finished.result?.outcome)

        val easy = bot(ChessDifficulty.EASY).chooseMove(Position.fromFen(fen))
        assertFalse("easy is not supposed to find this", easy?.uci == "f4g3")
    }

    @Test
    fun theSameSeedAlwaysPlaysTheSameMove() {
        val first = ChessBot(ChessDifficulty.EASY, Random(42)).chooseMove(ChessGame.new())
        val second = ChessBot(ChessDifficulty.EASY, Random(42)).chooseMove(ChessGame.new())
        assertEquals(first, second)
    }

    @Test
    fun theEasierLevelsDoNotPlayTheSameOpeningEveryTime() {
        val openings = (1..12)
            .map { seed -> ChessBot(ChessDifficulty.EASY, Random(seed)).chooseMove(ChessGame.new()) }
            .toSet()
        assertTrue("easy should mix its openings up, got $openings", openings.size > 1)
    }

    @Test
    fun theHardestLevelIsNotRandomAtAll() {
        val moves = (1..6)
            .map { seed -> ChessBot(ChessDifficulty.HARD, Random(seed)).chooseMove(ChessGame.new()) }
            .toSet()
        assertEquals("hard always plays the best move it found", 1, moves.size)
    }

    @Test
    fun itConvertsAWonEndgameInsteadOfShufflingForEver() {
        // King and queen against a bare king. Material alone gives the search no reason to make
        // progress, so this is the test that the mating logic in the evaluation is doing its job.
        val finished = playOut(
            white = bot(ChessDifficulty.HARD, seed = 1),
            black = bot(ChessDifficulty.HARD, seed = 2),
            from = ChessGame.fromFen("7k/8/8/8/8/8/6QK/8 w - - 0 1"),
            maxPlies = 100
        )
        assertEquals(GameEndReason.CHECKMATE, finished.result?.reason)
        assertEquals(GameOutcome.WHITE_WINS, finished.result?.outcome)
    }

    @Test
    fun aHarderLevelBeatsAnEasierOne() {
        // Two games with the colours swapped, so neither result is down to having the first move.
        val asWhite = playOut(
            white = bot(ChessDifficulty.MEDIUM, seed = 3),
            black = bot(ChessDifficulty.EASY, seed = 4),
            from = ChessGame.new(),
            maxPlies = 120
        )
        assertEquals(
            "medium should beat easy with the white pieces",
            PieceColor.WHITE,
            asWhite.result?.winner
        )

        val asBlack = playOut(
            white = bot(ChessDifficulty.EASY, seed = 5),
            black = bot(ChessDifficulty.MEDIUM, seed = 6),
            from = ChessGame.new(),
            maxPlies = 120
        )
        assertEquals(
            "medium should beat easy with the black pieces too",
            PieceColor.BLACK,
            asBlack.result?.winner
        )
    }

    @Test
    fun itOnlyEverOffersLegalMoves() {
        // playOut asserts on every move it plays; this just makes sure a whole game is covered.
        val finished = playOut(
            white = bot(ChessDifficulty.MEDIUM, seed = 7),
            black = bot(ChessDifficulty.MEDIUM, seed = 8),
            from = ChessGame.new(),
            maxPlies = 60
        )
        assertTrue("expected a real game to have been played", finished.moveCount > 10)
    }

    @Test
    fun aDrawOfferIsAcceptedWhenLosingAndDeclinedWhenWinning() {
        // Black is a whole queen down.
        val lopsided = ChessGame.fromFen("4k3/8/8/8/8/8/8/3QK3 b - - 0 1")
        for (difficulty in ChessDifficulty.entries) {
            assertTrue(
                "$difficulty should take the draw when a queen down",
                bot(difficulty).acceptsDrawOffer(lopsided, PieceColor.BLACK)
            )
            assertFalse(
                "$difficulty should play on when a queen up",
                bot(difficulty).acceptsDrawOffer(lopsided, PieceColor.WHITE)
            )
        }
    }

    @Test
    fun theHarderLevelsPlayOnFromAnEvenPosition() {
        val start = ChessGame.new()
        assertFalse(
            "medium should want to play on from the starting position",
            bot(ChessDifficulty.MEDIUM).acceptsDrawOffer(start, PieceColor.BLACK)
        )
        assertFalse(
            "hard should want to play on from the starting position",
            bot(ChessDifficulty.HARD).acceptsDrawOffer(start, PieceColor.BLACK)
        )
    }

    @Test
    fun aFinishedGameHasNoMoveToOffer() {
        val resigned = ChessGame.new().resign(PieceColor.WHITE)
        val stalemate = ChessGame.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue("fixture should be stalemate", stalemate.isStalemate)

        for (difficulty in ChessDifficulty.entries) {
            assertEquals(null, bot(difficulty).chooseMove(resigned))
            assertEquals(null, bot(difficulty).chooseMove(stalemate))
            assertFalse(bot(difficulty).acceptsDrawOffer(resigned, PieceColor.BLACK))
        }
    }
}
