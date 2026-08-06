package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ludo.LudoBot
import com.example.ludo.LudoColor
import com.example.ludo.LudoEventKind
import com.example.ludo.LudoGame
import com.example.ludo.LudoMove
import com.example.model.LudoSetup
import com.example.model.LudoUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Runs a game of Ludo. The rules live in [com.example.ludo]; this class owns the dice, the pacing
 * of the computer players, and the feedback effects.
 */
class LudoViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(LudoUiState())
    val state: StateFlow<LudoUiState> = _state.asStateFlow()

    private var turnJob: Job? = null
    private var random: Random = Random.Default

    private val toneGenerator = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 70)
    } catch (e: Exception) {
        null
    }

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        startBotsIfNeeded()
    }

    // region Set up

    fun newGame(setup: LudoSetup = _state.value.setup) {
        turnJob?.cancel()
        _state.update { current ->
            LudoUiState(
                game = LudoGame.new(
                    playerCount = setup.playerCount,
                    bots = setup.activeBots,
                    rules = setup.rules
                ),
                setup = setup,
                diceFace = 6,
                message = "Roll to start",
                soundEnabled = current.soundEnabled,
                vibrationEnabled = current.vibrationEnabled
            )
        }
        vibrate(30)
        startBotsIfNeeded()
    }

    fun updateSetup(setup: LudoSetup) {
        _state.update { it.copy(setup = setup) }
    }

    /** Test hook: makes the dice reproducible. */
    internal fun useDice(source: Random) {
        random = source
    }

    // endregion

    // region Player actions

    fun rollDice() {
        if (!_state.value.canRoll) return
        turnJob?.cancel()
        turnJob = viewModelScope.launch {
            rollAndResolve()
            playBotTurns()
        }
    }

    fun onTokenTapped(color: LudoColor, tokenIndex: Int) {
        val current = _state.value
        if (current.isRolling || current.currentIsBot || current.game.isOver) return
        if (color != current.currentColor) return
        val move = current.game.moveForToken(tokenIndex) ?: return

        turnJob?.cancel()
        turnJob = viewModelScope.launch {
            applyMove(move)
            playBotTurns()
        }
    }

    fun toggleSound() {
        _state.update { it.copy(soundEnabled = !it.soundEnabled) }
    }

    fun toggleVibration() {
        _state.update { it.copy(vibrationEnabled = !it.vibrationEnabled) }
    }

    fun dismissResult() {
        _state.update { it.copy(resultDismissed = true) }
    }

    // endregion

    // region Turn flow

    private fun startBotsIfNeeded() {
        turnJob?.cancel()
        turnJob = viewModelScope.launch { playBotTurns() }
    }

    /** Keeps rolling for computer players until it is a human's turn again. */
    private suspend fun playBotTurns() {
        while (coroutineContext.isActive) {
            val game = _state.value.game
            if (game.isOver || !game.currentPlayer.isBot) return
            delay(BOT_PAUSE_MS)
            rollAndResolve()
        }
    }

    /** Rolls the die for whoever is on turn, then plays whatever follows automatically. */
    private suspend fun rollAndResolve() {
        val before = _state.value
        if (before.game.isOver || before.game.dice != null) return

        _state.update { it.copy(isRolling = true, message = "${it.currentColor.displayName} is rolling…") }
        repeat(DICE_FLICKER_FRAMES) {
            _state.update { it.copy(diceFace = random.nextInt(1, 7)) }
            delay(DICE_FLICKER_MS)
        }

        val value = random.nextInt(1, 7)
        val rolled = _state.value.game.roll(value)
        _state.update {
            it.copy(
                game = rolled,
                diceFace = value,
                isRolling = false,
                message = rolled.lastEvents.lastOrNull()?.message ?: it.message
            )
        }
        announce(rolled.lastEvents.map { it.kind })
        vibrate(25)

        resolveTurn()
    }

    private suspend fun resolveTurn() {
        val game = _state.value.game
        if (game.isOver) return

        // Three sixes in a row void the roll and the turn has already moved on.
        if (game.dice == null) {
            delay(PASS_PAUSE_MS)
            return
        }

        val moves = game.legalMoves
        when {
            moves.isEmpty() -> {
                _state.update { it.copy(message = "${it.currentColor.displayName} has no move") }
                delay(PASS_PAUSE_MS)
                val skipped = game.skipTurn()
                _state.update {
                    it.copy(game = skipped, message = skipped.lastEvents.lastOrNull()?.message ?: it.message)
                }
            }
            // Nothing to decide, so play it rather than making the player tap.
            moves.size == 1 -> {
                delay(AUTO_MOVE_PAUSE_MS)
                applyMove(moves.first())
            }
            game.currentPlayer.isBot -> {
                delay(BOT_THINK_MS)
                LudoBot.chooseMove(game)?.let { applyMove(it) }
            }
            else -> {
                _state.update { it.copy(message = "${it.currentColor.displayName}: pick a token") }
            }
        }
    }

    private suspend fun applyMove(move: LudoMove) {
        val played = _state.value.game.play(move)
        if (played === _state.value.game) return

        _state.update {
            it.copy(
                game = played,
                lastMove = move,
                message = played.lastEvents.joinToString(" · ") { event -> event.message }
                    .ifEmpty { it.message },
                resultDismissed = if (played.isOver) false else it.resultDismissed
            )
        }
        announce(played.lastEvents.map { it.kind })

        // An extra turn for a human stops here: they roll again when they are ready.
        if (!played.isOver && played.dice == null && played.currentPlayer.isBot) {
            delay(SHORT_PAUSE_MS)
        }
    }

    // endregion

    // region Feedback

    private fun announce(kinds: List<LudoEventKind>) {
        when {
            LudoEventKind.GAME_OVER in kinds -> {
                vibrate(400)
                playTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)
            }
            LudoEventKind.PLAYER_HOME in kinds -> {
                vibrate(250)
                playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
            }
            LudoEventKind.CAPTURED in kinds -> {
                vibrate(120)
                playTone(ToneGenerator.TONE_CDMA_HIGH_L, 180)
            }
            LudoEventKind.TOKEN_HOME in kinds -> {
                vibrate(90)
                playTone(ToneGenerator.TONE_PROP_BEEP2, 150)
            }
            LudoEventKind.THREE_SIXES in kinds || LudoEventKind.NO_MOVES in kinds -> {
                playTone(ToneGenerator.TONE_PROP_NACK, 150)
            }
            LudoEventKind.MOVED in kinds -> playTone(ToneGenerator.TONE_PROP_BEEP, 60)
            LudoEventKind.ROLLED in kinds -> playTone(ToneGenerator.TONE_PROP_PROMPT, 80)
        }
    }

    private fun vibrate(durationMs: Long) {
        if (!_state.value.vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Feedback is optional; never let it break a turn.
        }
    }

    private fun playTone(tone: Int, durationMs: Int) {
        if (!_state.value.soundEnabled) return
        try {
            toneGenerator?.startTone(tone, durationMs)
        } catch (e: Exception) {
            // Feedback is optional; never let it break a turn.
        }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        turnJob?.cancel()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Nothing useful to do while tearing down.
        }
    }

    companion object {
        private const val DICE_FLICKER_FRAMES = 8
        private const val DICE_FLICKER_MS = 55L
        private const val AUTO_MOVE_PAUSE_MS = 320L
        private const val BOT_THINK_MS = 480L
        private const val BOT_PAUSE_MS = 600L
        private const val PASS_PAUSE_MS = 750L
        private const val SHORT_PAUSE_MS = 250L
    }
}
