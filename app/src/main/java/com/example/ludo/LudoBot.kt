package com.example.ludo

/**
 * Picks a move for a computer player. It is a plain scoring heuristic — no search — which is
 * plenty for Ludo, where the dice does most of the deciding.
 */
object LudoBot {

    fun chooseMove(game: LudoGame): LudoMove? {
        val moves = game.legalMoves
        if (moves.isEmpty()) return null
        // maxByOrNull keeps the first best move, so equal scores resolve in token order.
        return moves.maxByOrNull { score(game, it) }
    }

    /** Higher is better. Exposed so the reasoning can be asserted in tests. */
    fun score(game: LudoGame, move: LudoMove): Int {
        var score = 0

        if (move.finishes) score += 1_000
        if (move.entersHomeColumn) score += 220

        // Knocking a token back is worth roughly what it had travelled.
        for (victim in move.captured) {
            score += 300 + victim.progress * 5
        }

        if (move.entersBoard) {
            // Getting out matters most when there is nothing else on the board.
            val inPlay = game.currentPlayer.tokensInPlay
            score += 260 - inPlay * 45
        }

        val landsSafely = LudoBoard.isSafeProgress(move.color, move.to)
        if (landsSafely) score += 70

        if (!landsSafely && isReachableByOpponent(game, move.color, move.to)) {
            // The further along a token is, the more painful it is to lose.
            score -= 90 + move.to * 3
        }

        if (!move.entersBoard && isUnderThreat(game, move.token)) {
            // Running away from a square an opponent can reach.
            score += 60 + move.from
        }

        // All else equal, push the leading token on.
        score += move.to

        return score
    }

    /** Could any opponent land on [progress] with a single roll? */
    private fun isReachableByOpponent(game: LudoGame, color: LudoColor, progress: Int): Boolean {
        val target = LudoBoard.trackIndexOf(color, progress) ?: return false
        return game.players
            .filter { it.color != color && !it.hasFinished }
            .any { opponent ->
                opponent.tokens.any { token ->
                    when {
                        token.isOnTrack -> (1..6).any { step ->
                            LudoBoard.trackIndexOf(opponent.color, token.progress + step) == target
                        }
                        // A token in the yard comes out onto its start square with a six.
                        token.isInYard -> LudoBoard.trackIndexOf(opponent.color, 1) == target
                        else -> false
                    }
                }
            }
    }

    private fun isUnderThreat(game: LudoGame, token: LudoToken): Boolean {
        if (!token.isOnTrack || token.isSafe) return false
        return isReachableByOpponent(game, token.color, token.progress)
    }
}
