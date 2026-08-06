package com.example.chess

enum class GameOutcome { WHITE_WINS, BLACK_WINS, DRAW }

enum class GameEndReason {
    CHECKMATE,
    STALEMATE,
    FIFTY_MOVE_RULE,
    THREEFOLD_REPETITION,
    INSUFFICIENT_MATERIAL,
    TIMEOUT,
    TIMEOUT_VS_INSUFFICIENT_MATERIAL,
    RESIGNATION,
    DRAW_AGREED
}

data class GameResult(val outcome: GameOutcome, val reason: GameEndReason) {

    val winner: PieceColor?
        get() = when (outcome) {
            GameOutcome.WHITE_WINS -> PieceColor.WHITE
            GameOutcome.BLACK_WINS -> PieceColor.BLACK
            GameOutcome.DRAW -> null
        }

    /** The `1-0` / `0-1` / `½-½` score line. */
    val scoreLine: String
        get() = when (outcome) {
            GameOutcome.WHITE_WINS -> "1-0"
            GameOutcome.BLACK_WINS -> "0-1"
            GameOutcome.DRAW -> "½-½"
        }

    companion object {
        fun winFor(color: PieceColor, reason: GameEndReason) = GameResult(
            outcome = if (color == PieceColor.WHITE) GameOutcome.WHITE_WINS else GameOutcome.BLACK_WINS,
            reason = reason
        )

        fun draw(reason: GameEndReason) = GameResult(GameOutcome.DRAW, reason)
    }
}

/** One played half-move, with everything needed to render it and to step back from it. */
data class Ply(
    val move: Move,
    val san: String,
    val positionBefore: Position,
    val positionAfter: Position
)

/**
 * A complete game: the move list, the current position, and the rules that decide when it is over.
 * Every mutating operation returns a new [ChessGame], so previous states stay valid.
 */
class ChessGame private constructor(
    val initialPosition: Position,
    val history: List<Ply>,
    /** Set only for results the rules cannot infer from the board (resignation, timeout, agreement). */
    private val adjudicatedResult: GameResult?
) {

    val position: Position
        get() = history.lastOrNull()?.positionAfter ?: initialPosition

    val sideToMove: PieceColor
        get() = position.sideToMove

    val moveCount: Int
        get() = history.size

    val lastMove: Move?
        get() = history.lastOrNull()?.move

    val isCheck: Boolean
        get() = position.isInCheck(position.sideToMove)

    val legalMoves: List<Move> by lazy { position.legalMoves() }

    val isCheckmate: Boolean
        get() = legalMoves.isEmpty() && isCheck

    val isStalemate: Boolean
        get() = legalMoves.isEmpty() && !isCheck

    /** The result of the game, or null while it is still in progress. */
    val result: GameResult? by lazy { adjudicatedResult ?: naturalResult() }

    val isOver: Boolean
        get() = result != null

    private fun naturalResult(): GameResult? = when {
        isCheckmate -> GameResult.winFor(position.sideToMove.opposite, GameEndReason.CHECKMATE)
        isStalemate -> GameResult.draw(GameEndReason.STALEMATE)
        Material.isInsufficient(position) -> GameResult.draw(GameEndReason.INSUFFICIENT_MATERIAL)
        position.halfmoveClock >= 100 -> GameResult.draw(GameEndReason.FIFTY_MOVE_RULE)
        repetitionCount() >= 3 -> GameResult.draw(GameEndReason.THREEFOLD_REPETITION)
        else -> null
    }

    /** How many times the current position has occurred in this game, including now. */
    fun repetitionCount(): Int {
        val key = position.repetitionKey()
        var count = if (initialPosition.repetitionKey() == key) 1 else 0
        for (ply in history) {
            if (ply.positionAfter.repetitionKey() == key) count++
        }
        return count
    }

    /** Plays [move] if it is legal for the current position. Returns null otherwise. */
    fun play(move: Move): ChessGame? {
        if (isOver) return null
        val legal = legalMoves.firstOrNull {
            it.from == move.from && it.to == move.to && it.promotion == move.promotion
        } ?: return null
        val before = position
        val ply = Ply(
            move = legal,
            san = San.toSan(before, legal),
            positionBefore = before,
            positionAfter = before.makeMove(legal)
        )
        return ChessGame(initialPosition, history + ply, null)
    }

    fun play(from: Int, to: Int, promotion: PieceType? = null): ChessGame? {
        val move = position.findLegalMove(from, to, promotion) ?: return null
        return play(move)
    }

    fun playSan(san: String): ChessGame? = San.parse(position, san)?.let { play(it) }

    /**
     * Steps back. A result that was declared rather than played — a resignation, a flag fall, an
     * agreed draw — is taken back first; only then do further calls remove half-moves.
     */
    fun undo(): ChessGame? {
        if (adjudicatedResult != null) return ChessGame(initialPosition, history, null)
        if (history.isEmpty()) return null
        return ChessGame(initialPosition, history.dropLast(1), null)
    }

    fun withResult(result: GameResult): ChessGame = ChessGame(initialPosition, history, result)

    fun resign(color: PieceColor): ChessGame =
        withResult(GameResult.winFor(color.opposite, GameEndReason.RESIGNATION))

    fun agreeDraw(): ChessGame = withResult(GameResult.draw(GameEndReason.DRAW_AGREED))

    /**
     * Flags [color] on time. FIDE 6.9: if the opponent cannot possibly deliver mate, the game is
     * drawn instead of lost.
     */
    fun flag(color: PieceColor): ChessGame {
        val opponent = color.opposite
        return if (Material.canPossiblyMate(position, opponent)) {
            withResult(GameResult.winFor(opponent, GameEndReason.TIMEOUT))
        } else {
            withResult(GameResult.draw(GameEndReason.TIMEOUT_VS_INSUFFICIENT_MATERIAL))
        }
    }

    /** Move list grouped into `1. e4 e5` style pairs. */
    fun movePairs(): List<Triple<Int, String, String?>> {
        val pairs = mutableListOf<Triple<Int, String, String?>>()
        var index = 0
        var number = initialPosition.fullmoveNumber
        if (initialPosition.sideToMove == PieceColor.BLACK && history.isNotEmpty()) {
            pairs.add(Triple(number, "…", history[0].san))
            index = 1
            number++
        }
        while (index < history.size) {
            val white = history[index].san
            val black = history.getOrNull(index + 1)?.san
            pairs.add(Triple(number, white, black))
            index += 2
            number++
        }
        return pairs
    }

    /** Pieces of [color] that have been captured by the opponent, best first. */
    fun capturedFrom(color: PieceColor): List<PieceType> =
        history.mapNotNull { it.move.captured }
            .filter { it.color == color }
            .map { it.type }
            .sortedByDescending { it.materialValue }

    /** Positive when White is ahead on material. */
    fun materialBalance(): Int {
        var balance = 0
        for ((_, piece) in position.occupiedSquares()) {
            val value = piece.type.materialValue
            balance += if (piece.color == PieceColor.WHITE) value else -value
        }
        return balance
    }

    companion object {
        fun new(): ChessGame = fromPosition(Position.initial())

        fun fromPosition(position: Position): ChessGame = ChessGame(position, emptyList(), null)

        fun fromFen(fen: String): ChessGame = fromPosition(Position.fromFen(fen))
    }
}

/** Material based draw rules. */
object Material {

    /** True for the dead positions of FIDE 5.2.2: K v K, K+B v K, K+N v K, K+B v K+B same colour. */
    fun isInsufficient(position: Position): Boolean {
        val pieces = position.occupiedSquares().filter { it.second.type != PieceType.KING }
        if (pieces.isEmpty()) return true
        if (pieces.any { it.second.type == PieceType.PAWN ||
                it.second.type == PieceType.ROOK ||
                it.second.type == PieceType.QUEEN }
        ) {
            return false
        }
        if (pieces.size == 1) return true // lone bishop or knight
        if (pieces.size == 2) {
            val (first, second) = pieces
            val bothBishops = first.second.type == PieceType.BISHOP && second.second.type == PieceType.BISHOP
            val opposingSides = first.second.color != second.second.color
            val sameSquareColor = Square.isLight(first.first) == Square.isLight(second.first)
            if (bothBishops && opposingSides && sameSquareColor) return true
        }
        return false
    }

    /**
     * Whether [color] could still mate by some legal sequence — the test used when the opponent
     * runs out of time. A bare king, or a king with a single minor piece, never can.
     */
    fun canPossiblyMate(position: Position, color: PieceColor): Boolean {
        val pieces = position.piecesOf(color).filter { it.type != PieceType.KING }
        if (pieces.isEmpty()) return false
        if (pieces.size == 1 && (pieces[0].type == PieceType.BISHOP || pieces[0].type == PieceType.KNIGHT)) {
            return false
        }
        return true
    }
}
