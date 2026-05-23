package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TimePreset
import com.example.data.TimePresetRepository
import com.example.model.ChessTimerState
import com.example.model.GameStatus
import com.example.model.Player
import com.example.model.TimingStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChessTimerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = TimePresetRepository(db.timePresetDao())

    val presets: StateFlow<List<TimePreset>> = repository.allPresets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _timerState = MutableStateFlow(ChessTimerState())
    val timerState: StateFlow<ChessTimerState> = _timerState.asStateFlow()

    private var timerJob: Job? = null
    private val toneGenerator = try {
        ToneGenerator(AudioManager.STREAM_ALARM, 70)
    } catch (e: Exception) {
        null
    }

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Temporary storage for custom input during user creation
    private val _selectedPresetId = MutableStateFlow<Int?>(null)
    val selectedPresetId: StateFlow<Int?> = _selectedPresetId.asStateFlow()

    init {
        // Find default or first preset when presets load, and reset to it
        viewModelScope.launch {
            presets.collect { list ->
                if (list.isNotEmpty() && _timerState.value.gameStatus == GameStatus.SETUP) {
                    val target = list.firstOrNull { it.initialTimeMs == 180_000L && it.timingStyle == "INCREMENT" && it.incrementSeconds == 2 }
                        ?: list.firstOrNull { it.initialTimeMs == 300_000L }
                        ?: list.first()
                    _selectedPresetId.value = target.id
                    applyPresetState(target)
                }
            }
        }
    }

    fun selectPreset(preset: TimePreset) {
        _selectedPresetId.value = preset.id
        applyPresetState(preset)
    }

    private fun applyPresetState(preset: TimePreset) {
        stopTimerJob()
        val timingStyleEnum = try {
            TimingStyle.valueOf(preset.timingStyle)
        } catch (e: Exception) {
            TimingStyle.NONE
        }

        _timerState.update {
            it.copy(
                playerATimeMs = preset.initialTimeMs,
                playerBTimeMs = preset.initialTimeMs,
                activePlayer = null,
                gameStatus = GameStatus.READY,
                playerAMoveCount = 0,
                playerBMoveCount = 0,
                isWhiteActive = true,
                delayBufferMs = if (timingStyleEnum == TimingStyle.DELAY) preset.delaySeconds * 1000L else 0L,
                timingStyle = timingStyleEnum,
                initialTimeMs = preset.initialTimeMs,
                incrementSeconds = preset.incrementSeconds,
                delaySeconds = preset.delaySeconds,
                winner = null
            )
        }
    }

    fun createAndSelectCustomPreset(
        name: String,
        initialTimeMs: Long,
        style: TimingStyle,
        incrementSec: Int,
        delaySec: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val label = if (name.isNotBlank()) name else {
                val min = initialTimeMs / 60_000
                val sec = (initialTimeMs % 60_000) / 1000
                val tag = when (style) {
                    TimingStyle.NONE -> ""
                    TimingStyle.INCREMENT -> "+$incrementSec"
                    TimingStyle.DELAY -> " d$delaySec"
                }
                if (sec > 0) "$min:$sec$tag" else "$min min$tag"
            }
            val newPreset = TimePreset(
                name = label,
                initialTimeMs = initialTimeMs,
                timingStyle = style.name,
                incrementSeconds = incrementSec,
                delaySeconds = delaySec,
                isCustom = true
            )
            val insertId = repository.insert(newPreset)
            viewModelScope.launch(Dispatchers.Main) {
                // Instantly select the inserted custom preset
                _selectedPresetId.value = insertId.toInt()
                applyPresetState(newPreset.copy(id = insertId.toInt()))
            }
        }
    }

    fun deletePreset(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCustom(id)
            // If the deleted preset was active, go back to first standard preset
            viewModelScope.launch(Dispatchers.Main) {
                if (_selectedPresetId.value == id) {
                    presets.value.firstOrNull { !it.isCustom }?.let {
                        selectPreset(it)
                    }
                }
            }
        }
    }

    fun onPlayerTap(player: Player) {
        val state = _timerState.value
        
        // Prevent clicks if game is finished
        if (state.gameStatus == GameStatus.FLAG_FALL) return

        // Sound and vibrate feedback on actual clicks
        vibrateFeedback(50)
        playBeep(ToneGenerator.TONE_PROP_BEEP2)

        when (state.gameStatus) {
            GameStatus.SETUP, GameStatus.READY -> {
                // Clicking your button starts the OTHER player's timer
                val targetPlayer = if (player == Player.PLAYER_A) Player.PLAYER_B else Player.PLAYER_A
                startTimer(targetPlayer)
                return
            }
            GameStatus.PAUSED -> {
                // Resume game, ticking the clicked player's opponent
                val targetPlayer = if (player == Player.PLAYER_A) Player.PLAYER_B else Player.PLAYER_A
                startTimer(targetPlayer)
                return
            }
            GameStatus.RUNNING -> {
                if (state.activePlayer == player) {
                    // Turn finished for this player. 
                    // 1. Give incentive time if necessary (Fischer Increment style)
                    var incentiveTime = 0L
                    if (state.timingStyle == TimingStyle.INCREMENT) {
                        incentiveTime = state.incrementSeconds * 1000L
                    }

                    _timerState.update {
                        val updatedTimeA = if (player == Player.PLAYER_A) {
                            it.playerATimeMs + incentiveTime
                        } else {
                            it.playerATimeMs
                        }

                        val updatedTimeB = if (player == Player.PLAYER_B) {
                            it.playerBTimeMs + incentiveTime
                        } else {
                            it.playerBTimeMs
                        }

                        val moveCountA = if (player == Player.PLAYER_A) it.playerAMoveCount + 1 else it.playerAMoveCount
                        val moveCountB = if (player == Player.PLAYER_B) it.playerBMoveCount + 1 else it.playerBMoveCount

                        val targetNextPlayer = if (player == Player.PLAYER_A) Player.PLAYER_B else Player.PLAYER_A
                        
                        it.copy(
                            playerATimeMs = updatedTimeA,
                            playerBTimeMs = updatedTimeB,
                            playerAMoveCount = moveCountA,
                            playerBMoveCount = moveCountB,
                            activePlayer = targetNextPlayer,
                            delayBufferMs = if (it.timingStyle == TimingStyle.DELAY) it.delaySeconds * 1000L else 0L
                        )
                    }
                }
            }
            GameStatus.FLAG_FALL -> {}
        }
    }

    fun pauseGame() {
        if (_timerState.value.gameStatus == GameStatus.RUNNING) {
            stopTimerJob()
            _timerState.update { it.copy(gameStatus = GameStatus.PAUSED) }
            vibrateFeedback(70)
        }
    }

    fun resumeGame() {
        val state = _timerState.value
        if (state.gameStatus == GameStatus.PAUSED) {
            val target = state.activePlayer ?: Player.PLAYER_B
            startTimer(target)
        }
    }

    fun resetGame() {
        stopTimerJob()
        _timerState.update {
            it.copy(
                playerATimeMs = it.initialTimeMs,
                playerBTimeMs = it.initialTimeMs,
                activePlayer = null,
                gameStatus = GameStatus.READY,
                playerAMoveCount = 0,
                playerBMoveCount = 0,
                delayBufferMs = if (it.timingStyle == TimingStyle.DELAY) it.delaySeconds * 1000L else 0L,
                winner = null
            )
        }
        vibrateFeedback(30)
    }

    fun toggleSound() {
        _timerState.update { it.copy(soundEnabled = !it.soundEnabled) }
    }

    fun toggleVibration() {
        _timerState.update { it.copy(vibrationEnabled = !it.vibrationEnabled) }
    }

    private fun startTimer(player: Player) {
        stopTimerJob()
        
        _timerState.update {
            it.copy(
                activePlayer = player,
                gameStatus = GameStatus.RUNNING,
                delayBufferMs = if (it.timingStyle == TimingStyle.DELAY) it.delaySeconds * 1000L else 0L
            )
        }

        var lastTickTime = SystemClock.elapsedRealtime()

        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(40) // Check state update frequently for fluid display updates
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastTickTime
                lastTickTime = now

                var flagFell = false
                var soundToPlay: Int? = null

                _timerState.update { state ->
                    if (state.gameStatus != GameStatus.RUNNING || state.activePlayer == null) {
                        return@update state
                    }

                    var timeA = state.playerATimeMs
                    var timeB = state.playerBTimeMs
                    var buffer = state.delayBufferMs

                    // Handle Simple Delay counting down first
                    if (state.timingStyle == TimingStyle.DELAY && buffer > 0L) {
                        buffer -= delta
                        if (buffer < 0L) {
                            // If buffer went below zero, apply leftover delta to main clock
                            val overflow = -buffer
                            buffer = 0L
                            if (state.activePlayer == Player.PLAYER_A) {
                                timeA -= overflow
                            } else {
                                timeB -= overflow
                            }
                        }
                    } else {
                        // Regular counting down
                        if (state.activePlayer == Player.PLAYER_A) {
                            timeA -= delta
                        } else {
                            timeB -= delta
                        }
                    }

                    // Check bounds & trigger alert thresholds
                    if (timeA <= 0L) {
                        timeA = 0L
                        flagFell = true
                    }
                    if (timeB <= 0L) {
                        timeB = 0L
                        flagFell = true
                    }

                    // Periodically play ticking or alarm sound if time is low
                    val activeTime = if (state.activePlayer == Player.PLAYER_A) timeA else timeB
                    if (activeTime < 10_000L && activeTime > 0L) {
                        // Beep every second on low time
                        val lastSec = ((activeTime + delta) / 1000).toInt()
                        val currentSec = (activeTime / 1000).toInt()
                        if (lastSec != currentSec) {
                            soundToPlay = ToneGenerator.TONE_CDMA_PIP
                        }
                    }

                    state.copy(
                        playerATimeMs = timeA,
                        playerBTimeMs = timeB,
                        delayBufferMs = buffer,
                        gameStatus = if (flagFell) GameStatus.FLAG_FALL else state.gameStatus,
                        winner = if (flagFell) {
                            if (timeA <= 0L) Player.PLAYER_B else Player.PLAYER_A
                        } else null
                    )
                }

                // Side-effect behaviors in Thread loop
                if (flagFell) {
                    viewModelScope.launch(Dispatchers.Main) {
                        vibrateFeedback(400)
                        playBeep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD)
                    }
                    break
                }

                soundToPlay?.let { s ->
                    viewModelScope.launch(Dispatchers.Main) {
                        playBeep(s)
                    }
                }
            }
        }
    }

    private fun stopTimerJob() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun vibrateFeedback(durationMs: Long) {
        if (!_timerState.value.vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun playBeep(toneType: Int) {
        if (!_timerState.value.soundEnabled) return
        try {
            toneGenerator?.startTone(toneType, 120)
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimerJob()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Safe fallback
        }
    }
}
