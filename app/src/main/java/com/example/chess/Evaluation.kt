package com.example.chess

/**
 * How a position is scored, in centipawns, always from White's point of view: positive is good for
 * White. Material is the bulk of it; the piece-square tables are what stop the engine from shuffling
 * pieces about, by giving it a reason to prefer a knight on d4 to a knight on a1.
 */
object Evaluation {

    /** Beyond any real evaluation, so a forced mate always outranks winning material. */
    const val MATE_SCORE = 30_000

    /** Centipawn values used by the search — the display values in [PieceType] are too coarse. */
    fun valueOf(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 320
        PieceType.BISHOP -> 330
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        PieceType.KING -> 0
    }

    /**
     * The score of [position] from White's point of view. This runs at every leaf of the search, so
     * it walks the board once and derives everything else from that single pass.
     */
    fun evaluate(position: Position): Int {
        val pieces = position.occupiedSquares()

        var material = 0
        var heavyMaterial = 0
        var whiteBishops = 0
        var blackBishops = 0
        var whiteKing = Square.NONE
        var blackKing = Square.NONE
        val whitePawnFiles = IntArray(8)
        val blackPawnFiles = IntArray(8)

        for ((square, piece) in pieces) {
            val white = piece.color == PieceColor.WHITE
            val value = valueOf(piece.type)
            material += if (white) value else -value

            when (piece.type) {
                PieceType.KING -> if (white) whiteKing = square else blackKing = square
                PieceType.BISHOP -> {
                    if (white) whiteBishops++ else blackBishops++
                    heavyMaterial += value
                }
                PieceType.PAWN -> {
                    val file = Square.file(square)
                    if (white) whitePawnFiles[file]++ else blackPawnFiles[file]++
                }
                else -> heavyMaterial += value
            }
        }

        // 0 while the pieces are still on, 100 once they are gone: the king's table is interpolated
        // between hiding behind its pawns and marching into the middle.
        val endgameWeight = ((OPENING_MATERIAL - heavyMaterial).coerceIn(0, OPENING_MATERIAL) * 100) /
            OPENING_MATERIAL

        var score = material
        for ((square, piece) in pieces) {
            val bonus = squareBonus(piece, square, endgameWeight)
            score += if (piece.color == PieceColor.WHITE) bonus else -bonus
        }

        // The bishop pair is worth about a third of a pawn.
        if (whiteBishops >= 2) score += BISHOP_PAIR
        if (blackBishops >= 2) score -= BISHOP_PAIR

        score += pawnStructure(whitePawnFiles, blackPawnFiles)
        score += mopUp(pieces, material, whiteKing, blackKing)

        // Having the move is worth a little, and it keeps the search from thinking a position is
        // exactly equal when it is not.
        score += TEMPO * if (position.sideToMove == PieceColor.WHITE) 1 else -1

        return score
    }

    /** The score from the side to move's point of view, which is what a negamax search wants. */
    fun evaluateForSideToMove(position: Position): Int {
        val white = evaluate(position)
        return if (position.sideToMove == PieceColor.WHITE) white else -white
    }

    private fun squareBonus(piece: Piece, square: Int, endgameWeight: Int): Int {
        // Tables are written from White's side, so Black reads them off the mirrored square.
        val index = if (piece.color == PieceColor.WHITE) square else square xor 56
        return when (piece.type) {
            PieceType.PAWN -> PAWN_TABLE[index]
            PieceType.KNIGHT -> KNIGHT_TABLE[index]
            PieceType.BISHOP -> BISHOP_TABLE[index]
            PieceType.ROOK -> ROOK_TABLE[index]
            PieceType.QUEEN -> QUEEN_TABLE[index]
            PieceType.KING -> {
                val opening = KING_OPENING_TABLE[index]
                val endgame = KING_ENDGAME_TABLE[index]
                (opening * (100 - endgameWeight) + endgame * endgameWeight) / 100
            }
        }
    }

    /** Doubled and isolated pawns, the two structural faults cheap enough to check at every leaf. */
    private fun pawnStructure(whiteFiles: IntArray, blackFiles: IntArray): Int {
        var score = 0
        for (file in 0..7) {
            if (whiteFiles[file] > 1) score -= DOUBLED_PAWN * (whiteFiles[file] - 1)
            if (blackFiles[file] > 1) score += DOUBLED_PAWN * (blackFiles[file] - 1)

            val left = file - 1
            val right = file + 1
            val whiteNeighbour = (left >= 0 && whiteFiles[left] > 0) || (right <= 7 && whiteFiles[right] > 0)
            val blackNeighbour = (left >= 0 && blackFiles[left] > 0) || (right <= 7 && blackFiles[right] > 0)
            if (whiteFiles[file] > 0 && !whiteNeighbour) score -= ISOLATED_PAWN
            if (blackFiles[file] > 0 && !blackNeighbour) score += ISOLATED_PAWN
        }
        return score
    }

    /**
     * Once one side is a rook or more up against a king with no pawns, the tables have nothing left
     * to say and the search will happily shuffle for ever. This is what actually finishes the game:
     * drive the bare king to the edge and walk the winning king up to it.
     */
    private fun mopUp(
        pieces: List<Pair<Int, Piece>>,
        material: Int,
        whiteKing: Int,
        blackKing: Int
    ): Int {
        if (whiteKing == Square.NONE || blackKing == Square.NONE) return 0
        if (material >= MOP_UP_LEAD) {
            if (pieces.any { it.second.color == PieceColor.BLACK && it.second.type == PieceType.PAWN }) return 0
            return mopUpBonus(loser = blackKing, winner = whiteKing)
        }
        if (material <= -MOP_UP_LEAD) {
            if (pieces.any { it.second.color == PieceColor.WHITE && it.second.type == PieceType.PAWN }) return 0
            return -mopUpBonus(loser = whiteKing, winner = blackKing)
        }
        return 0
    }

    private fun mopUpBonus(loser: Int, winner: Int): Int {
        val fileGap = kotlin.math.abs(Square.file(loser) - Square.file(winner))
        val rankGap = kotlin.math.abs(Square.rank(loser) - Square.rank(winner))
        // Corner the loser, and close the gap so the winning king can support the mate.
        return centreDistance(loser) * EDGE_PUSH + (14 - (fileGap + rankGap)) * KING_APPROACH
    }

    /** How far a square is from the middle four, in king-ish steps: 0 in the centre, 6 in a corner. */
    private fun centreDistance(square: Int): Int {
        val file = Square.file(square)
        val rank = Square.rank(square)
        return maxOf(3 - file, file - 4, 0) + maxOf(3 - rank, rank - 4, 0)
    }

    /** Both sides start with 2*(320 + 330 + 500) + 900 of material that is neither king nor pawn. */
    private const val OPENING_MATERIAL = 6_400
    private const val BISHOP_PAIR = 30
    private const val DOUBLED_PAWN = 18
    private const val ISOLATED_PAWN = 14
    private const val TEMPO = 8

    /** A rook's worth ahead is the point at which mating the bare king becomes the plan. */
    private const val MOP_UP_LEAD = 450
    private const val EDGE_PUSH = 16
    private const val KING_APPROACH = 8

    // The tables below are indexed a1..h8 — the same addressing as the board — so the first row is
    // White's home rank. They are the well-known "simplified evaluation" tables, rank-reversed to
    // match that layout.

    private val PAWN_TABLE = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, -20, -20, 10, 10, 5,
        5, -5, -10, 0, 0, -10, -5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, 5, 10, 25, 25, 10, 5, 5,
        10, 10, 20, 30, 30, 20, 10, 10,
        50, 50, 50, 50, 50, 50, 50, 50,
        0, 0, 0, 0, 0, 0, 0, 0
    )

    private val KNIGHT_TABLE = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )

    private val BISHOP_TABLE = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -20, -10, -10, -10, -10, -10, -10, -20
    )

    private val ROOK_TABLE = intArrayOf(
        0, 0, 0, 5, 5, 0, 0, 0,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        5, 10, 10, 10, 10, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0
    )

    private val QUEEN_TABLE = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -10, 5, 5, 5, 5, 5, 0, -10,
        0, 0, 5, 5, 5, 5, 0, -5,
        -5, 0, 5, 5, 5, 5, 0, -5,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20
    )

    private val KING_OPENING_TABLE = intArrayOf(
        20, 30, 10, 0, 0, 10, 30, 20,
        20, 20, 0, 0, 0, 0, 20, 20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30
    )

    private val KING_ENDGAME_TABLE = intArrayOf(
        -50, -30, -30, -30, -30, -30, -30, -50,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -50, -40, -30, -20, -20, -30, -40, -50
    )
}
