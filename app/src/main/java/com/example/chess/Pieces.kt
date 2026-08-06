package com.example.chess

/** The two sides of a chess game. */
enum class PieceColor {
    WHITE,
    BLACK;

    val opposite: PieceColor
        get() = if (this == WHITE) BLACK else WHITE
}

/**
 * The six piece types. [san] is the uppercase letter used in algebraic notation,
 * [materialValue] is the conventional point count used for the material balance readout.
 */
enum class PieceType(val san: Char, val materialValue: Int) {
    PAWN('P', 1),
    KNIGHT('N', 3),
    BISHOP('B', 3),
    ROOK('R', 5),
    QUEEN('Q', 9),
    KING('K', 0);

    companion object {
        fun fromChar(c: Char): PieceType? {
            val upper = c.uppercaseChar()
            return entries.firstOrNull { it.san == upper }
        }
    }
}

data class Piece(val color: PieceColor, val type: PieceType) {

    val fenChar: Char
        get() = if (color == PieceColor.WHITE) type.san else type.san.lowercaseChar()

    companion object {
        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromChar(c) ?: return null
            return Piece(if (c.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK, type)
        }
    }
}

/**
 * Squares are plain ints in `0..63`, laid out as `rank * 8 + file` with a1 == 0 and h8 == 63.
 * Rank 0 is White's home rank.
 */
object Square {
    const val NONE = -1

    fun of(file: Int, rank: Int): Int = rank * 8 + file

    fun file(square: Int): Int = square and 7

    fun rank(square: Int): Int = square shr 3

    fun isValid(square: Int): Boolean = square in 0..63

    fun isValid(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7

    fun name(square: Int): String =
        if (!isValid(square)) "-" else "${'a' + file(square)}${rank(square) + 1}"

    fun fromName(name: String): Int {
        if (name.length != 2) return NONE
        val file = name[0].lowercaseChar() - 'a'
        val rank = name[1] - '1'
        return if (isValid(file, rank)) of(file, rank) else NONE
    }

    /** a1 is a dark square, so light squares are the ones where file + rank is odd. */
    fun isLight(square: Int): Boolean = (file(square) + rank(square)) % 2 == 1
}
