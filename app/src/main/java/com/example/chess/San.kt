package com.example.chess

/**
 * Standard Algebraic Notation. [toSan] must be called on the position *before* the move is played,
 * because disambiguation depends on the other legal moves available at that moment.
 */
object San {

    fun toSan(position: Position, move: Move): String {
        val after = position.makeMove(move)
        val suffix = when {
            after.legalMoves().isEmpty() && after.isInCheck(after.sideToMove) -> "#"
            after.isInCheck(after.sideToMove) -> "+"
            else -> ""
        }

        if (move.isCastleKingSide) return "O-O$suffix"
        if (move.isCastleQueenSide) return "O-O-O$suffix"

        val target = Square.name(move.to)

        if (move.piece.type == PieceType.PAWN) {
            val body = if (move.isCapture) {
                "${'a' + Square.file(move.from)}x$target"
            } else {
                target
            }
            val promotion = move.promotion?.let { "=${it.san}" } ?: ""
            return "$body$promotion$suffix"
        }

        val disambiguation = disambiguate(position, move)
        val capture = if (move.isCapture) "x" else ""
        return "${move.piece.type.san}$disambiguation$capture$target$suffix"
    }

    private fun disambiguate(position: Position, move: Move): String {
        val rivals = position.legalMoves().filter {
            it.to == move.to &&
                it.from != move.from &&
                it.piece.type == move.piece.type &&
                it.piece.color == move.piece.color
        }
        if (rivals.isEmpty()) return ""

        val fromFile = Square.file(move.from)
        val fromRank = Square.rank(move.from)
        val fileIsUnique = rivals.none { Square.file(it.from) == fromFile }
        if (fileIsUnique) return "${'a' + fromFile}"
        val rankIsUnique = rivals.none { Square.rank(it.from) == fromRank }
        if (rankIsUnique) return "${fromRank + 1}"
        return Square.name(move.from)
    }

    /** Parses a SAN string against [position]; returns null when it is not a legal move. */
    fun parse(position: Position, san: String): Move? {
        val cleaned = san.trim().removeSuffix("!").removeSuffix("?").trimEnd('+', '#')
        if (cleaned.isEmpty()) return null

        val legal = position.legalMoves()
        if (cleaned == "O-O" || cleaned == "0-0") return legal.firstOrNull { it.isCastleKingSide }
        if (cleaned == "O-O-O" || cleaned == "0-0-0") return legal.firstOrNull { it.isCastleQueenSide }

        return legal.firstOrNull { toSan(position, it).trimEnd('+', '#') == cleaned }
    }
}
