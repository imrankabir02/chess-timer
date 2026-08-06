package com.example.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SanTest {

    private fun san(fen: String, from: String, to: String, promotion: PieceType? = null): String {
        val position = Position.fromFen(fen)
        val move = position.findLegalMove(Square.fromName(from), Square.fromName(to), promotion)
            ?: error("$from$to is not legal in $fen")
        return San.toSan(position, move)
    }

    @Test
    fun quietAndCapturingMovesUseStandardNotation() {
        assertEquals("e4", san(Position.START_FEN, "e2", "e4"))
        assertEquals("Nf3", san(Position.START_FEN, "g1", "f3"))
        assertEquals(
            "exd5",
            san("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2", "e4", "d5")
        )
        assertEquals(
            "Bxf7+",
            san("rnbqkbnr/pppp1ppp/8/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR w KQkq - 0 3", "c4", "f7")
        )
    }

    @Test
    fun castlingUsesONotation() {
        val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
        assertEquals("O-O", san(fen, "e1", "g1"))
        assertEquals("O-O-O", san(fen, "e1", "c1"))
    }

    @Test
    fun promotionsCarryTheChosenPiece() {
        val fen = "8/4P3/8/8/8/8/8/K6k w - - 0 1"
        assertEquals("e8=Q", san(fen, "e7", "e8", PieceType.QUEEN))
        assertEquals("e8=N", san(fen, "e7", "e8", PieceType.KNIGHT))
        assertEquals(
            "exd8=Q+",
            san("3r3k/4P3/8/8/8/8/8/K7 w - - 0 1", "e7", "d8", PieceType.QUEEN)
        )
    }

    @Test
    fun ambiguousMovesAreDisambiguated() {
        // Two knights can reach d2 from different files, so the file letter is enough.
        val knights = "4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1"
        assertEquals("Nbd2", san(knights, "b1", "d2"))
        assertEquals("Nfd2", san(knights, "f3", "d2"))

        // Rooks sharing the a-file need the rank instead.
        val rooks = "4k3/8/8/R7/8/8/8/R3K3 w - - 0 1"
        assertEquals("R1a3", san(rooks, "a1", "a3"))
        assertEquals("R5a3", san(rooks, "a5", "a3"))

        // Three queens cover d1: h1 is alone on rank 1, the other two need file or full square.
        val queens = "1k6/8/8/3Q3Q/8/8/8/K6Q w - - 0 1"
        assertEquals("Q1d1", san(queens, "h1", "d1"))
        assertEquals("Qh5d1", san(queens, "h5", "d1"))
        assertEquals("Qdd1", san(queens, "d5", "d1"))
    }

    @Test
    fun checkmateIsMarkedWithAHash() {
        assertEquals(
            "Qh4#",
            san("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2", "d8", "h4")
        )
    }

    @Test
    fun sanParsingRoundTripsEveryLegalMove() {
        var position = Position.fromFen("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6")
        repeat(6) {
            val moves = position.legalMoves()
            if (moves.isEmpty()) return
            for (move in moves) {
                val text = San.toSan(position, move)
                assertEquals("round trip of $text", move, San.parse(position, text))
            }
            position = position.makeMove(moves.first())
        }
    }

    @Test
    fun illegalSanIsRejected() {
        assertNull(San.parse(Position.initial(), "e5"))
        assertNull(San.parse(Position.initial(), "O-O"))
        assertNull(San.parse(Position.initial(), "zz"))
    }
}
