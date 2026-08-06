package com.example.chess

import kotlin.math.abs
import kotlin.random.Random

/**
 * How strongly the computer plays. The three levels share one search — they differ in how deep it
 * looks, whether it plays exchanges out to the end, and how much noise is allowed into the choice.
 *
 * The budgets are what keeps a level honest on a phone: the search stops at whichever of the depth,
 * the node count or the clock runs out first, and plays the best move from the last full iteration.
 */
enum class ChessBotLevel(
    val label: String,
    val blurb: String,
    internal val searchDepth: Int,
    internal val nodeBudget: Int,
    internal val timeBudgetMs: Long,
    internal val usesQuiescence: Boolean,
    /** Random swing added to each move's score before the best one is picked, in centipawns. */
    internal val noiseCentipawns: Int,
    /** How often a move is played without looking at all. */
    internal val randomMoveChance: Double
) {
    EASY(
        label = "Easy",
        blurb = "Looks one move ahead — takes what it is offered, and hangs pieces of its own",
        searchDepth = 1,
        nodeBudget = 30_000,
        timeBudgetMs = 400L,
        usesQuiescence = false,
        noiseCentipawns = 120,
        randomMoveChance = 0.20
    ),
    MEDIUM(
        label = "Medium",
        blurb = "Looks three moves ahead and plays every exchange out before judging it",
        searchDepth = 3,
        nodeBudget = 150_000,
        timeBudgetMs = 1_500L,
        usesQuiescence = true,
        noiseCentipawns = 30,
        randomMoveChance = 0.0
    ),
    HARD(
        label = "Hard",
        blurb = "Searches as deep as a couple of seconds allow and plays the best it can find",
        searchDepth = 6,
        nodeBudget = 400_000,
        timeBudgetMs = 3_000L,
        usesQuiescence = true,
        noiseCentipawns = 0,
        randomMoveChance = 0.0
    )
}

/**
 * The computer opponent: an alpha-beta search over [Position] with a piece-square evaluation.
 *
 * Nothing here mutates the game — [chooseMove] takes a position and hands back a legal move — so it
 * can be run off the main thread and thrown away if the player takes the move back.
 */
object ChessBot {

    /**
     * The move the computer would play, or null when the game is already decided.
     *
     * [timeBudgetMs] trims the level's own budget, which is how a level keeps its shape under a
     * fast time control: the search gives up depth rather than flagging on its own clock.
     */
    fun chooseMove(
        game: ChessGame,
        level: ChessBotLevel,
        random: Random = Random.Default,
        timeBudgetMs: Long = level.timeBudgetMs
    ): Move? {
        if (game.isOver) return null
        return Search(level, random, repetitionCounts(game), timeBudgetMs).bestMove(game.position)
    }

    /** Same, from a bare position — used by the tests and anywhere there is no move history. */
    fun chooseMove(
        position: Position,
        level: ChessBotLevel,
        random: Random = Random.Default,
        timeBudgetMs: Long = level.timeBudgetMs
    ): Move? = Search(level, random, emptyMap(), timeBudgetMs).bestMove(position)

    /**
     * The static score of [position] in centipawns, from the side to move's point of view.
     * Positive means the player on move stands better.
     */
    internal fun evaluate(position: Position): Int {
        val white = evaluateForWhite(position)
        return if (position.sideToMove == PieceColor.WHITE) white else -white
    }

    /** How often each position of the game so far has already appeared. */
    private fun repetitionCounts(game: ChessGame): Map<String, Int> {
        val counts = HashMap<String, Int>(game.moveCount + 1)
        counts[game.initialPosition.repetitionKey()] = 1
        for (ply in game.history) {
            val key = ply.positionAfter.repetitionKey()
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts
    }
}

// region Search

private const val MATE_SCORE = 30_000
private const val INFINITY = 32_000
private const val DRAW_SCORE = 0

/** Scores this far from a mate score can only have come out of a mating line. */
private const val MATE_THRESHOLD = MATE_SCORE - 1_000

private class Search(
    private val level: ChessBotLevel,
    private val random: Random,
    /** Positions already reached in the game, so the root can spot a third occurrence. */
    private val repetitions: Map<String, Int>,
    timeBudgetMs: Long
) {

    private var nodes = 0
    private var aborted = false
    private val deadlineNanos =
        System.nanoTime() + minOf(timeBudgetMs, level.timeBudgetMs) * 1_000_000L

    /** Two quiet moves per ply that caused a cut-off, tried early next time. */
    private val killers = arrayOfNulls<Move>(2 * MAX_PLY)

    /** from*64+to counters, so moves that keep working keep being tried first. */
    private val history = IntArray(64 * 64)

    fun bestMove(position: Position): Move? {
        val legal = position.legalMoves()
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal.first()
        if (level.randomMoveChance > 0.0 && random.nextDouble() < level.randomMoveChance) {
            return legal[random.nextInt(legal.size)]
        }

        // Exact scores for every root move are only needed when the choice is a noisy one; the
        // levels that always play the best move can narrow the window as they go.
        val fullWindow = level.noiseCentipawns > 0

        var order = legal.sortedByDescending { orderScore(it, ply = 0) }
        var best = order.first()

        for (depth in 1..level.searchDepth) {
            val scored = ArrayList<ScoredMove>(order.size)
            var alpha = -INFINITY
            for (move in order) {
                val child = position.makeMove(move)
                val score = when {
                    isThirdOccurrence(child) -> DRAW_SCORE
                    else -> -search(
                        position = child,
                        depth = depth - 1,
                        alpha = -INFINITY,
                        beta = if (fullWindow) INFINITY else -alpha,
                        ply = 1
                    )
                }
                if (aborted) break
                scored.add(ScoredMove(move, score))
                if (score > alpha) alpha = score
            }

            // A half-finished iteration says nothing about the moves it never reached.
            if (aborted) break

            order = scored.sortedByDescending { it.score }.map { it.move }
            best = pick(scored)
            // A forced mate is not going to be improved on by looking further.
            if (abs(alpha) > MATE_THRESHOLD) break
        }

        return best
    }

    private fun pick(scored: List<ScoredMove>): Move {
        val noise = level.noiseCentipawns
        if (noise <= 0) return scored.maxBy { it.score }.move
        return scored.maxBy { it.score + random.nextInt(-noise, noise + 1) }.move
    }

    /** Would playing into [position] repeat a position for the third time? */
    private fun isThirdOccurrence(position: Position): Boolean =
        (repetitions[position.repetitionKey()] ?: 0) >= 2

    private fun search(position: Position, depth: Int, alpha: Int, beta: Int, ply: Int): Int {
        nodes++
        if (position.halfmoveClock >= 100) return DRAW_SCORE
        if (depth <= 0) {
            return if (level.usesQuiescence) {
                quiescence(position, alpha, beta, ply, 0)
            } else {
                leafScore(position, ply)
            }
        }
        if (outOfBudget()) return ChessBot.evaluate(position)

        val us = position.sideToMove
        var a = alpha
        var best = -INFINITY
        var anyLegal = false

        for (move in position.pseudoLegalMoves().sortedByDescending { orderScore(it, ply) }) {
            val child = position.makeMove(move)
            if (child.isSquareAttacked(child.kingSquare(us), us.opposite)) continue
            anyLegal = true

            val score = -search(child, depth - 1, -beta, -a, ply + 1)
            if (aborted) return best

            if (score > best) best = score
            if (best > a) a = best
            if (a >= beta) {
                if (!move.isCapture) remember(move, depth, ply)
                break
            }
        }

        if (!anyLegal) {
            return if (position.isInCheck(us)) -MATE_SCORE + ply else DRAW_SCORE
        }
        return best
    }

    /**
     * Plays out the captures before judging a position, so the search is never left holding a
     * half-finished exchange. Checks are answered in full for a few plies so a mate at the end of
     * a forcing line is still seen.
     */
    private fun quiescence(position: Position, alpha: Int, beta: Int, ply: Int, qPly: Int): Int {
        nodes++
        if (outOfBudget()) return ChessBot.evaluate(position)

        val us = position.sideToMove
        var a = alpha

        if (position.isInCheck(us)) {
            if (qPly >= MAX_CHECK_PLY) return ChessBot.evaluate(position)
            val evasions = position.legalMoves()
            if (evasions.isEmpty()) return -MATE_SCORE + ply
            var best = -INFINITY
            for (move in evasions.sortedByDescending { orderScore(it, ply) }) {
                val score = -quiescence(position.makeMove(move), -beta, -a, ply + 1, qPly + 1)
                if (aborted) return best
                if (score > best) best = score
                if (best > a) a = best
                if (a >= beta) break
            }
            return best
        }

        val standPat = ChessBot.evaluate(position)
        if (standPat >= beta) return standPat
        if (standPat > a) a = standPat
        var best = standPat

        val forcing = position.pseudoLegalMoves()
            .filter { it.isCapture || it.promotion != null }
            .sortedByDescending { orderScore(it, ply) }

        for (move in forcing) {
            val child = position.makeMove(move)
            if (child.isSquareAttacked(child.kingSquare(us), us.opposite)) continue
            val score = -quiescence(child, -beta, -a, ply + 1, qPly + 1)
            if (aborted) return best
            if (score > best) best = score
            if (best > a) a = best
            if (a >= beta) break
        }
        return best
    }

    /**
     * A leaf for the levels that do not search captures out. Mate is still checked for, so even the
     * shallowest level can finish a game off.
     */
    private fun leafScore(position: Position, ply: Int): Int {
        val us = position.sideToMove
        if (position.isInCheck(us) && position.legalMoves().isEmpty()) return -MATE_SCORE + ply
        return ChessBot.evaluate(position)
    }

    private fun orderScore(move: Move, ply: Int): Int {
        var score = 0
        if (move.isCapture) {
            // Most valuable victim, least valuable attacker: PxQ before QxP.
            score += 100_000 + 10 * pieceValue(move.captured!!.type) - pieceValue(move.piece.type)
        }
        move.promotion?.let { score += 90_000 + pieceValue(it) }
        if (!move.isCapture && ply < MAX_PLY) {
            if (move == killers[2 * ply] || move == killers[2 * ply + 1]) score += 80_000
        }
        if (move.isCastle) score += 400
        return score + history[move.from * 64 + move.to]
    }

    private fun remember(move: Move, depth: Int, ply: Int) {
        if (ply < MAX_PLY && killers[2 * ply] != move) {
            killers[2 * ply + 1] = killers[2 * ply]
            killers[2 * ply] = move
        }
        val index = move.from * 64 + move.to
        history[index] = minOf(history[index] + depth * depth, MAX_HISTORY)
    }

    private fun outOfBudget(): Boolean {
        if (aborted) return true
        if (nodes >= level.nodeBudget) {
            aborted = true
        } else if (nodes and 1_023 == 0 && System.nanoTime() > deadlineNanos) {
            aborted = true
        }
        return aborted
    }

    private companion object {
        const val MAX_PLY = 64
        const val MAX_CHECK_PLY = 6
        const val MAX_HISTORY = 60_000
    }
}

private class ScoredMove(val move: Move, val score: Int)

// endregion

// region Evaluation

private const val PAWN_VALUE = 100
private const val KNIGHT_VALUE = 320
private const val BISHOP_VALUE = 330
private const val ROOK_VALUE = 500
private const val QUEEN_VALUE = 900

private fun pieceValue(type: PieceType): Int = when (type) {
    PieceType.PAWN -> PAWN_VALUE
    PieceType.KNIGHT -> KNIGHT_VALUE
    PieceType.BISHOP -> BISHOP_VALUE
    PieceType.ROOK -> ROOK_VALUE
    PieceType.QUEEN -> QUEEN_VALUE
    PieceType.KING -> 0
}

/**
 * Material, piece placement and pawn structure, in centipawns from White's point of view.
 *
 * The placement tables are the well-known simplified ones: knights want the middle, rooks want the
 * seventh rank, the king wants a corner until the queens come off and the centre afterwards.
 */
private fun evaluateForWhite(position: Position): Int {
    var score = 0
    var whiteBishops = 0
    var blackBishops = 0
    var nonPawnMaterial = 0

    val whitePawnCount = IntArray(8)
    val blackPawnCount = IntArray(8)
    // The pawn nearest each side's own end of a file is what decides whether a passer gets through.
    val whiteMinRank = IntArray(8) { 8 }
    val blackMaxRank = IntArray(8) { -1 }
    val whitePawns = ArrayList<Int>(8)
    val blackPawns = ArrayList<Int>(8)

    for (square in 0..63) {
        val piece = position.pieceAt(square) ?: continue
        val white = piece.color == PieceColor.WHITE
        val file = Square.file(square)
        val rank = Square.rank(square)

        val value = pieceValue(piece.type)
        score += if (white) value else -value

        when (piece.type) {
            PieceType.PAWN -> if (white) {
                whitePawnCount[file]++
                whitePawns.add(square)
                if (rank < whiteMinRank[file]) whiteMinRank[file] = rank
            } else {
                blackPawnCount[file]++
                blackPawns.add(square)
                if (rank > blackMaxRank[file]) blackMaxRank[file] = rank
            }
            PieceType.BISHOP -> {
                if (white) whiteBishops++ else blackBishops++
                nonPawnMaterial += value
            }
            PieceType.KING -> Unit
            else -> nonPawnMaterial += value
        }
    }

    val endgame = nonPawnMaterial <= ENDGAME_MATERIAL

    for (square in 0..63) {
        val piece = position.pieceAt(square) ?: continue
        val table = when (piece.type) {
            PieceType.PAWN -> PAWN_TABLE
            PieceType.KNIGHT -> KNIGHT_TABLE
            PieceType.BISHOP -> BISHOP_TABLE
            PieceType.ROOK -> ROOK_TABLE
            PieceType.QUEEN -> QUEEN_TABLE
            PieceType.KING -> if (endgame) KING_ENDGAME_TABLE else KING_TABLE
        }
        val placement = table[tableIndex(square, piece.color)]
        score += if (piece.color == PieceColor.WHITE) placement else -placement
    }

    if (whiteBishops >= 2) score += BISHOP_PAIR
    if (blackBishops >= 2) score -= BISHOP_PAIR

    score += pawnStructure(whitePawns, whitePawnCount, blackMaxRank, PieceColor.WHITE)
    score -= pawnStructure(blackPawns, blackPawnCount, whiteMinRank, PieceColor.BLACK)

    return score
}

/** Doubled and isolated pawns cost, passed pawns pay — always as a positive score for [color]. */
private fun pawnStructure(
    pawns: List<Int>,
    ownCount: IntArray,
    opponentBlockers: IntArray,
    color: PieceColor
): Int {
    var score = 0

    for (file in 0..7) {
        val count = ownCount[file]
        if (count == 0) continue
        if (count > 1) score -= DOUBLED_PAWN * (count - 1)
        val left = if (file > 0) ownCount[file - 1] else 0
        val right = if (file < 7) ownCount[file + 1] else 0
        if (left == 0 && right == 0) score -= ISOLATED_PAWN * count
    }

    for (square in pawns) {
        val file = Square.file(square)
        val rank = Square.rank(square)
        var passed = true
        for (f in maxOf(0, file - 1)..minOf(7, file + 1)) {
            val blocker = opponentBlockers[f]
            val blocks = if (color == PieceColor.WHITE) blocker > rank else blocker < rank
            if (blocks) {
                passed = false
                break
            }
        }
        if (passed) {
            val advanced = if (color == PieceColor.WHITE) rank else 7 - rank
            score += PASSED_PAWN[advanced]
        }
    }

    return score
}

/**
 * The tables below are written out as a board with rank 8 on the first row, so they read the way a
 * board looks. Squares are numbered from a1, hence the flip for White and the straight read for
 * Black, whose side of the board is the top of the table.
 */
private fun tableIndex(square: Int, color: PieceColor): Int {
    val file = Square.file(square)
    val rank = Square.rank(square)
    return if (color == PieceColor.WHITE) (7 - rank) * 8 + file else rank * 8 + file
}

private const val BISHOP_PAIR = 30
private const val DOUBLED_PAWN = 12
private const val ISOLATED_PAWN = 16
private const val ENDGAME_MATERIAL = 2 * (ROOK_VALUE + BISHOP_VALUE)

/** What a passed pawn is worth, by how far it has come. */
private val PASSED_PAWN = intArrayOf(0, 5, 12, 25, 45, 75, 120, 0)

private val PAWN_TABLE = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0,
    50, 50, 50, 50, 50, 50, 50, 50,
    10, 10, 20, 30, 30, 20, 10, 10,
    5, 5, 10, 25, 25, 10, 5, 5,
    0, 0, 0, 20, 20, 0, 0, 0,
    5, -5, -10, 0, 0, -10, -5, 5,
    5, 10, 10, -20, -20, 10, 10, 5,
    0, 0, 0, 0, 0, 0, 0, 0
)

private val KNIGHT_TABLE = intArrayOf(
    -50, -40, -30, -30, -30, -30, -40, -50,
    -40, -20, 0, 0, 0, 0, -20, -40,
    -30, 0, 10, 15, 15, 10, 0, -30,
    -30, 5, 15, 20, 20, 15, 5, -30,
    -30, 0, 15, 20, 20, 15, 0, -30,
    -30, 5, 10, 15, 15, 10, 5, -30,
    -40, -20, 0, 5, 5, 0, -20, -40,
    -50, -40, -30, -30, -30, -30, -40, -50
)

private val BISHOP_TABLE = intArrayOf(
    -20, -10, -10, -10, -10, -10, -10, -20,
    -10, 0, 0, 0, 0, 0, 0, -10,
    -10, 0, 5, 10, 10, 5, 0, -10,
    -10, 5, 5, 10, 10, 5, 5, -10,
    -10, 0, 10, 10, 10, 10, 0, -10,
    -10, 10, 10, 10, 10, 10, 10, -10,
    -10, 5, 0, 0, 0, 0, 5, -10,
    -20, -10, -10, -10, -10, -10, -10, -20
)

private val ROOK_TABLE = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0,
    5, 10, 10, 10, 10, 10, 10, 5,
    -5, 0, 0, 0, 0, 0, 0, -5,
    -5, 0, 0, 0, 0, 0, 0, -5,
    -5, 0, 0, 0, 0, 0, 0, -5,
    -5, 0, 0, 0, 0, 0, 0, -5,
    -5, 0, 0, 0, 0, 0, 0, -5,
    0, 0, 0, 5, 5, 0, 0, 0
)

private val QUEEN_TABLE = intArrayOf(
    -20, -10, -10, -5, -5, -10, -10, -20,
    -10, 0, 0, 0, 0, 0, 0, -10,
    -10, 0, 5, 5, 5, 5, 0, -10,
    -5, 0, 5, 5, 5, 5, 0, -5,
    0, 0, 5, 5, 5, 5, 0, -5,
    -10, 5, 5, 5, 5, 5, 0, -10,
    -10, 0, 5, 0, 0, 0, 0, -10,
    -20, -10, -10, -5, -5, -10, -10, -20
)

private val KING_TABLE = intArrayOf(
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -20, -30, -30, -40, -40, -30, -30, -20,
    -10, -20, -20, -20, -20, -20, -20, -10,
    20, 20, 0, 0, 0, 0, 20, 20,
    20, 30, 10, 0, 0, 10, 30, 20
)

private val KING_ENDGAME_TABLE = intArrayOf(
    -50, -40, -30, -20, -20, -30, -40, -50,
    -30, -20, -10, 0, 0, -10, -20, -30,
    -30, -10, 20, 30, 30, 20, -10, -30,
    -30, -10, 30, 40, 40, 30, -10, -30,
    -30, -10, 30, 40, 40, 30, -10, -30,
    -30, -10, 20, 30, 30, 20, -10, -30,
    -30, -30, 0, 0, 0, 0, -30, -30,
    -50, -30, -30, -30, -30, -30, -30, -50
)

// endregion
