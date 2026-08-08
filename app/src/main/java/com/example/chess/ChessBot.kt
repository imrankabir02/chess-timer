package com.example.chess

import kotlin.random.Random

/**
 * How hard the computer opponent plays. The three levels differ in how far ahead they look, whether
 * they follow captures through to the end, and how willing they are to play something other than the
 * best move they found.
 */
enum class ChessDifficulty(
    val label: String,
    val blurb: String,
    /** Plies of full-width search. */
    internal val searchDepth: Int,
    /** Whether captures are followed past the horizon before a position is scored. */
    internal val quiescence: Boolean,
    /** How far below the best score a move may be and still get played, in centipawns. */
    internal val scoreSlack: Int,
    /** At most this many near-best moves are considered when picking. */
    internal val choiceWidth: Int,
    /** Search is abandoned past this many nodes, and the last finished depth is used. */
    internal val nodeBudget: Int
) {
    EASY(
        label = "Easy",
        blurb = "Looks one move ahead. Takes anything you leave hanging — and hangs plenty itself.",
        searchDepth = 1,
        quiescence = false,
        scoreSlack = 70,
        choiceWidth = 4,
        nodeBudget = 40_000
    ),
    MEDIUM(
        label = "Medium",
        blurb = "Looks two moves ahead and follows every capture through. Punishes loose pieces.",
        searchDepth = 2,
        quiescence = true,
        scoreSlack = 30,
        choiceWidth = 3,
        nodeBudget = 200_000
    ),
    HARD(
        label = "Hard",
        blurb = "Searches four moves deep with no mercy and always plays the best line it finds.",
        searchDepth = 4,
        quiescence = true,
        scoreSlack = 0,
        choiceWidth = 1,
        nodeBudget = 1_200_000
    );

    companion object {
        val DEFAULT = MEDIUM
    }
}

/**
 * The computer opponent: a negamax search with alpha-beta pruning over [Evaluation], wrapped in the
 * per-level tuning of [ChessDifficulty].
 *
 * Pure Kotlin like the rest of [com.example.chess] — the caller decides which thread it runs on.
 * Given the same [random] it always plays the same game, which is what makes it testable.
 */
class ChessBot(
    val difficulty: ChessDifficulty = ChessDifficulty.DEFAULT,
    private val random: Random = Random.Default
) {

    private var nodes = 0
    private var aborted = false

    /** A move and the position it leads to, so the search never makes the same move twice. */
    private class Child(val move: Move, val position: Position)

    private class Scored(val child: Child, val score: Int)

    /**
     * The move the computer would play in [game], or null when it is over or has no legal move.
     * Positions already seen twice are scored as the draws they would become.
     */
    fun chooseMove(game: ChessGame): Move? {
        if (game.isOver) return null
        return chooseMove(game.position, repetitionCounts(game))
    }

    /** As above, from a bare position. [seen] maps repetition keys to how often they have occurred. */
    fun chooseMove(position: Position, seen: Map<String, Int> = emptyMap()): Move? {
        val children = legalChildren(position)
        if (children.isEmpty()) return null
        if (children.size == 1) return children.first().move

        nodes = 0
        aborted = false

        var order = children.sortedByDescending { orderingScore(it.move) }
        var best: List<Scored> = order.map { Scored(it, 0) }

        // Iterative deepening: each pass orders the next one, and an abandoned pass is discarded.
        for (depth in 1..difficulty.searchDepth) {
            val pass = searchRoot(order, depth, seen)
            if (aborted) break
            best = pass
            order = pass.map { it.child }
        }

        return pick(best)
    }

    /**
     * Whether the computer would accept a draw offer while playing [color]. It agrees when it is not
     * better off playing on — and the easier levels are rather more agreeable about it.
     */
    fun acceptsDrawOffer(game: ChessGame, color: PieceColor): Boolean {
        // A dead drawn position has already ended the game, so there is nothing left to agree to.
        if (game.isOver) return false
        val position = game.position

        nodes = 0
        aborted = false
        val children = legalChildren(position)
        if (children.isEmpty()) return true

        val sideToMoveScore = searchRoot(children, DRAW_OFFER_DEPTH, repetitionCounts(game))
            .firstOrNull()?.score ?: 0
        // searchRoot scores from the side to move's point of view; flip it if that is the offerer.
        val botScore = if (position.sideToMove == color) sideToMoveScore else -sideToMoveScore
        return botScore <= drawAcceptThreshold()
    }

    private fun drawAcceptThreshold(): Int = when (difficulty) {
        ChessDifficulty.EASY -> 100
        ChessDifficulty.MEDIUM -> -40
        ChessDifficulty.HARD -> -100
    }

    // region Search

    /** Scores every root move and returns them best first, from the side to move's point of view. */
    private fun searchRoot(children: List<Child>, depth: Int, seen: Map<String, Int>): List<Scored> {
        // A level that may play something other than the best move needs honest scores for the
        // also-rans, so it searches the root with a full window. A level that always plays the best
        // move can narrow as it goes, which is far cheaper.
        val narrowing = difficulty.scoreSlack == 0 && difficulty.choiceWidth == 1
        var alpha = -INFINITY
        val scored = ArrayList<Scored>(children.size)

        for (child in children) {
            val score = if ((seen[child.position.repetitionKey()] ?: 0) >= 2) {
                // Playing this would be the third occurrence, so it is simply a draw.
                DRAW
            } else {
                -negamax(child.position, depth - 1, -INFINITY, if (narrowing) -alpha else INFINITY, 1)
            }
            if (aborted) return scored
            scored.add(Scored(child, score))
            if (narrowing && score > alpha) alpha = score
        }

        return scored.sortedByDescending { it.score }
    }

    private fun negamax(position: Position, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        if (nodes >= difficulty.nodeBudget) {
            aborted = true
            return DRAW
        }
        nodes++

        if (position.halfmoveClock >= 100 || Material.isInsufficient(position)) return DRAW

        if (depth <= 0) return leafScore(position, alphaIn, beta, ply)

        val children = legalChildren(position)
        if (children.isEmpty()) {
            // Mating sooner is better, so the score shrinks with the distance from the root.
            return if (position.isInCheck(position.sideToMove)) -(Evaluation.MATE_SCORE - ply) else DRAW
        }

        var alpha = alphaIn
        for (child in children.sortedByDescending { orderingScore(it.move) }) {
            val score = -negamax(child.position, depth - 1, -beta, -alpha, ply + 1)
            if (aborted) return alpha
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    /** The horizon. Checkmate is still worth spotting here; everything else is scored. */
    private fun leafScore(position: Position, alpha: Int, beta: Int, ply: Int): Int {
        if (position.isInCheck(position.sideToMove) && position.legalMoves().isEmpty()) {
            return -(Evaluation.MATE_SCORE - ply)
        }
        return if (difficulty.quiescence) {
            quiesce(position, alpha, beta, ply, QUIESCENCE_DEPTH)
        } else {
            Evaluation.evaluateForSideToMove(position)
        }
    }

    /**
     * Plays out the captures and promotions still available so the search is never caught scoring a
     * position halfway through a trade.
     */
    private fun quiesce(position: Position, alphaIn: Int, beta: Int, ply: Int, depth: Int): Int {
        if (nodes >= difficulty.nodeBudget) {
            aborted = true
            return alphaIn
        }
        nodes++

        var alpha = alphaIn
        val standPat = Evaluation.evaluateForSideToMove(position)
        if (standPat >= beta) return beta
        if (standPat > alpha) alpha = standPat
        if (depth <= 0) return alpha

        val mover = position.sideToMove
        val tactical = position.pseudoLegalMoves()
            .filter { it.isCapture || it.promotion != null }
            .sortedByDescending { orderingScore(it) }

        for (move in tactical) {
            val next = position.makeMove(move)
            if (next.isSquareAttacked(next.kingSquare(mover), mover.opposite)) continue
            val score = -quiesce(next, -beta, -alpha, ply + 1, depth - 1)
            if (aborted) return alpha
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    // endregion

    // region Move handling

    /**
     * Every legal move with the position it produces. Generating pseudo-legal moves and keeping the
     * ones that leave the king safe costs a single [Position.makeMove] per move, where asking the
     * position for its legal moves and then playing them would cost two.
     */
    private fun legalChildren(position: Position): List<Child> {
        val mover = position.sideToMove
        val children = ArrayList<Child>(40)
        for (move in position.pseudoLegalMoves()) {
            val next = position.makeMove(move)
            if (!next.isSquareAttacked(next.kingSquare(mover), mover.opposite)) {
                children.add(Child(move, next))
            }
        }
        return children
    }

    /**
     * Cheap ordering so alpha-beta gets its cutoffs early: win material first, taking the biggest
     * piece with the smallest one, then promotions, then castling.
     */
    private fun orderingScore(move: Move): Int {
        var score = 0
        move.captured?.let { victim ->
            score += 10_000 + Evaluation.valueOf(victim.type) * 10 - Evaluation.valueOf(move.piece.type)
        }
        move.promotion?.let { score += 8_000 + Evaluation.valueOf(it) }
        if (move.isCastle) score += 200
        return score
    }

    /** Picks from the near-best moves, so a level with slack does not play the same game every time. */
    private fun pick(scored: List<Scored>): Move? {
        if (scored.isEmpty()) return null
        val best = scored.first().score
        // Never throw away a forced mate for the sake of variety.
        if (best >= Evaluation.MATE_SCORE - MAX_MATE_DISTANCE) return scored.first().child.move

        val pool = scored
            .filter { best - it.score <= difficulty.scoreSlack }
            .take(difficulty.choiceWidth)
        if (pool.size <= 1) return pool.firstOrNull()?.child?.move ?: scored.first().child.move
        return pool[random.nextInt(pool.size)].child.move
    }

    private fun repetitionCounts(game: ChessGame): Map<String, Int> {
        val counts = HashMap<String, Int>()
        counts[game.initialPosition.repetitionKey()] = 1
        for (ply in game.history) {
            val key = ply.positionAfter.repetitionKey()
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts
    }

    // endregion

    companion object {
        private const val DRAW = 0
        private const val INFINITY = Evaluation.MATE_SCORE + 1_000
        private const val QUIESCENCE_DEPTH = 6
        private const val DRAW_OFFER_DEPTH = 2
        private const val MAX_MATE_DISTANCE = 100
    }
}
