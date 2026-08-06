package com.example.model

import com.example.ludo.LudoColor
import com.example.ludo.LudoGame
import com.example.ludo.LudoMove
import com.example.ludo.LudoPlayer
import com.example.ludo.LudoRules

/** How a game is set up before it starts. By default one human plays three computer opponents. */
data class LudoSetup(
    val playerCount: Int = 4,
    val botColors: Set<LudoColor> = setOf(LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE),
    val rules: LudoRules = LudoRules()
) {
    val seats: List<LudoColor> get() = LudoGame.seatsFor(playerCount)

    /** Bots restricted to the seats actually in play. */
    val activeBots: Set<LudoColor> get() = botColors.intersect(seats.toSet())

    val humanCount: Int get() = seats.count { it !in botColors }

    fun describe(): String {
        val humans = humanCount
        val bots = seats.size - humans
        return buildString {
            append("$playerCount players · ")
            append(if (humans == 1) "1 human" else "$humans humans")
            if (bots > 0) append(", $bots computer")
            if (rules.blockades) append(" · blockades on")
        }
    }
}

/** Everything the Ludo screen renders. */
data class LudoUiState(
    val game: LudoGame = LudoGame.new(bots = LudoSetup().activeBots),
    val setup: LudoSetup = LudoSetup(),
    /** The face currently shown on the die — it flickers while rolling. */
    val diceFace: Int = 6,
    val isRolling: Boolean = false,
    val lastMove: LudoMove? = null,
    val message: String = "Roll to start",
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val resultDismissed: Boolean = false
) {
    val currentPlayer: LudoPlayer get() = game.currentPlayer

    val currentColor: LudoColor get() = game.currentColor

    val currentIsBot: Boolean get() = currentPlayer.isBot

    /** Tokens the player on turn may move right now. */
    val movableTokens: Set<Int>
        get() = if (isRolling || currentIsBot) emptySet() else game.movableTokens()

    /** True while a human has rolled and still has to choose a token. */
    val awaitingTokenChoice: Boolean
        get() = !isRolling && !currentIsBot && game.dice != null && game.legalMoves.isNotEmpty()

    val canRoll: Boolean
        get() = !isRolling && !currentIsBot && game.canRoll

    fun isMovable(color: LudoColor, tokenIndex: Int): Boolean =
        color == currentColor && tokenIndex in movableTokens
}
