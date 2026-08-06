package com.example.chess

/**
 * A fully described move. Everything the board, the notation writer and the undo stack need is
 * carried on the move itself so nothing has to be re-derived from a position later on.
 */
data class Move(
    val from: Int,
    val to: Int,
    val piece: Piece,
    val captured: Piece? = null,
    val promotion: PieceType? = null,
    val isEnPassant: Boolean = false,
    val isCastleKingSide: Boolean = false,
    val isCastleQueenSide: Boolean = false,
    val isDoublePawnPush: Boolean = false
) {
    val isCapture: Boolean get() = captured != null

    val isCastle: Boolean get() = isCastleKingSide || isCastleQueenSide

    /** Long algebraic / UCI form, e.g. `e2e4` or `e7e8q`. */
    val uci: String
        get() = Square.name(from) + Square.name(to) + (promotion?.san?.lowercaseChar() ?: "")

    override fun toString(): String = uci
}

/** Castling right bit flags stored on a [Position]. */
object Castling {
    const val NONE = 0
    const val WHITE_KING_SIDE = 1
    const val WHITE_QUEEN_SIDE = 2
    const val BLACK_KING_SIDE = 4
    const val BLACK_QUEEN_SIDE = 8
    const val ALL = WHITE_KING_SIDE or WHITE_QUEEN_SIDE or BLACK_KING_SIDE or BLACK_QUEEN_SIDE

    fun kingSide(color: PieceColor): Int =
        if (color == PieceColor.WHITE) WHITE_KING_SIDE else BLACK_KING_SIDE

    fun queenSide(color: PieceColor): Int =
        if (color == PieceColor.WHITE) WHITE_QUEEN_SIDE else BLACK_QUEEN_SIDE

    fun toFen(rights: Int): String {
        if (rights == NONE) return "-"
        return buildString {
            if (rights and WHITE_KING_SIDE != 0) append('K')
            if (rights and WHITE_QUEEN_SIDE != 0) append('Q')
            if (rights and BLACK_KING_SIDE != 0) append('k')
            if (rights and BLACK_QUEEN_SIDE != 0) append('q')
        }
    }

    fun fromFen(text: String): Int {
        if (text == "-") return NONE
        var rights = NONE
        text.forEach { c ->
            when (c) {
                'K' -> rights = rights or WHITE_KING_SIDE
                'Q' -> rights = rights or WHITE_QUEEN_SIDE
                'k' -> rights = rights or BLACK_KING_SIDE
                'q' -> rights = rights or BLACK_QUEEN_SIDE
            }
        }
        return rights
    }
}
