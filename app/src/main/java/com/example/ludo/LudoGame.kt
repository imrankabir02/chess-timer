package com.example.ludo

/** One of a player's four tokens. [progress] is the distance travelled, see [LudoBoard]. */
data class LudoToken(
    val color: LudoColor,
    val index: Int,
    val progress: Int = LudoBoard.YARD
) {
    val isInYard: Boolean get() = progress == LudoBoard.YARD
    val isHome: Boolean get() = progress == LudoBoard.HOME
    val isOnTrack: Boolean get() = progress in 1..LudoBoard.TRACK_STEPS
    val isInHomeColumn: Boolean get() = progress in LudoBoard.FIRST_HOME_COLUMN_PROGRESS until LudoBoard.HOME

    /** The shared-track square this token stands on, or null when it is in the yard or past it. */
    val trackIndex: Int? get() = LudoBoard.trackIndexOf(color, progress)

    val isSafe: Boolean get() = LudoBoard.isSafeProgress(color, progress)
}

data class LudoPlayer(
    val color: LudoColor,
    val isBot: Boolean = false,
    val tokens: List<LudoToken> = (0 until LudoBoard.TOKENS_PER_PLAYER).map { LudoToken(color, it) }
) {
    val hasFinished: Boolean get() = tokens.all { it.isHome }
    val tokensHome: Int get() = tokens.count { it.isHome }
    val tokensInYard: Int get() = tokens.count { it.isInYard }
    val tokensInPlay: Int get() = tokens.count { !it.isInYard && !it.isHome }

    /** Total distance travelled by all four tokens: a simple measure of how far along a player is. */
    val totalProgress: Int get() = tokens.sumOf { it.progress }
}

/** A move of one token by the current dice value. */
data class LudoMove(
    val token: LudoToken,
    val to: Int,
    val captured: List<LudoToken> = emptyList(),
    val entersBoard: Boolean = false,
    val finishes: Boolean = false,
    val entersHomeColumn: Boolean = false
) {
    val color: LudoColor get() = token.color
    val from: Int get() = token.progress
    val isCapture: Boolean get() = captured.isNotEmpty()
}

/** The house rules in force. The defaults are the ones most Ludo apps play by. */
data class LudoRules(
    val extraTurnOnSix: Boolean = true,
    val extraTurnOnCapture: Boolean = true,
    val extraTurnOnFinish: Boolean = true,
    /** Three sixes in a row void the turn — a common brake on lucky streaks. */
    val forfeitOnThreeSixes: Boolean = true,
    /** Two tokens of one colour on a square bar everyone else from passing or landing. */
    val blockades: Boolean = false
)

enum class LudoEventKind {
    ROLLED,
    MOVED,
    CAPTURED,
    TOKEN_HOME,
    EXTRA_TURN,
    NO_MOVES,
    THREE_SIXES,
    PLAYER_HOME,
    GAME_OVER
}

data class LudoEvent(val kind: LudoEventKind, val color: LudoColor, val message: String)

/**
 * A game of Ludo for two to four players. Every operation returns a new game, so the previous
 * state stays valid — the same shape as the chess engine in [com.example.chess].
 *
 * The dice is passed in rather than rolled here, which keeps the rules deterministic and testable;
 * the ViewModel owns the randomness.
 */
data class LudoGame(
    val players: List<LudoPlayer>,
    val turnIndex: Int = 0,
    val dice: Int? = null,
    val consecutiveSixes: Int = 0,
    val finishOrder: List<LudoColor> = emptyList(),
    val rules: LudoRules = LudoRules(),
    val lastEvents: List<LudoEvent> = emptyList()
) {

    init {
        require(players.size in 2..4) { "Ludo is played by two to four players" }
        require(turnIndex in players.indices) { "turnIndex out of range" }
    }

    val currentPlayer: LudoPlayer get() = players[turnIndex]

    val currentColor: LudoColor get() = currentPlayer.color

    /** The game ends when only one player is still going round. */
    val isOver: Boolean get() = finishOrder.size >= players.size - 1

    val winner: LudoColor? get() = finishOrder.firstOrNull().takeIf { isOver }

    /** Final placings: those who got all four tokens home, then whoever is left. */
    val standings: List<LudoColor>
        get() = finishOrder + players.map { it.color }.filter { it !in finishOrder }

    val canRoll: Boolean get() = !isOver && dice == null

    val allTokens: List<LudoToken> get() = players.flatMap { it.tokens }

    fun player(color: LudoColor): LudoPlayer = players.first { it.color == color }

    // region Moves

    /** Every move the current player may make with the dice they have rolled. */
    val legalMoves: List<LudoMove> by lazy {
        val value = dice
        if (value == null || isOver || currentPlayer.hasFinished) {
            emptyList()
        } else {
            currentPlayer.tokens.mapNotNull { moveFor(it, value) }
        }
    }

    fun movableTokens(): Set<Int> = legalMoves.map { it.token.index }.toSet()

    fun moveForToken(tokenIndex: Int): LudoMove? =
        legalMoves.firstOrNull { it.token.index == tokenIndex }

    private fun moveFor(token: LudoToken, value: Int): LudoMove? {
        if (token.isHome) return null

        val target = if (token.isInYard) {
            if (value != 6) return null
            1
        } else {
            token.progress + value
        }
        // The last step has to be exact: an overshoot is not a legal move.
        if (target > LudoBoard.HOME) return null
        if (rules.blockades && isPathBlocked(token, target)) return null

        return LudoMove(
            token = token,
            to = target,
            captured = capturesAt(token.color, target),
            entersBoard = token.isInYard,
            finishes = target == LudoBoard.HOME,
            entersHomeColumn = token.progress <= LudoBoard.TRACK_STEPS &&
                target >= LudoBoard.FIRST_HOME_COLUMN_PROGRESS
        )
    }

    /** Opponent tokens sent back to the yard by landing on [target]; none on a safe square. */
    private fun capturesAt(color: LudoColor, target: Int): List<LudoToken> {
        val index = LudoBoard.trackIndexOf(color, target) ?: return emptyList()
        if (index in LudoBoard.safeTrackIndices) return emptyList()
        return players.filter { it.color != color }
            .flatMap { other -> other.tokens.filter { it.trackIndex == index } }
    }

    /** With blockades in force, a pair of enemy tokens on a square stops anyone moving through. */
    private fun isPathBlocked(token: LudoToken, target: Int): Boolean {
        val first = if (token.isInYard) 1 else token.progress + 1
        val last = minOf(target, LudoBoard.TRACK_STEPS)
        for (progress in first..last) {
            val index = LudoBoard.trackIndexOf(token.color, progress) ?: continue
            if (hasBlockade(index, ignoring = token.color)) return true
        }
        return false
    }

    private fun hasBlockade(trackIndex: Int, ignoring: LudoColor): Boolean =
        players.any { player ->
            player.color != ignoring && player.tokens.count { it.trackIndex == trackIndex } >= 2
        }

    /** Every token standing on a given shared-track square, whoever it belongs to. */
    fun tokensOnTrackIndex(trackIndex: Int): List<LudoToken> =
        allTokens.filter { it.trackIndex == trackIndex }

    // endregion

    // region Turn flow

    /** Records a dice roll. Three sixes in a row hand the turn straight on. */
    fun roll(value: Int): LudoGame {
        require(value in 1..6) { "a die shows 1 to 6, not $value" }
        if (isOver || dice != null) return this

        val sixes = if (value == 6) consecutiveSixes + 1 else 0
        val rolled = LudoEvent(LudoEventKind.ROLLED, currentColor, "${currentColor.displayName} rolled $value")

        if (sixes >= 3 && rules.forfeitOnThreeSixes) {
            return advanceTurn(
                listOf(
                    rolled,
                    LudoEvent(LudoEventKind.THREE_SIXES, currentColor, "Three sixes — turn lost")
                )
            )
        }

        return copy(dice = value, consecutiveSixes = sixes, lastEvents = listOf(rolled))
    }

    /** Plays [move] if it is one of the current player's legal moves. */
    fun play(move: LudoMove): LudoGame {
        val value = dice ?: return this
        val legal = legalMoves.firstOrNull {
            it.token.color == move.token.color &&
                it.token.index == move.token.index &&
                it.to == move.to
        } ?: return this

        val capturedKeys = legal.captured.map { it.color to it.index }.toSet()
        val movedPlayers = players.map { player ->
            player.copy(
                tokens = player.tokens.map { token ->
                    when {
                        token.color == legal.color && token.index == legal.token.index ->
                            token.copy(progress = legal.to)
                        (token.color to token.index) in capturedKeys ->
                            token.copy(progress = LudoBoard.YARD)
                        else -> token
                    }
                }
            )
        }

        val events = mutableListOf<LudoEvent>()
        events += LudoEvent(
            kind = if (legal.isCapture) LudoEventKind.CAPTURED else LudoEventKind.MOVED,
            color = legal.color,
            message = describe(legal)
        )
        if (legal.finishes) {
            events += LudoEvent(
                LudoEventKind.TOKEN_HOME,
                legal.color,
                "${legal.color.displayName} sent a token home"
            )
        }

        val mover = movedPlayers[turnIndex]
        val justFinished = mover.hasFinished && mover.color !in finishOrder
        val order = if (justFinished) finishOrder + mover.color else finishOrder
        if (justFinished) {
            val place = order.size
            events += LudoEvent(
                LudoEventKind.PLAYER_HOME,
                mover.color,
                "${mover.color.displayName} is all home — place $place"
            )
        }

        val next = copy(players = movedPlayers, finishOrder = order)
        if (next.isOver) {
            val champion = order.firstOrNull() ?: mover.color
            events += LudoEvent(LudoEventKind.GAME_OVER, champion, "${champion.displayName} wins!")
            return next.copy(dice = null, consecutiveSixes = 0, lastEvents = events)
        }

        val earnedExtraTurn = !justFinished && (
            (value == 6 && rules.extraTurnOnSix) ||
                (legal.isCapture && rules.extraTurnOnCapture) ||
                (legal.finishes && rules.extraTurnOnFinish)
            )

        return if (earnedExtraTurn) {
            events += LudoEvent(
                LudoEventKind.EXTRA_TURN,
                legal.color,
                "${legal.color.displayName} rolls again"
            )
            next.copy(dice = null, lastEvents = events)
        } else {
            next.advanceTurn(events)
        }
    }

    /** Hands the turn on when the roll produced nothing playable. */
    fun skipTurn(): LudoGame {
        if (isOver || dice == null || legalMoves.isNotEmpty()) return this
        return advanceTurn(
            listOf(
                LudoEvent(
                    LudoEventKind.NO_MOVES,
                    currentColor,
                    "${currentColor.displayName} cannot move"
                )
            )
        )
    }

    private fun advanceTurn(events: List<LudoEvent>): LudoGame {
        var index = turnIndex
        do {
            index = (index + 1) % players.size
        } while (players[index].hasFinished && index != turnIndex)

        return copy(
            turnIndex = index,
            dice = null,
            consecutiveSixes = 0,
            lastEvents = events
        )
    }

    private fun describe(move: LudoMove): String {
        val who = move.color.displayName
        return when {
            move.isCapture -> {
                val victims = move.captured.map { it.color.displayName }.distinct().joinToString(" and ")
                "$who knocked $victims back to the yard"
            }
            move.entersBoard -> "$who brought a token out"
            move.finishes -> "$who got a token home"
            move.entersHomeColumn -> "$who entered the home run"
            else -> "$who moved ${move.to - move.from}"
        }
    }

    // endregion

    companion object {

        /** Seats are always used in board order so the colours stay opposite each other. */
        fun seatsFor(playerCount: Int): List<LudoColor> = when (playerCount) {
            2 -> listOf(LudoColor.RED, LudoColor.YELLOW)
            3 -> listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW)
            else -> LudoColor.entries.toList()
        }

        fun new(
            playerCount: Int = 4,
            bots: Set<LudoColor> = emptySet(),
            rules: LudoRules = LudoRules()
        ): LudoGame {
            val seats = seatsFor(playerCount.coerceIn(2, 4))
            return LudoGame(
                players = seats.map { LudoPlayer(color = it, isBot = it in bots) },
                rules = rules
            )
        }
    }
}
