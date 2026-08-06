package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.ChessBot
import com.example.chess.ChessGame
import com.example.chess.Move
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.Square
import com.example.data.AppDatabase
import com.example.data.TimePreset
import com.example.data.TimePresetRepository
import com.example.model.ChessGameUiState
import com.example.model.ChessOpponent
import com.example.model.GameTimeControl
import com.example.model.PendingPromotion
import com.example.model.TimingStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives a real game of chess played on the device, with the same time controls the physical
 * clock mode offers. The rules live in [com.example.chess]; this class owns the clock, the
 * selection state and the feedback effects.
 */
class ChessGameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = TimePresetRepository(db.timePresetDao())

    /** The stored presets, offered as time controls when starting a new game. */
    val timeControls: StateFlow<List<GameTimeControl>> = repository.allPresets
        .map { presets -> listOf(GameTimeControl.UNLIMITED) + presets.map { it.toTimeControl() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = listOf(GameTimeControl.UNLIMITED)
        )

    private val _state = MutableStateFlow(ChessGameUiState())
    val state: StateFlow<ChessGameUiState> = _state.asStateFlow()

    /** Clock readings after each half-move, so undo can rewind time as well as the board. */
    private val clockHistory = mutableListOf(
        GameTimeControl.DEFAULT.initialTimeMs to GameTimeControl.DEFAULT.initialTimeMs
    )

    private var timerJob: Job? = null

    /** The computer's search, running off the main thread while the board waits for it. */
    private var botJob: Job? = null

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

    // region Game lifecycle

    fun newGame(
        timeControl: GameTimeControl = _state.value.timeControl,
        opponent: ChessOpponent = _state.value.opponent
    ) {
        stopClock()
        cancelComputerMove()
        val startTime = if (timeControl.isUnlimited) 0L else timeControl.initialTimeMs
        clockHistory.clear()
        clockHistory.add(startTime to startTime)
        _state.update { current ->
            ChessGameUiState(
                game = ChessGame.new(),
                timeControl = timeControl,
                opponent = opponent,
                whiteTimeMs = startTime,
                blackTimeMs = startTime,
                delayBufferMs = initialDelayFor(timeControl),
                clockRunning = false,
                clockStarted = false,
                // Facing the computer, the player always sits behind their own pieces.
                boardFlipped = opponent.botColor == PieceColor.WHITE,
                autoFlip = current.autoFlip && !opponent.isComputer,
                soundEnabled = current.soundEnabled,
                vibrationEnabled = current.vibrationEnabled
            )
        }
        vibrate(30)
        maybeStartComputerMove()
    }

    fun selectTimeControl(timeControl: GameTimeControl) = newGame(timeControl)

    // endregion

    // region Board interaction

    fun onSquareTapped(square: Int) {
        val current = _state.value
        if (current.isOver || current.pendingPromotion != null || current.isPaused) return
        // The computer's pieces are not the player's to move.
        if (current.isComputerToMove) return
        if (!Square.isValid(square)) return

        if (current.selectedSquare == square) {
            clearSelection()
            return
        }

        if (current.selectedSquare != Square.NONE) {
            val candidates = current.position.legalMoves()
                .filter { it.from == current.selectedSquare && it.to == square }
            if (candidates.isNotEmpty()) {
                val promotion = candidates.firstOrNull { it.promotion != null }
                if (promotion != null) {
                    _state.update {
                        it.copy(
                            pendingPromotion = PendingPromotion(
                                from = current.selectedSquare,
                                to = square,
                                color = current.sideToMove
                            )
                        )
                    }
                } else {
                    applyMove(candidates.first())
                }
                return
            }
        }

        val piece = current.position.pieceAt(square)
        if (piece != null && piece.color == current.sideToMove) {
            _state.update {
                it.copy(
                    selectedSquare = square,
                    legalTargets = current.position.legalMovesFrom(square).map { m -> m.to }.toSet()
                )
            }
        } else {
            clearSelection()
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedSquare = Square.NONE, legalTargets = emptySet()) }
    }

    fun choosePromotion(type: PieceType) {
        val pending = _state.value.pendingPromotion ?: return
        val move = _state.value.position.findLegalMove(pending.from, pending.to, type)
        _state.update { it.copy(pendingPromotion = null) }
        if (move != null) applyMove(move)
    }

    fun cancelPromotion() {
        _state.update { it.copy(pendingPromotion = null) }
        clearSelection()
    }

    private fun applyMove(move: Move) {
        val played = _state.value.game.play(move) ?: return
        val mover = _state.value.sideToMove

        // The mover's clock stops the moment the piece lands, before anything else is worked out.
        stopClock()

        var snapshot = _state.value.whiteTimeMs to _state.value.blackTimeMs
        var clockShouldRun = false

        _state.update { state ->
            val bonus = if (!state.timeControl.isUnlimited &&
                state.timeControl.timingStyle == TimingStyle.INCREMENT
            ) {
                state.timeControl.incrementSeconds * 1_000L
            } else {
                0L
            }
            val whiteTime = state.whiteTimeMs + if (mover == PieceColor.WHITE) bonus else 0L
            val blackTime = state.blackTimeMs + if (mover == PieceColor.BLACK) bonus else 0L
            snapshot = whiteTime to blackTime
            clockShouldRun = !state.timeControl.isUnlimited && !played.isOver

            state.copy(
                game = played,
                whiteTimeMs = whiteTime,
                blackTimeMs = blackTime,
                delayBufferMs = initialDelayFor(state.timeControl),
                clockRunning = clockShouldRun,
                clockStarted = state.clockStarted || !state.timeControl.isUnlimited,
                selectedSquare = Square.NONE,
                legalTargets = emptySet(),
                boardFlipped = if (state.autoFlip) {
                    played.sideToMove == PieceColor.BLACK
                } else {
                    state.boardFlipped
                },
                resultDismissed = false
            )
        }

        clockHistory.add(snapshot)
        announceMove(played, move)

        if (clockShouldRun) restartClockLoop()
        maybeStartComputerMove()
    }

    // endregion

    // region Computer opponent

    /**
     * Hands the position to [ChessBot] when it is the computer's turn. The search runs off the main
     * thread and its move is only played if the board it was given is still the board on screen —
     * so a take-back or a new game while it thinks simply discards the result.
     */
    private fun maybeStartComputerMove() {
        val current = _state.value
        if (!current.isComputerToMove || current.isPaused || current.pendingPromotion != null) return

        val game = current.game
        val level = current.opponent.level
        val budget = thinkingBudgetFor(current)
        botJob?.cancel()
        _state.update {
            it.copy(isThinking = true, selectedSquare = Square.NONE, legalTargets = emptySet())
        }
        botJob = viewModelScope.launch(Dispatchers.Default) {
            val startedAt = SystemClock.elapsedRealtime()
            val move = ChessBot.chooseMove(game, level, timeBudgetMs = budget)
            // A move that lands the instant the player lets go reads as a glitch rather than a reply.
            val pause = minOf(MIN_THINK_MS, budget)
            delay((pause - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L))

            _state.update { it.copy(isThinking = false) }
            // Anything that moved the game on while the search ran wins: its move is stale.
            if (move != null && _state.value.game === game) applyMove(move)
        }
    }

    /**
     * How long the computer may think for: its level's own budget, cut down to a share of the clock
     * it is playing on. A one-minute game gets a couple of seconds' thought on the first move and
     * less on every move after it, so the computer plays shallower rather than losing on time.
     */
    private fun thinkingBudgetFor(state: ChessGameUiState): Long {
        val level = state.opponent.level
        if (state.timeControl.isUnlimited) return level.timeBudgetMs
        val remaining = state.timeFor(state.opponent.computerColor)
        val increment = when (state.timeControl.timingStyle) {
            TimingStyle.INCREMENT -> state.timeControl.incrementSeconds * 1_000L
            TimingStyle.DELAY -> state.timeControl.delaySeconds * 1_000L
            TimingStyle.NONE -> 0L
        }
        return (remaining / MOVES_TO_BUDGET_FOR + increment * 4 / 5)
            .coerceIn(MIN_BUDGET_MS, level.timeBudgetMs)
    }

    private fun cancelComputerMove() {
        botJob?.cancel()
        botJob = null
        if (_state.value.isThinking) _state.update { it.copy(isThinking = false) }
    }

    /** Test hook: waits for a move the computer is working on, if there is one. */
    internal suspend fun awaitComputerMove() {
        botJob?.join()
    }

    // endregion

    // region Controls

    fun undo() {
        cancelComputerMove()
        if (!undoOnce()) return
        // Against the computer, taking back one half-move would only hand the position straight
        // back to it, so its reply comes off too.
        if (_state.value.isComputerToMove && _state.value.game.moveCount > 0) undoOnce()
        vibrate(25)
        // Unless the computer has White and the board is back to the start, where it is on move.
        maybeStartComputerMove()
    }

    /** Steps back one half-move — or one declared result. False when there was nothing to undo. */
    private fun undoOnce(): Boolean {
        val current = _state.value
        val previous = current.game.undo() ?: return false
        stopClock()
        // Taking back a declared result leaves the move list — and so the clocks — untouched.
        val movesWereTakenBack = previous.moveCount < current.game.moveCount
        if (movesWereTakenBack && clockHistory.size > 1) {
            clockHistory.removeAt(clockHistory.size - 1)
        }
        val (whiteTime, blackTime) = clockHistory.last()
        _state.update {
            it.copy(
                game = previous,
                whiteTimeMs = whiteTime,
                blackTimeMs = blackTime,
                delayBufferMs = initialDelayFor(it.timeControl),
                clockRunning = false,
                clockStarted = it.clockStarted && previous.moveCount > 0,
                selectedSquare = Square.NONE,
                legalTargets = emptySet(),
                pendingPromotion = null,
                boardFlipped = if (it.autoFlip) previous.sideToMove == PieceColor.BLACK else it.boardFlipped,
                resultDismissed = false
            )
        }
        return true
    }

    fun pauseClock() {
        if (!_state.value.clockRunning) return
        stopClock()
        cancelComputerMove()
        _state.update { it.copy(clockRunning = false) }
        vibrate(40)
    }

    fun resumeClock() {
        val current = _state.value
        if (current.isOver || current.timeControl.isUnlimited) return
        if (!current.clockStarted || current.clockRunning) return
        _state.update { it.copy(clockRunning = true) }
        restartClockLoop()
        maybeStartComputerMove()
    }

    fun toggleClock() {
        if (_state.value.clockRunning) pauseClock() else resumeClock()
    }

    fun resign(color: PieceColor) {
        val current = _state.value
        if (current.isOver) return
        stopClock()
        cancelComputerMove()
        _state.update {
            it.copy(game = it.game.resign(color), clockRunning = false, resultDismissed = false)
        }
        signalGameOver()
    }

    fun agreeDraw() {
        val current = _state.value
        if (current.isOver) return
        stopClock()
        cancelComputerMove()
        _state.update {
            it.copy(game = it.game.agreeDraw(), clockRunning = false, resultDismissed = false)
        }
        signalGameOver()
    }

    fun flipBoard() {
        _state.update { it.copy(boardFlipped = !it.boardFlipped) }
    }

    fun toggleAutoFlip() {
        _state.update {
            val enabled = !it.autoFlip
            it.copy(
                autoFlip = enabled,
                boardFlipped = if (enabled) it.sideToMove == PieceColor.BLACK else it.boardFlipped
            )
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

    // region Clock

    private fun initialDelayFor(timeControl: GameTimeControl): Long =
        if (!timeControl.isUnlimited && timeControl.timingStyle == TimingStyle.DELAY) {
            timeControl.delaySeconds * 1_000L
        } else {
            0L
        }

    private fun restartClockLoop() {
        stopClock()
        var lastTick = SystemClock.elapsedRealtime()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastTick
                lastTick = now

                var flagged: PieceColor? = null
                var lowTimePip = false

                _state.update { current ->
                    // Reset per-attempt: MutableStateFlow.update may re-run this block.
                    flagged = null
                    lowTimePip = false
                    if (!current.clockRunning || current.isOver || current.timeControl.isUnlimited) {
                        return@update current
                    }

                    val mover = current.sideToMove
                    var whiteTime = current.whiteTimeMs
                    var blackTime = current.blackTimeMs
                    var buffer = current.delayBufferMs
                    var drain = elapsed

                    if (buffer > 0L) {
                        val used = minOf(buffer, drain)
                        buffer -= used
                        drain -= used
                    }

                    if (drain > 0L) {
                        if (mover == PieceColor.WHITE) whiteTime -= drain else blackTime -= drain
                    }

                    val before = if (mover == PieceColor.WHITE) current.whiteTimeMs else current.blackTimeMs
                    val after = if (mover == PieceColor.WHITE) whiteTime else blackTime
                    if (after in 1L until LOW_TIME_MS && before / 1000L != after / 1000L) {
                        lowTimePip = true
                    }

                    if (whiteTime <= 0L) {
                        whiteTime = 0L
                        flagged = PieceColor.WHITE
                    }
                    if (blackTime <= 0L) {
                        blackTime = 0L
                        flagged = PieceColor.BLACK
                    }

                    val loser = flagged
                    current.copy(
                        whiteTimeMs = whiteTime,
                        blackTimeMs = blackTime,
                        delayBufferMs = buffer,
                        game = if (loser != null) current.game.flag(loser) else current.game,
                        clockRunning = loser == null,
                        resultDismissed = if (loser != null) false else current.resultDismissed
                    )
                }

                if (flagged != null) {
                    signalGameOver()
                    break
                }
                if (lowTimePip) playTone(ToneGenerator.TONE_CDMA_PIP, 60)
            }
        }
    }

    private fun stopClock() {
        timerJob?.cancel()
        timerJob = null
    }

    // endregion

    // region Feedback

    private fun announceMove(game: ChessGame, move: Move) {
        vibrate(if (move.isCapture) 45L else 25L)
        when {
            game.isOver -> signalGameOver()
            game.isCheck -> playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
            move.isCapture -> playTone(ToneGenerator.TONE_PROP_BEEP2, 90)
            else -> playTone(ToneGenerator.TONE_PROP_BEEP, 60)
        }
    }

    private fun signalGameOver() {
        vibrate(320)
        playTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400)
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
            // Feedback is optional; never let it break a move.
        }
    }

    private fun playTone(tone: Int, durationMs: Int) {
        if (!_state.value.soundEnabled) return
        try {
            toneGenerator?.startTone(tone, durationMs)
        } catch (e: Exception) {
            // Feedback is optional; never let it break a move.
        }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        stopClock()
        botJob?.cancel()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Nothing useful to do while tearing down.
        }
    }

    companion object {
        private const val TICK_MS = 40L
        private const val LOW_TIME_MS = 10_000L

        /** The shortest a computer move may take, so replies do not appear instantly. */
        private const val MIN_THINK_MS = 350L

        /** The clock is spread over this many moves when working out how long to think. */
        private const val MOVES_TO_BUDGET_FOR = 30L

        private const val MIN_BUDGET_MS = 120L
    }
}

private fun TimePreset.toTimeControl(): GameTimeControl = GameTimeControl(
    label = name,
    initialTimeMs = initialTimeMs,
    timingStyle = try {
        TimingStyle.valueOf(timingStyle)
    } catch (e: IllegalArgumentException) {
        TimingStyle.NONE
    },
    incrementSeconds = incrementSeconds,
    delaySeconds = delaySeconds
)
