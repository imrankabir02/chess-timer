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
import com.example.model.ChessSetup
import com.example.model.GameTimeControl
import com.example.model.PendingPromotion
import com.example.model.TimingStyle
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

/**
 * Drives a real game of chess played on the device, against another player or against the computer,
 * with the same time controls the physical clock mode offers. The rules and the engine live in
 * [com.example.chess]; this class owns the clock, the selection state, the pacing of the computer
 * opponent and the feedback effects.
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

    /** The opponent's engine, rebuilt whenever a game starts so a level change takes effect. */
    private var bot: ChessBot? = null
    private var botJob: Job? = null

    /** Test hooks: a fixed seed makes the opponent reproducible, and the search can run inline. */
    private var botRandom: Random = Random.Default
    internal var botSearchContext: CoroutineContext = Dispatchers.Default

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
        setup: ChessSetup = _state.value.setup
    ) {
        stopClock()
        botJob?.cancel()
        val startTime = if (timeControl.isUnlimited) 0L else timeControl.initialTimeMs
        // A RANDOM side is settled here, once, so it does not flip about mid-game.
        val dealt = setup.dealt(botRandom)
        bot = if (dealt.vsComputer) ChessBot(dealt.difficulty, botRandom) else null
        clockHistory.clear()
        clockHistory.add(startTime to startTime)
        _state.update { current ->
            ChessGameUiState(
                game = ChessGame.new(),
                setup = dealt,
                timeControl = timeControl,
                whiteTimeMs = startTime,
                blackTimeMs = startTime,
                delayBufferMs = initialDelayFor(timeControl),
                clockRunning = false,
                clockStarted = false,
                // Playing Black against the computer means sitting behind the black pieces.
                boardFlipped = dealt.vsComputer && dealt.humanColor == PieceColor.BLACK,
                // Passing the device back and forth makes no sense with only one player at it.
                autoFlip = current.autoFlip && !dealt.vsComputer,
                soundEnabled = current.soundEnabled,
                vibrationEnabled = current.vibrationEnabled,
                setupSeen = true
            )
        }
        vibrate(30)
        startComputerTurnIfNeeded()
    }

    fun selectTimeControl(timeControl: GameTimeControl) = newGame(timeControl)

    /** Records that the setup has been offered, so it is not put in front of the player again. */
    fun markSetupSeen() {
        _state.update { it.copy(setupSeen = true) }
    }

    /** Test hook: makes the computer opponent reproducible. Takes effect from the next game. */
    internal fun useBotRandom(random: Random) {
        botRandom = random
    }

    // endregion

    // region Board interaction

    fun onSquareTapped(square: Int) {
        val current = _state.value
        if (current.isOver || current.pendingPromotion != null || current.isPaused) return
        // The board belongs to the computer while it is on move.
        if (current.isComputerTurn) return
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
                notice = null,
                resultDismissed = false
            )
        }

        clockHistory.add(snapshot)
        announceMove(played, move)

        if (clockShouldRun) restartClockLoop()
        startComputerTurnIfNeeded()
    }

    // endregion

    // region The computer opponent

    /**
     * Hands the move to the computer when it is its turn. The search runs off the main thread and
     * its result is only applied if the game has not moved on in the meantime — a take-back or a new
     * game while it is thinking simply discards the move it was working out.
     */
    private fun startComputerTurnIfNeeded() {
        val current = _state.value
        val engine = bot ?: return
        // Checked before cancelling: this also runs from inside the bot's own coroutine, once it has
        // played, and must not cancel the job it is standing in.
        if (!current.isComputerTurn || current.pendingPromotion != null) return
        botJob?.cancel()

        val gameWhenAsked = current.game
        botJob = viewModelScope.launch(CoroutineName("chess-bot")) {
            _state.update { it.copy(computerThinking = true) }
            try {
                // A move that lands the instant you let go of your own piece feels like a glitch.
                delay(THINKING_PAUSE_MS)
                val move = withContext(botSearchContext) { engine.chooseMove(gameWhenAsked) }
                coroutineContext.ensureActive()
                if (_state.value.game === gameWhenAsked && move != null) {
                    applyMove(move)
                }
            } finally {
                // update() runs even on cancellation, so the board never stays stuck on "thinking".
                _state.update { it.copy(computerThinking = false) }
            }
        }
    }

    /** Drops whatever the computer was working out — after a take-back, or when the game ends. */
    private fun stopComputerTurn() {
        botJob?.cancel()
        botJob = null
        _state.update { it.copy(computerThinking = false) }
    }

    // endregion

    // region Controls

    fun undo() {
        val current = _state.value
        if (!current.canUndo) return
        stopComputerTurn()

        // A declared result — a resignation, a flag fall — is taken back on its own first. Only
        // after that do further presses start rewinding the board.
        var previous = current.game.undo() ?: return
        if (previous.moveCount < current.game.moveCount) {
            // Against the computer, stopping on its move would just hand it the move straight back,
            // so a take-back steps past its reply as well.
            repeat(current.undoPlies - 1) {
                previous = previous.undo() ?: previous
            }
        }

        stopClock()
        // Taking back a declared result leaves the move list — and so the clocks — untouched.
        val pliesTakenBack = current.game.moveCount - previous.moveCount
        repeat(pliesTakenBack) {
            if (clockHistory.size > 1) clockHistory.removeAt(clockHistory.size - 1)
        }
        val (whiteTime, blackTime) = clockHistory.last()
        val rewoundGame = previous
        _state.update {
            it.copy(
                game = rewoundGame,
                whiteTimeMs = whiteTime,
                blackTimeMs = blackTime,
                delayBufferMs = initialDelayFor(it.timeControl),
                clockRunning = false,
                clockStarted = it.clockStarted && rewoundGame.moveCount > 0,
                selectedSquare = Square.NONE,
                legalTargets = emptySet(),
                pendingPromotion = null,
                boardFlipped = if (it.autoFlip) {
                    rewoundGame.sideToMove == PieceColor.BLACK
                } else {
                    it.boardFlipped
                },
                notice = null,
                resultDismissed = false
            )
        }
        vibrate(25)

        // Taking back only a declared result can leave the computer on move again.
        if (pliesTakenBack == 0) startComputerTurnIfNeeded()
    }

    fun pauseClock() {
        if (!_state.value.clockRunning) return
        stopClock()
        _state.update { it.copy(clockRunning = false) }
        vibrate(40)
    }

    fun resumeClock() {
        val current = _state.value
        if (current.isOver || current.timeControl.isUnlimited) return
        if (!current.clockStarted || current.clockRunning) return
        _state.update { it.copy(clockRunning = true) }
        restartClockLoop()
    }

    fun toggleClock() {
        if (_state.value.clockRunning) pauseClock() else resumeClock()
    }

    fun resign(color: PieceColor) {
        val current = _state.value
        if (current.isOver) return
        stopClock()
        stopComputerTurn()
        _state.update {
            it.copy(
                game = it.game.resign(color),
                clockRunning = false,
                notice = null,
                resultDismissed = false
            )
        }
        signalGameOver()
    }

    fun agreeDraw() {
        if (_state.value.isOver) return
        stopComputerTurn()
        finishWithAgreedDraw()
    }

    /** The draw itself, without touching the computer's job — the offer flow is already inside it. */
    private fun finishWithAgreedDraw() {
        if (_state.value.isOver) return
        stopClock()
        _state.update {
            it.copy(
                game = it.game.agreeDraw(),
                clockRunning = false,
                notice = null,
                resultDismissed = false
            )
        }
        signalGameOver()
    }

    /**
     * A draw offer. Between two players it is simply agreed — they have already talked it over. The
     * computer weighs the position up first and says no when it would rather play on.
     */
    fun offerDraw() {
        val current = _state.value
        if (current.isOver) return
        val engine = bot
        val computerColor = current.setup.computerColor
        if (engine == null || computerColor == null) {
            agreeDraw()
            return
        }

        botJob?.cancel()
        botJob = viewModelScope.launch(CoroutineName("chess-bot-draw")) {
            val gameWhenAsked = current.game
            _state.update { it.copy(computerThinking = true, notice = null) }
            val accepted = try {
                withContext(botSearchContext) { engine.acceptsDrawOffer(gameWhenAsked, computerColor) }
            } finally {
                _state.update { it.copy(computerThinking = false) }
            }
            coroutineContext.ensureActive()
            if (_state.value.game !== gameWhenAsked) return@launch

            if (accepted) {
                finishWithAgreedDraw()
            } else {
                _state.update { it.copy(notice = "The computer declines — it wants to play on.") }
                playTone(ToneGenerator.TONE_PROP_NACK, 150)
                // The offer cost the player nothing but the time it took to make it.
                startComputerTurnIfNeeded()
            }
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
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
                    stopComputerTurn()
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

        /** A beat before the computer replies, so its move does not land on top of yours. */
        private const val THINKING_PAUSE_MS = 350L
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
