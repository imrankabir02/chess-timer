package com.example.chess

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Move generation is verified with perft: the number of leaf nodes reachable at a given depth.
 * The expected counts are the published values for the standard test positions, so any rule that
 * is subtly wrong (pins, en passant edge cases, castling through check, promotions) shows up here.
 */
class PerftTest {

    private fun perft(position: Position, depth: Int): Long {
        if (depth == 0) return 1L
        val moves = position.legalMoves()
        if (depth == 1) return moves.size.toLong()
        var nodes = 0L
        for (move in moves) {
            nodes += perft(position.makeMove(move), depth - 1)
        }
        return nodes
    }

    private fun assertPerft(fen: String, expected: List<Long>) {
        val position = Position.fromFen(fen)
        expected.forEachIndexed { index, nodes ->
            assertEquals("perft(${index + 1}) for $fen", nodes, perft(position, index + 1))
        }
    }

    @Test
    fun startingPosition() {
        assertPerft(Position.START_FEN, listOf(20L, 400L, 8_902L, 197_281L))
    }

    @Test
    fun kiwipete() {
        assertPerft(
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            listOf(48L, 2_039L, 97_862L)
        )
    }

    @Test
    fun endgameWithEnPassantAndPromotionTricks() {
        assertPerft(
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            listOf(14L, 191L, 2_812L, 43_238L)
        )
    }

    @Test
    fun positionWithManyPromotions() {
        assertPerft(
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            listOf(6L, 264L, 9_467L)
        )
    }

    @Test
    fun mirroredPositionFour() {
        assertPerft(
            "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
            listOf(6L, 264L, 9_467L)
        )
    }

    @Test
    fun crowdedMiddlegame() {
        assertPerft(
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            listOf(44L, 1_486L, 62_379L)
        )
    }

    @Test
    fun stevenEdwardsPosition() {
        assertPerft(
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
            listOf(46L, 2_079L, 89_890L)
        )
    }
}
