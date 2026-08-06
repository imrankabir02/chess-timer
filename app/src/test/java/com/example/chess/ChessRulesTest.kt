package com.example.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessRulesTest {

    private fun sq(name: String) = Square.fromName(name)

    @Test
    fun fenRoundTripsThroughTheBoard() {
        val fen = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3"
        assertEquals(fen, Position.fromFen(fen).toFen())
    }

    @Test
    fun startingPositionIsSetUpCorrectly() {
        val position = Position.initial()
        assertEquals(Piece(PieceColor.WHITE, PieceType.ROOK), position.pieceAt(sq("a1")))
        assertEquals(Piece(PieceColor.WHITE, PieceType.KING), position.pieceAt(sq("e1")))
        assertEquals(Piece(PieceColor.BLACK, PieceType.QUEEN), position.pieceAt(sq("d8")))
        assertEquals(Piece(PieceColor.BLACK, PieceType.PAWN), position.pieceAt(sq("h7")))
        assertNull(position.pieceAt(sq("e4")))
        assertEquals(PieceColor.WHITE, position.sideToMove)
        assertEquals(20, position.legalMoves().size)
    }

    @Test
    fun pawnsPromoteToEveryPieceType() {
        val position = Position.fromFen("8/4P3/8/8/8/8/8/K6k w - - 0 1")
        val promotions = position.legalMovesFrom(sq("e7")).mapNotNull { it.promotion }.toSet()
        assertEquals(
            setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT),
            promotions
        )

        val promoted = position.makeMove(position.findLegalMove(sq("e7"), sq("e8"), PieceType.KNIGHT)!!)
        assertEquals(Piece(PieceColor.WHITE, PieceType.KNIGHT), promoted.pieceAt(sq("e8")))
    }

    @Test
    fun enPassantCapturesTheBypassedPawn() {
        var position = Position.fromFen("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3")
        val enPassant = position.findLegalMove(sq("e5"), sq("f6"))
        assertNotNull(enPassant)
        assertTrue(enPassant!!.isEnPassant)

        position = position.makeMove(enPassant)
        assertEquals(Piece(PieceColor.WHITE, PieceType.PAWN), position.pieceAt(sq("f6")))
        assertNull("the captured pawn must leave f5", position.pieceAt(sq("f5")))
    }

    @Test
    fun doublePawnPushSetsAndThenClearsTheEnPassantTarget() {
        val opening = Position.initial().let { it.makeMove(it.findLegalMove(sq("e2"), sq("e4"))!!) }
        assertEquals(sq("e3"), opening.enPassantTarget)

        val afterKnight = opening.makeMove(opening.findLegalMove(sq("b8"), sq("c6"))!!)
        assertEquals(Square.NONE, afterKnight.enPassantTarget)
    }

    @Test
    fun castlingMovesBothKingAndRook() {
        val position = Position.fromFen("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1")

        val shortCastle = position.findLegalMove(sq("e1"), sq("g1"))!!
        assertTrue(shortCastle.isCastleKingSide)
        val afterShort = position.makeMove(shortCastle)
        assertEquals(Piece(PieceColor.WHITE, PieceType.KING), afterShort.pieceAt(sq("g1")))
        assertEquals(Piece(PieceColor.WHITE, PieceType.ROOK), afterShort.pieceAt(sq("f1")))
        assertNull(afterShort.pieceAt(sq("h1")))
        assertEquals(0, afterShort.castlingRights and Castling.WHITE_KING_SIDE)
        assertEquals(0, afterShort.castlingRights and Castling.WHITE_QUEEN_SIDE)

        val longCastle = position.findLegalMove(sq("e1"), sq("c1"))!!
        assertTrue(longCastle.isCastleQueenSide)
        val afterLong = position.makeMove(longCastle)
        assertEquals(Piece(PieceColor.WHITE, PieceType.KING), afterLong.pieceAt(sq("c1")))
        assertEquals(Piece(PieceColor.WHITE, PieceType.ROOK), afterLong.pieceAt(sq("d1")))
    }

    @Test
    fun castlingIsForbiddenThroughOrOutOfCheck() {
        // Black rook on e8 attacks the king: castling is not allowed while in check.
        val inCheck = Position.fromFen("4r3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertNull(inCheck.findLegalMove(sq("e1"), sq("g1")))
        assertNull(inCheck.findLegalMove(sq("e1"), sq("c1")))

        // Rook on f8 covers f1, the square the king would cross going short.
        val throughCheck = Position.fromFen("5r2/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertNull(throughCheck.findLegalMove(sq("e1"), sq("g1")))
        assertNotNull(throughCheck.findLegalMove(sq("e1"), sq("c1")))

        // b1 may be attacked when castling long — only e1, d1 and c1 need to be safe.
        val attackedKnightSquare = Position.fromFen("1r6/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertNotNull(attackedKnightSquare.findLegalMove(sq("e1"), sq("c1")))
    }

    @Test
    fun losingTheRookLosesTheCastlingRight() {
        val position = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val capture = position.findLegalMove(sq("a1"), sq("a8"))!!
        val after = position.makeMove(capture)
        assertEquals(0, after.castlingRights and Castling.WHITE_QUEEN_SIDE)
        assertEquals(0, after.castlingRights and Castling.BLACK_QUEEN_SIDE)
        assertTrue(after.castlingRights and Castling.WHITE_KING_SIDE != 0)
        assertTrue(after.castlingRights and Castling.BLACK_KING_SIDE != 0)
    }

    @Test
    fun pinnedPieceMayNotAbandonTheKing() {
        // The knight on e2 is pinned along the e-file by the rook on e8.
        val position = Position.fromFen("4r3/8/8/8/8/8/4N3/4K3 w - - 0 1")
        assertTrue(position.legalMovesFrom(sq("e2")).isEmpty())
        assertFalse(position.isInCheck(PieceColor.WHITE))
    }

    @Test
    fun foolsMateIsCheckmate() {
        var game = ChessGame.new()
        listOf("f3", "e5", "g4", "Qh4").forEach { san ->
            game = game.playSan(san) ?: error("$san should be legal")
        }
        assertTrue(game.isCheckmate)
        assertEquals(GameEndReason.CHECKMATE, game.result?.reason)
        assertEquals(GameOutcome.BLACK_WINS, game.result?.outcome)
        assertEquals("Qh4#", game.history.last().san)
    }

    @Test
    fun stalemateIsADraw() {
        val game = ChessGame.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(game.isStalemate)
        assertEquals(GameEndReason.STALEMATE, game.result?.reason)
        assertEquals(GameOutcome.DRAW, game.result?.outcome)
    }

    @Test
    fun insufficientMaterialEndsTheGame() {
        assertTrue(Material.isInsufficient(Position.fromFen("8/8/4k3/8/8/3K4/8/8 w - - 0 1")))
        assertTrue(Material.isInsufficient(Position.fromFen("8/8/4k3/8/8/3K1B2/8/8 w - - 0 1")))
        assertTrue(Material.isInsufficient(Position.fromFen("8/8/4k1n1/8/8/3K4/8/8 w - - 0 1")))
        // Bishops on same coloured squares (c6 and f3 are both light) cannot mate.
        assertTrue(Material.isInsufficient(Position.fromFen("8/8/2b1k3/8/8/3K1B2/8/8 w - - 0 1")))
        // Opposite coloured bishops can (with help), so the game continues.
        assertFalse(Material.isInsufficient(Position.fromFen("8/8/4kb2/8/8/3K1B2/8/8 w - - 0 1")))
        assertFalse(Material.isInsufficient(Position.fromFen("8/8/4k3/8/8/3K1R2/8/8 w - - 0 1")))
        assertFalse(Material.isInsufficient(Position.initial()))

        assertEquals(
            GameEndReason.INSUFFICIENT_MATERIAL,
            ChessGame.fromFen("8/8/4k3/8/8/3K1N2/8/8 w - - 0 1").result?.reason
        )
    }

    @Test
    fun fiftyMoveRuleDrawsAtOneHundredHalfMoves() {
        assertNull(ChessGame.fromFen("8/8/4k3/8/8/3K1R2/8/8 w - - 99 60").result)
        assertEquals(
            GameEndReason.FIFTY_MOVE_RULE,
            ChessGame.fromFen("8/8/4k3/8/8/3K1R2/8/8 w - - 100 60").result?.reason
        )
    }

    @Test
    fun threefoldRepetitionDrawsTheGame() {
        var game = ChessGame.fromFen("4k3/8/8/8/8/8/R7/4K2R w - - 0 1")
        // Shuffle both rooks back and forth until the position appears a third time.
        listOf(
            "Ra3", "Ke7", "Ra2", "Ke8",
            "Ra3", "Ke7", "Ra2", "Ke8"
        ).forEach { san ->
            game = game.playSan(san) ?: error("$san should be legal")
        }
        assertEquals(3, game.repetitionCount())
        assertEquals(GameEndReason.THREEFOLD_REPETITION, game.result?.reason)
    }

    @Test
    fun undoRestoresThePreviousPosition() {
        val start = ChessGame.new()
        val afterE4 = start.playSan("e4")!!
        val afterE5 = afterE4.playSan("e5")!!
        assertEquals(2, afterE5.moveCount)

        val back = afterE5.undo()!!
        assertEquals(1, back.moveCount)
        assertEquals(afterE4.position.toFen(), back.position.toFen())
        assertEquals(start.position.toFen(), back.undo()!!.position.toFen())
        assertNull(start.undo())
    }

    @Test
    fun undoTakesBackADeclaredResultBeforeAnyMoves() {
        val game = ChessGame.new().playSan("e4")!!.playSan("e5")!!
        val resigned = game.resign(PieceColor.WHITE)
        assertNotNull(resigned.result)

        val backInPlay = resigned.undo()!!
        assertNull("the resignation is withdrawn first", backInPlay.result)
        assertEquals("no move is lost doing so", 2, backInPlay.moveCount)
        assertEquals(1, backInPlay.undo()!!.moveCount)
    }

    @Test
    fun resignationAndTimeoutProduceResults() {
        val game = ChessGame.new().playSan("e4")!!
        assertEquals(GameOutcome.BLACK_WINS, game.resign(PieceColor.WHITE).result?.outcome)
        assertEquals(GameEndReason.RESIGNATION, game.resign(PieceColor.WHITE).result?.reason)
        assertEquals(GameOutcome.DRAW, game.agreeDraw().result?.outcome)

        val flagged = game.flag(PieceColor.BLACK)
        assertEquals(GameOutcome.WHITE_WINS, flagged.result?.outcome)
        assertEquals(GameEndReason.TIMEOUT, flagged.result?.reason)

        // A lone king cannot mate, so a flag fall against it is only a draw.
        val bareKing = ChessGame.fromFen("4k3/8/8/8/8/8/8/4K1N1 w - - 0 1").flag(PieceColor.WHITE)
        assertEquals(GameOutcome.DRAW, bareKing.result?.outcome)
        assertEquals(GameEndReason.TIMEOUT_VS_INSUFFICIENT_MATERIAL, bareKing.result?.reason)
    }

    @Test
    fun illegalMovesAreRejected() {
        val game = ChessGame.new()
        assertNull(game.play(sq("e2"), sq("e5")))
        assertNull(game.playSan("Ke2"))
        assertNotNull(game.play(sq("e2"), sq("e4")))
    }

    @Test
    fun capturedPiecesAndMaterialBalanceAreTracked() {
        var game = ChessGame.new()
        listOf("e4", "d5", "exd5", "Qxd5", "Nc3", "Qxa2").forEach {
            game = game.playSan(it) ?: error("$it should be legal")
        }
        assertEquals(listOf(PieceType.PAWN), game.capturedFrom(PieceColor.BLACK))
        assertEquals(listOf(PieceType.PAWN, PieceType.PAWN), game.capturedFrom(PieceColor.WHITE))
        assertEquals(-1, game.materialBalance())
    }

    @Test
    fun moveListIsGroupedIntoNumberedPairs() {
        var game = ChessGame.new()
        listOf("e4", "e5", "Nf3").forEach { game = game.playSan(it)!! }
        assertEquals(
            listOf(Triple(1, "e4", "e5"), Triple(2, "Nf3", null)),
            game.movePairs()
        )
    }
}
