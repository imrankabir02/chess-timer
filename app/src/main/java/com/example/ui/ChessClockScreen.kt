package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.TimePreset
import com.example.model.ChessTimerState
import com.example.model.GameStatus
import com.example.model.Player
import com.example.model.TimingStyle
import com.example.ui.theme.GeometricActiveCyan
import com.example.ui.theme.GeometricActiveOnCyan
import com.example.ui.theme.GeometricDarkBackground
import com.example.ui.theme.GeometricInactivePlayer
import com.example.ui.theme.GeometricSurfaceDark
import com.example.viewmodel.ChessTimerViewModel

@Composable
fun ChessClockScreen(
    viewModel: ChessTimerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.timerState.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val activePresetId by viewModel.selectedPresetId.collectAsState()

    var showCustomConfig by remember { mutableStateOf(false) }
    var showPresetsOverview by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GeometricDarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // --- TOP PLAYER (PLAYER A) - Rotated 180 ---
            val isPlayerAActive = state.activePlayer == Player.PLAYER_A
            PlayerClockSegment(
                playerName = "Player One",
                timeMs = state.playerATimeMs,
                moveCount = state.playerAMoveCount,
                timingStyle = state.timingStyle,
                incrementSeconds = state.incrementSeconds,
                delaySeconds = state.delaySeconds,
                isActive = isPlayerAActive,
                delayBufferMs = if (isPlayerAActive) state.delayBufferMs else 0L,
                gameStatus = state.gameStatus,
                isRotated = true,
                onTap = { viewModel.onPlayerTap(Player.PLAYER_A) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("player_a_box")
            )

            // --- REGULATION CONTROL BAR (MIDDLE) ---
            MiddleControlBar(
                state = state,
                onPause = { viewModel.pauseGame() },
                onResume = { viewModel.resumeGame() },
                onReset = { viewModel.resetGame() },
                onToggleSound = { viewModel.toggleSound() },
                onToggleVibration = { viewModel.toggleVibration() },
                onOpenSavedPresets = { showPresetsOverview = true },
                onOpenCustomSetup = { showCustomConfig = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(GeometricDarkBackground)
                    .padding(horizontal = 16.dp)
            )

            // --- BOTTOM PLAYER (PLAYER B) - Standard Orientation ---
            val isPlayerBActive = state.activePlayer == Player.PLAYER_B
            PlayerClockSegment(
                playerName = "Player Two",
                timeMs = state.playerBTimeMs,
                moveCount = state.playerBMoveCount,
                timingStyle = state.timingStyle,
                incrementSeconds = state.incrementSeconds,
                delaySeconds = state.delaySeconds,
                isActive = isPlayerBActive,
                delayBufferMs = if (isPlayerBActive) state.delayBufferMs else 0L,
                gameStatus = state.gameStatus,
                isRotated = false,
                onTap = { viewModel.onPlayerTap(Player.PLAYER_B) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("player_b_box")
            )
        }

        // --- QUICK TIME SETTER FLOATING OVERLAY ---
        // Displayed only when setting up or paused to let players configure on the fly
        if (state.gameStatus == GameStatus.SETUP || state.gameStatus == GameStatus.READY || state.gameStatus == GameStatus.PAUSED) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                QuickPresetsOverlay(
                    presets = presets,
                    activePresetId = activePresetId,
                    onSelect = { viewModel.selectPreset(it) }
                )
            }
        }

        // --- DRAWERS / DIALOGS ---
        if (showCustomConfig) {
            CustomClockBuilderDialog(
                onDismiss = { showCustomConfig = false },
                onSave = { name, totalMs, timingType, incSec, dSec ->
                    viewModel.createAndSelectCustomPreset(name, totalMs, timingType, incSec, dSec)
                    showCustomConfig = false
                }
            )
        }

        if (showPresetsOverview) {
            PresetsOverviewDialog(
                presets = presets,
                activePresetId = activePresetId,
                onDismiss = { showPresetsOverview = false },
                onSelectPreset = {
                    viewModel.selectPreset(it)
                    showPresetsOverview = false
                },
                onDelete = { viewModel.deletePreset(it) }
            )
        }
    }
}

@Composable
fun PlayerClockSegment(
    playerName: String,
    timeMs: Long,
    moveCount: Int,
    timingStyle: TimingStyle,
    incrementSeconds: Int,
    delaySeconds: Int,
    isActive: Boolean,
    delayBufferMs: Long,
    gameStatus: GameStatus,
    isRotated: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic styling using colors aligned with "Geometric Balance"
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) GeometricActiveCyan else GeometricInactivePlayer,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "BgAnimation"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isActive) GeometricActiveOnCyan else Color.White,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "TextColorAnimation"
    )

    val labelColor = if (isActive) contentColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.5f)
    val cornerShape = if (isRotated) {
        RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)
    } else {
        RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)
    }

    Box(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(cornerShape)
            .background(backgroundColor)
            .clickable { onTap() }
    ) {
        val contents = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Active top-strip indicator
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(contentColor.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Player name and move indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playerName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.4.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        ),
                        color = labelColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = contentColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "M$moveCount",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = labelColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Time Counter Display
                val formattedTime = formatChessClockTime(timeMs)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (timeMs >= 10_000L) {
                        val parts = formattedTime.split(":")
                        if (parts.size == 2) {
                            Text(
                                text = parts[0],
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.SansSerif,
                                color = contentColor,
                                letterSpacing = (-2).sp
                            )
                            Text(
                                text = ":",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Thin,
                                color = contentColor.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Text(
                                text = parts[1],
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.SansSerif,
                                color = contentColor,
                                letterSpacing = (-2).sp
                            )
                        } else {
                            Text(
                                text = formattedTime,
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.SansSerif,
                                color = contentColor
                            )
                        }
                    } else {
                        // For under 10 seconds, render with intense red alerting contrast when inactive
                        // and show beautiful digital millisecond/subsecond precision!
                        val tensionColor = if (isActive) contentColor else Color(0xFFFF5252)
                        Text(
                            text = formattedTime,
                            fontSize = 86.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = tensionColor,
                            letterSpacing = (-1).sp
                        )
                    }
                }

                // Inline Timing Modifiers (Increment or delay overlay)
                if (timingStyle != TimingStyle.NONE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = contentColor.copy(alpha = if (isActive) 0.15f else 0.08f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        val text = when (timingStyle) {
                            TimingStyle.INCREMENT -> "+ ${incrementSeconds}s INC"
                            TimingStyle.DELAY -> {
                                if (isActive && delayBufferMs > 0) {
                                    val ds = (delayBufferMs / 1000f)
                                    "DELAY: %.1f".format(ds)
                                } else {
                                    "DELAY: ${delaySeconds}s"
                                }
                            }
                            else -> ""
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                }

                // Show flag state if time runs out
                if (gameStatus == GameStatus.FLAG_FALL && timeMs <= 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFD32F2F), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "FLAG FELL 🏳️",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                        )
                    }
                }
            }
        }

        if (isRotated) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(180f)
            ) {
                contents()
            }
        } else {
            contents()
        }
    }
}

@Composable
fun MiddleControlBar(
    state: ChessTimerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleVibration: () -> Unit,
    onOpenSavedPresets: () -> Unit,
    onOpenCustomSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Sound and Vibration controls
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onToggleSound,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GeometricSurfaceDark)
                    .testTag("sound_toggle_button"),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFD0BCFF))
            ) {
                Icon(
                    imageVector = if (state.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = "Toggle Audio FX"
                )
            }

            IconButton(
                onClick = onOpenSavedPresets,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GeometricSurfaceDark)
                    .testTag("history_presets_button"),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFD0BCFF))
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Presets & History"
                )
            }
        }

        // Center Action Circle Pill
        val isRunning = state.gameStatus == GameStatus.RUNNING
        IconButton(
            onClick = {
                if (isRunning) onPause() else onResume()
            },
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Color(0xFFD0BCFF))
                .testTag("play_pause_button"),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF381E72))
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isRunning) "Pause Match" else "Resume/Start Match",
                modifier = Modifier.size(32.dp)
            )
        }

        // Right Side: Reset and Custom settings Launcher
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onReset,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GeometricSurfaceDark)
                    .testTag("reset_clock_button"),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFD0BCFF))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Clock"
                )
            }

            IconButton(
                onClick = onOpenCustomSetup,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GeometricSurfaceDark)
                    .testTag("settings_clock_button"),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFD0BCFF))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configure Time limits"
                )
            }
        }
    }
}

@Composable
fun QuickPresetsOverlay(
    presets: List<TimePreset>,
    activePresetId: Int?,
    onSelect: (TimePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF121212).copy(alpha = 0.55f),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .height(58.dp)
    ) {
        LazyRow(
            modifier = Modifier
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(presets.take(6)) { preset ->
                val isSelected = preset.id == activePresetId
                val btnBg = if (isSelected) Color(0xFFD0BCFF) else Color.Transparent
                val btnColor = if (isSelected) Color(0xFF381E72) else Color.White.copy(alpha = 0.85f)
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(btnBg)
                        .clickable { onSelect(preset) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        ),
                        color = btnColor
                    )
                }
            }
        }
    }
}

@Composable
fun CustomClockBuilderDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, totalTimeMs: Long, style: TimingStyle, incSec: Int, dSec: Int) -> Unit
) {
    var minutes by remember { mutableStateOf(5.0f) }
    var seconds by remember { mutableStateOf(0.0f) }
    var incSecs by remember { mutableStateOf(0.0f) }
    var delaySecs by remember { mutableStateOf(0.0f) }
    var timingMode by remember { mutableStateOf(TimingStyle.NONE) }
    var presetName by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = GeometricSurfaceDark,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "Time Setup",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Minutes slider
                Text(
                    text = "Minutes: ${minutes.toInt()}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Slider(
                    value = minutes,
                    onValueChange = { minutes = it },
                    valueRange = 0f..180f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFFD0BCFF),
                        thumbColor = Color(0xFFD0BCFF)
                    ),
                    modifier = Modifier.padding(bottom = 16.dp).testTag("minutes_slider")
                )

                // Seconds slider
                Text(
                    text = "Seconds: ${seconds.toInt()}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Slider(
                    value = seconds,
                    onValueChange = { seconds = it },
                    valueRange = 0f..59f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFFD0BCFF),
                        thumbColor = Color(0xFFD0BCFF)
                    ),
                    modifier = Modifier.padding(bottom = 16.dp).testTag("seconds_slider")
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))

                // Timer Delay Mode Switcher
                Text(
                    text = "Delay / Increment Style",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val styles = listOf(
                        Triple(TimingStyle.NONE, "None", Icons.Default.HourglassBottom),
                        Triple(TimingStyle.INCREMENT, "Fischer", Icons.Default.Add),
                        Triple(TimingStyle.DELAY, "Simple Delay", Icons.Default.HourglassTop)
                    )

                    styles.forEach { (style, label, icon) ->
                        val isSelected = timingMode == style
                        val borderCol = if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.15f)
                        val bgCol = if (isSelected) Color(0xFFD0BCFF).copy(alpha = 0.15f) else Color.Transparent

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgCol)
                                .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                .clickable { timingMode = style }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Conditionally show delay or increment parameters
                if (timingMode == TimingStyle.INCREMENT) {
                    Text(
                        text = "Fischer Increment (seconds): ${incSecs.toInt()}s",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Slider(
                        value = incSecs,
                        onValueChange = { incSecs = it },
                        valueRange = 0f..60f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFFD0BCFF),
                            thumbColor = Color(0xFFD0BCFF)
                        ),
                        modifier = Modifier.padding(bottom = 16.dp).testTag("increment_slider")
                    )
                }

                if (timingMode == TimingStyle.DELAY) {
                    Text(
                        text = "Simple USCF delay (seconds): ${delaySecs.toInt()}s",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Slider(
                        value = delaySecs,
                        onValueChange = { delaySecs = it },
                        valueRange = 0f..60f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFFD0BCFF),
                            thumbColor = Color(0xFFD0BCFF)
                        ),
                        modifier = Modifier.padding(bottom = 16.dp).testTag("delay_slider")
                    )
                }

                // Custom Title (Optional)
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Custom Mode Label (eg. Modern Speed)") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        focusedLabelColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("preset_name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Actions buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White.copy(alpha = 0.8f))
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val computedMinutes = minutes.toInt()
                            val computedSeconds = seconds.toInt()
                            val totalTimeMs = (computedMinutes * 60 + computedSeconds) * 1000L
                            
                            // Prevent empty setup
                            if (totalTimeMs > 0) {
                                onSave(
                                    presetName,
                                    totalTimeMs,
                                    timingMode,
                                    incSecs.toInt(),
                                    delaySecs.toInt()
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("apply_preset_submit")
                    ) {
                        Text("Apply & Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PresetsOverviewDialog(
    presets: List<TimePreset>,
    activePresetId: Int?,
    onDismiss: () -> Unit,
    onSelectPreset: (TimePreset) -> Unit,
    onDelete: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = GeometricSurfaceDark,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Saved Presets",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (presets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "None available",
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No active presets load",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .height(300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            val isSelected = preset.id == activePresetId
                            val borderCol = if (isSelected) Color(0xFFD0BCFF) else Color.Transparent
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectPreset(preset) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isSelected) Color(0xFFD0BCFF) else Color.White
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        
                                        val subLabel = when (preset.timingStyle) {
                                            "INCREMENT" -> "${preset.incrementSeconds}s Fischer Increment"
                                            "DELAY" -> "${preset.delaySeconds}s USCF Delay"
                                            else -> "Sudden Death"
                                        }
                                        Text(
                                            text = subLabel,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }

                                    if (preset.isCustom) {
                                        IconButton(
                                            onClick = { onDelete(preset.id) },
                                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFFF5252))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Custom Preset"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

// FORMAT HELPER
fun formatChessClockTime(timeMs: Long): String {
    if (timeMs >= 10_000L) {
        val minutes = (timeMs / 60_000L).toInt()
        val seconds = ((timeMs % 60_000L) / 1000L).toInt()
        return "%02d:%02d".format(minutes, seconds)
    } else {
        // Less than 10 seconds, return tenths of a second precision
        val seconds = timeMs / 1000f
        return "%.1f".format(seconds)
    }
}
