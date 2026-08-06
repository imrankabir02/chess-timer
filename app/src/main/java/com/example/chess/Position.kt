package com.example.chess

/**
 * An immutable chess position. [makeMove] returns a brand new position, which keeps the whole
 * game history trivially available for undo, threefold repetition and board review.
 */
class Position(
    private val squares: Array<Piece?>,
    val sideToMove: PieceColor,
    val castlingRights: Int,
    val enPassantTarget: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int
) {

    init {
        require(squares.size == 64) { "A chess board has 64 squares, got ${squares.size}" }
    }

    fun pieceAt(square: Int): Piece? =
        if (Square.isValid(square)) squares[square] else null

    fun pieceAt(file: Int, rank: Int): Piece? =
        if (Square.isValid(file, rank)) squares[Square.of(file, rank)] else null

    /** Every occupied square with its piece, in a1..h8 order. */
    fun occupiedSquares(): List<Pair<Int, Piece>> =
        (0..63).mapNotNull { sq -> squares[sq]?.let { sq to it } }

    fun piecesOf(color: PieceColor): List<Piece> =
        squares.filterNotNull().filter { it.color == color }

    fun kingSquare(color: PieceColor): Int {
        for (sq in 0..63) {
            val piece = squares[sq]
            if (piece != null && piece.color == color && piece.type == PieceType.KING) return sq
        }
        return Square.NONE
    }

    fun isInCheck(color: PieceColor): Boolean {
        val king = kingSquare(color)
        return king != Square.NONE && isSquareAttacked(king, color.opposite)
    }

    /** True when [square] is attacked by any piece of [attacker]. */
    fun isSquareAttacked(square: Int, attacker: PieceColor): Boolean {
        val file = Square.file(square)
        val rank = Square.rank(square)

        // Pawns: a white pawn on the rank below attacks upwards, a black pawn from above.
        val pawnRank = if (attacker == PieceColor.WHITE) rank - 1 else rank + 1
        for (df in intArrayOf(-1, 1)) {
            val piece = pieceAt(file + df, pawnRank)
            if (piece != null && piece.color == attacker && piece.type == PieceType.PAWN) return true
        }

        for ((df, dr) in KNIGHT_OFFSETS) {
            val piece = pieceAt(file + df, rank + dr)
            if (piece != null && piece.color == attacker && piece.type == PieceType.KNIGHT) return true
        }

        for ((df, dr) in KING_OFFSETS) {
            val piece = pieceAt(file + df, rank + dr)
            if (piece != null && piece.color == attacker && piece.type == PieceType.KING) return true
        }

        if (isAttackedBySlider(file, rank, attacker, BISHOP_DIRECTIONS, PieceType.BISHOP)) return true
        if (isAttackedBySlider(file, rank, attacker, ROOK_DIRECTIONS, PieceType.ROOK)) return true

        return false
    }

    private fun isAttackedBySlider(
        file: Int,
        rank: Int,
        attacker: PieceColor,
        directions: Array<IntArray>,
        slider: PieceType
    ): Boolean {
        for ((df, dr) in directions) {
            var f = file + df
            var r = rank + dr
            while (Square.isValid(f, r)) {
                val piece = squares[Square.of(f, r)]
                if (piece != null) {
                    if (piece.color == attacker &&
                        (piece.type == slider || piece.type == PieceType.QUEEN)
                    ) {
                        return true
                    }
                    break
                }
                f += df
                r += dr
            }
        }
        return false
    }

    /** All moves that are legal for the side to move. */
    fun legalMoves(): List<Move> = pseudoLegalMoves().filter { isLegal(it) }

    /** Legal moves that start on [square]; empty when the square holds no friendly piece. */
    fun legalMovesFrom(square: Int): List<Move> = legalMoves().filter { it.from == square }

    fun isLegal(move: Move): Boolean {
        val next = makeMove(move)
        return !next.isSquareAttacked(next.kingSquare(sideToMove), sideToMove.opposite)
    }

    fun pseudoLegalMoves(): List<Move> {
        val moves = ArrayList<Move>(48)
        for (square in 0..63) {
            val piece = squares[square] ?: continue
            if (piece.color != sideToMove) continue
            when (piece.type) {
                PieceType.PAWN -> generatePawnMoves(square, piece, moves)
                PieceType.KNIGHT -> generateStepMoves(square, piece, KNIGHT_OFFSETS, moves)
                PieceType.KING -> {
                    generateStepMoves(square, piece, KING_OFFSETS, moves)
                    generateCastlingMoves(square, piece, moves)
                }
                PieceType.BISHOP -> generateSlidingMoves(square, piece, BISHOP_DIRECTIONS, moves)
                PieceType.ROOK -> generateSlidingMoves(square, piece, ROOK_DIRECTIONS, moves)
                PieceType.QUEEN -> generateSlidingMoves(square, piece, QUEEN_DIRECTIONS, moves)
            }
        }
        return moves
    }

    private fun generatePawnMoves(from: Int, piece: Piece, moves: MutableList<Move>) {
        val file = Square.file(from)
        val rank = Square.rank(from)
        val forward = if (piece.color == PieceColor.WHITE) 1 else -1
        val startRank = if (piece.color == PieceColor.WHITE) 1 else 6
        val promotionRank = if (piece.color == PieceColor.WHITE) 7 else 0

        val oneAhead = rank + forward
        if (Square.isValid(file, oneAhead) && pieceAt(file, oneAhead) == null) {
            val to = Square.of(file, oneAhead)
            addPawnMove(from, to, piece, null, oneAhead == promotionRank, moves)

            val twoAhead = rank + 2 * forward
            if (rank == startRank && pieceAt(file, twoAhead) == null) {
                moves.add(
                    Move(
                        from = from,
                        to = Square.of(file, twoAhead),
                        piece = piece,
                        isDoublePawnPush = true
                    )
                )
            }
        }

        for (df in intArrayOf(-1, 1)) {
            val targetFile = file + df
            if (!Square.isValid(targetFile, oneAhead)) continue
            val to = Square.of(targetFile, oneAhead)
            val target = squares[to]
            if (target != null && target.color != piece.color) {
                addPawnMove(from, to, piece, target, oneAhead == promotionRank, moves)
            } else if (target == null && to == enPassantTarget) {
                val capturedSquare = Square.of(targetFile, rank)
                val capturedPawn = squares[capturedSquare]
                if (capturedPawn != null && capturedPawn.color != piece.color) {
                    moves.add(
                        Move(
                            from = from,
                            to = to,
                            piece = piece,
                            captured = capturedPawn,
                            isEnPassant = true
                        )
                    )
                }
            }
        }
    }

    private fun addPawnMove(
        from: Int,
        to: Int,
        piece: Piece,
        captured: Piece?,
        isPromotion: Boolean,
        moves: MutableList<Move>
    ) {
        if (isPromotion) {
            for (type in PROMOTION_TYPES) {
                moves.add(Move(from = from, to = to, piece = piece, captured = captured, promotion = type))
            }
        } else {
            moves.add(Move(from = from, to = to, piece = piece, captured = captured))
        }
    }

    private fun generateStepMoves(
        from: Int,
        piece: Piece,
        offsets: Array<IntArray>,
        moves: MutableList<Move>
    ) {
        val file = Square.file(from)
        val rank = Square.rank(from)
        for ((df, dr) in offsets) {
            val f = file + df
            val r = rank + dr
            if (!Square.isValid(f, r)) continue
            val to = Square.of(f, r)
            val target = squares[to]
            if (target == null || target.color != piece.color) {
                moves.add(Move(from = from, to = to, piece = piece, captured = target))
            }
        }
    }

    private fun generateSlidingMoves(
        from: Int,
        piece: Piece,
        directions: Array<IntArray>,
        moves: MutableList<Move>
    ) {
        val file = Square.file(from)
        val rank = Square.rank(from)
        for ((df, dr) in directions) {
            var f = file + df
            var r = rank + dr
            while (Square.isValid(f, r)) {
                val to = Square.of(f, r)
                val target = squares[to]
                if (target == null) {
                    moves.add(Move(from = from, to = to, piece = piece))
                } else {
                    if (target.color != piece.color) {
                        moves.add(Move(from = from, to = to, piece = piece, captured = target))
                    }
                    break
                }
                f += df
                r += dr
            }
        }
    }

    private fun generateCastlingMoves(from: Int, piece: Piece, moves: MutableList<Move>) {
        val color = piece.color
        val homeRank = if (color == PieceColor.WHITE) 0 else 7
        if (from != Square.of(4, homeRank)) return
        if (isSquareAttacked(from, color.opposite)) return

        if (castlingRights and Castling.kingSide(color) != 0) {
            val f5 = Square.of(5, homeRank)
            val g6 = Square.of(6, homeRank)
            val rookSquare = Square.of(7, homeRank)
            val rook = squares[rookSquare]
            if (squares[f5] == null && squares[g6] == null &&
                rook != null && rook.color == color && rook.type == PieceType.ROOK &&
                !isSquareAttacked(f5, color.opposite) && !isSquareAttacked(g6, color.opposite)
            ) {
                moves.add(Move(from = from, to = g6, piece = piece, isCastleKingSide = true))
            }
        }

        if (castlingRights and Castling.queenSide(color) != 0) {
            val d3 = Square.of(3, homeRank)
            val c2 = Square.of(2, homeRank)
            val b1 = Square.of(1, homeRank)
            val rookSquare = Square.of(0, homeRank)
            val rook = squares[rookSquare]
            if (squares[d3] == null && squares[c2] == null && squares[b1] == null &&
                rook != null && rook.color == color && rook.type == PieceType.ROOK &&
                !isSquareAttacked(d3, color.opposite) && !isSquareAttacked(c2, color.opposite)
            ) {
                moves.add(Move(from = from, to = c2, piece = piece, isCastleQueenSide = true))
            }
        }
    }

    /** Applies [move] and returns the resulting position. The receiver is left untouched. */
    fun makeMove(move: Move): Position {
        val next = squares.copyOf()
        val mover = move.piece
        val color = mover.color

        next[move.from] = null
        next[move.to] = if (move.promotion != null) Piece(color, move.promotion) else mover

        if (move.isEnPassant) {
            next[Square.of(Square.file(move.to), Square.rank(move.from))] = null
        }

        val homeRank = if (color == PieceColor.WHITE) 0 else 7
        if (move.isCastleKingSide) {
            next[Square.of(7, homeRank)] = null
            next[Square.of(5, homeRank)] = Piece(color, PieceType.ROOK)
        } else if (move.isCastleQueenSide) {
            next[Square.of(0, homeRank)] = null
            next[Square.of(3, homeRank)] = Piece(color, PieceType.ROOK)
        }

        var rights = castlingRights
        if (mover.type == PieceType.KING) {
            rights = rights and (Castling.kingSide(color) or Castling.queenSide(color)).inv()
        }
        // A rook leaving — or being captured on — its home square kills that castling right.
        rights = rights and rookRightsMask(move.from)
        rights = rights and rookRightsMask(move.to)

        val epTarget = if (move.isDoublePawnPush) {
            Square.of(Square.file(move.from), (Square.rank(move.from) + Square.rank(move.to)) / 2)
        } else {
            Square.NONE
        }

        val resetsClock = mover.type == PieceType.PAWN || move.isCapture

        return Position(
            squares = next,
            sideToMove = color.opposite,
            castlingRights = rights,
            enPassantTarget = epTarget,
            halfmoveClock = if (resetsClock) 0 else halfmoveClock + 1,
            fullmoveNumber = if (color == PieceColor.BLACK) fullmoveNumber + 1 else fullmoveNumber
        )
    }

    private fun rookRightsMask(square: Int): Int = when (square) {
        Square.of(0, 0) -> Castling.WHITE_QUEEN_SIDE.inv()
        Square.of(7, 0) -> Castling.WHITE_KING_SIDE.inv()
        Square.of(0, 7) -> Castling.BLACK_QUEEN_SIDE.inv()
        Square.of(7, 7) -> Castling.BLACK_KING_SIDE.inv()
        else -> -1
    }

    /** Finds the legal move matching a from/to pair, optionally with a chosen promotion piece. */
    fun findLegalMove(from: Int, to: Int, promotion: PieceType? = null): Move? {
        val candidates = legalMoves().filter { it.from == from && it.to == to }
        if (candidates.isEmpty()) return null
        if (promotion != null) return candidates.firstOrNull { it.promotion == promotion }
        return candidates.firstOrNull { it.promotion == null } ?: candidates.firstOrNull()
    }

    fun toFen(): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val piece = squares[Square.of(file, rank)]
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        append(empty)
                        empty = 0
                    }
                    append(piece.fenChar)
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
        append(' ')
        append(if (sideToMove == PieceColor.WHITE) 'w' else 'b')
        append(' ')
        append(Castling.toFen(castlingRights))
        append(' ')
        append(if (enPassantTarget == Square.NONE) "-" else Square.name(enPassantTarget))
        append(' ')
        append(halfmoveClock)
        append(' ')
        append(fullmoveNumber)
    }

    /**
     * The position identity used for threefold repetition: placement, side to move, castling
     * rights and en passant target, but not the move counters.
     */
    fun repetitionKey(): String = toFen().split(' ').take(4).joinToString(" ")

    override fun toString(): String = toFen()

    companion object {
        val KNIGHT_OFFSETS = arrayOf(
            intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2),
            intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2)
        )
        val KING_OFFSETS = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, 0), intArrayOf(1, -1),
            intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1)
        )
        val BISHOP_DIRECTIONS = arrayOf(
            intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, -1), intArrayOf(-1, 1)
        )
        val ROOK_DIRECTIONS = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(-1, 0)
        )
        val QUEEN_DIRECTIONS = BISHOP_DIRECTIONS + ROOK_DIRECTIONS

        val PROMOTION_TYPES = listOf(
            PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT
        )

        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun initial(): Position = fromFen(START_FEN)

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 4) { "Malformed FEN: $fen" }

            val squares = arrayOfNulls<Piece>(64)
            var rank = 7
            var file = 0
            for (c in parts[0]) {
                when {
                    c == '/' -> {
                        rank--
                        file = 0
                    }
                    c.isDigit() -> file += c - '0'
                    else -> {
                        val piece = Piece.fromFenChar(c)
                            ?: throw IllegalArgumentException("Unknown piece '$c' in FEN: $fen")
                        require(Square.isValid(file, rank)) { "Malformed FEN board: $fen" }
                        squares[Square.of(file, rank)] = piece
                        file++
                    }
                }
            }

            return Position(
                squares = squares,
                sideToMove = if (parts[1] == "b") PieceColor.BLACK else PieceColor.WHITE,
                castlingRights = Castling.fromFen(parts[2]),
                enPassantTarget = if (parts[3] == "-") Square.NONE else Square.fromName(parts[3]),
                halfmoveClock = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                fullmoveNumber = parts.getOrNull(5)?.toIntOrNull() ?: 1
            )
        }
    }
}
