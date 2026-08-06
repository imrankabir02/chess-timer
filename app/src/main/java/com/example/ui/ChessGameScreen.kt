package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chess.GameEndReason
import com.example.chess.GameOutcome
import com.example.chess.GameResult
import com.example.chess.Piece
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.Square
import com.example.model.ChessGameUiState
import com.example.model.GameTimeControl
import com.example.model.PendingPromotion
import com.example.model.TimingStyle
import com.example.ui.theme.GeometricAccentLavender
import com.example.ui.theme.GeometricActiveCyan
import com.example.ui.theme.GeometricDarkBackground
import com.example.ui.theme.GeometricPurpleDark
import com.example.ui.theme.GeometricSurfaceDark
import com.example.viewmodel.ChessGameViewModel

private val LowTimeRed = Color(0xFFFF5252)

/** The full over-the-board experience: two clocks, a real board and the rules in between. */
@Composable
fun ChessGameScreen(
    viewModel: ChessGameViewModel,
    modifier: Modifier = Modifier,
    onExit: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val timeControls by viewModel.timeControls.collectAsState()

    var showNewGame by remember { mutableStateOf(false) }
    var showResignConfirm by remember { mutableStateOf(false) }
    var showDrawConfirm by remember { mutableStateOf(false) }

    // A chess clock is useless if the screen sleeps mid-game.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // With the board fixed, the two players sit opposite each other, so the far panel is upside
    // down. With auto-flip the device is passed around and everything should read upright.
    val topColor = if (state.boardFlipped) PieceColor.WHITE else PieceColor.BLACK
    val bottomColor = topColor.opposite

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GeometricDarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
    ) {
        GameTopBar(
            state = state,
            onExit = onExit,
            onToggleSound = { viewModel.toggleSound() },
            onNewGame = { showNewGame = true }
        )

        PlayerBar(
            color = topColor,
            state = state,
            rotated = !state.autoFlip,
            modifier = Modifier.testTag("player_bar_top")
        )

        Spacer(modifier = Modifier.height(6.dp))

        ChessBoard(
            position = state.position,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            flipped = state.boardFlipped,
            selectedSquare = state.selectedSquare,
            legalTargets = state.legalTargets,
            lastMove = state.game.lastMove,
            checkSquare = if (state.game.isCheck) {
                state.position.kingSquare(state.sideToMove)
            } else {
                Square.NONE
            },
            enabled = !state.isOver && !state.isPaused,
            onSquareClick = { viewModel.onSquareTapped(it) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        MoveTicker(
            state = state,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        PlayerBar(
            color = bottomColor,
            state = state,
            rotated = false,
            modifier = Modifier.testTag("player_bar_bottom")
        )

        GameControlBar(
            state = state,
            onUndo = { viewModel.undo() },
            onToggleClock = { viewModel.toggleClock() },
            onFlip = { viewModel.flipBoard() },
            onOfferDraw = { showDrawConfirm = true },
            onResign = { showResignConfirm = true }
        )
    }

    state.pendingPromotion?.let { pending ->
        PromotionDialog(
            pending = pending,
            onPick = { viewModel.choosePromotion(it) },
            onDismiss = { viewModel.cancelPromotion() }
        )
    }

    if (showNewGame) {
        NewGameDialog(
            timeControls = timeControls,
            current = state.timeControl,
            autoFlip = state.autoFlip,
            onToggleAutoFlip = { viewModel.toggleAutoFlip() },
            onStart = {
                viewModel.newGame(it)
                showNewGame = false
            },
            onDismiss = { showNewGame = false }
        )
    }

    if (showResignConfirm) {
        ConfirmDialog(
            title = "Resign?",
            message = "${state.sideToMove.displayName} resigns and the game ends.",
            confirmLabel = "Resign",
            onConfirm = {
                viewModel.resign(state.sideToMove)
                showResignConfirm = false
            },
            onDismiss = { showResignConfirm = false }
        )
    }

    if (showDrawConfirm) {
        ConfirmDialog(
            title = "Agree a draw?",
            message = "Both players agree to split the point.",
            confirmLabel = "Draw",
            onConfirm = {
                viewModel.agreeDraw()
                showDrawConfirm = false
            },
            onDismiss = { showDrawConfirm = false }
        )
    }

    val result = state.game.result
    if (result != null && !state.resultDismissed) {
        GameOverDialog(
            result = result,
            onNewGame = {
                viewModel.newGame()
                viewModel.dismissResult()
            },
            onReviewBoard = { viewModel.dismissResult() }
        )
    }
}

@Composable
private fun GameTopBar(
    state: ChessGameUiState,
    onExit: () -> Unit,
    onToggleSound: () -> Unit,
    onNewGame: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onExit,
            colors = IconButtonDefaults.iconButtonColors(contentColor = GeometricAccentLavender),
            modifier = Modifier.testTag("exit_game_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to modes")
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.statusLine(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.testTag("game_status_text")
            )
            Text(
                text = state.timeControl.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }

        IconButton(
            onClick = onToggleSound,
            colors = IconButtonDefaults.iconButtonColors(contentColor = GeometricAccentLavender),
            modifier = Modifier.testTag("game_sound_button")
        ) {
            Icon(
                imageVector = if (state.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                contentDescription = "Toggle sound"
            )
        }
        IconButton(
            onClick = onNewGame,
            colors = IconButtonDefaults.iconButtonColors(contentColor = GeometricAccentLavender),
            modifier = Modifier.testTag("new_game_button")
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "New game")
        }
    }
}

@Composable
private fun PlayerBar(
    color: PieceColor,
    state: ChessGameUiState,
    rotated: Boolean,
    modifier: Modifier = Modifier
) {
    val isToMove = !state.isOver && state.sideToMove == color
    val timeMs = state.timeFor(color)
    val isLowOnTime = !state.timeControl.isUnlimited && timeMs < 30_000L
    val accent = when {
        isToMove -> GeometricActiveCyan
        else -> Color.White.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isToMove) Color.White.copy(alpha = 0.07f) else GeometricSurfaceDark)
            .border(
                width = if (isToMove) 1.dp else 0.dp,
                color = if (isToMove) GeometricActiveCyan.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        val content = @Composable {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (color == PieceColor.WHITE) Color.White else Color(0xFF15161A))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = color.displayName.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.8.sp
                            ),
                            color = accent
                        )
                        val lead = state.materialLead(color)
                        if (lead > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "+$lead",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    CapturedRow(captured = state.capturedBy(color), color = color.opposite)
                }

                Text(
                    text = if (state.timeControl.isUnlimited) "∞" else formatChessClockTime(timeMs),
                    fontSize = if (state.timeControl.isUnlimited) 26.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        state.timeControl.isUnlimited -> Color.White.copy(alpha = 0.5f)
                        isLowOnTime -> LowTimeRed
                        isToMove -> Color.White
                        else -> Color.White.copy(alpha = 0.65f)
                    },
                    modifier = Modifier.testTag("clock_${color.name.lowercase()}")
                )
            }
        }

        if (rotated) {
            Box(modifier = Modifier.fillMaxWidth().rotate(180f)) { content() }
        } else {
            content()
        }
    }
}

@Composable
private fun CapturedRow(captured: List<PieceType>, color: PieceColor) {
    if (captured.isEmpty()) {
        Text(
            text = "—",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.25f)
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.heightIn(min = 18.dp)
    ) {
        captured.take(12).forEach { type ->
            CapturedPieceGlyph(
                type = type,
                color = color,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 1.dp)
            )
        }
    }
}

@Composable
private fun MoveTicker(state: ChessGameUiState, modifier: Modifier = Modifier) {
    val pairs = state.game.movePairs()
    val listState = rememberLazyListState()

    LaunchedEffect(pairs.size) {
        if (pairs.isNotEmpty()) listState.animateScrollToItem(pairs.size - 1)
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(GeometricSurfaceDark),
        contentAlignment = Alignment.CenterStart
    ) {
        if (pairs.isEmpty()) {
            Text(
                text = "White to start — tap a piece, then its destination",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag("move_list"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(pairs) { _, (number, white, black) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$number.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = white,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        if (black != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = black,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameControlBar(
    state: ChessGameUiState,
    onUndo: () -> Unit,
    onToggleClock: () -> Unit,
    onFlip: () -> Unit,
    onOfferDraw: () -> Unit,
    onResign: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquareIconButton(
            onClick = onUndo,
            enabled = state.game.moveCount > 0 || state.isOver,
            testTag = "undo_button"
        ) {
            Icon(Icons.Default.Undo, contentDescription = "Take back a move")
        }

        SquareIconButton(
            onClick = onToggleClock,
            enabled = !state.timeControl.isUnlimited && state.clockStarted && !state.isOver,
            testTag = "pause_button"
        ) {
            Icon(
                imageVector = if (state.clockRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.clockRunning) "Pause clock" else "Resume clock"
            )
        }

        SquareIconButton(onClick = onFlip, testTag = "flip_board_button") {
            Icon(Icons.Default.SwapVert, contentDescription = "Flip board")
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onOfferDraw,
            enabled = !state.isOver,
            colors = ButtonDefaults.textButtonColors(contentColor = GeometricAccentLavender),
            modifier = Modifier.testTag("draw_button")
        ) {
            Text("Draw", fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = onResign,
            enabled = !state.isOver,
            colors = ButtonDefaults.textButtonColors(contentColor = LowTimeRed),
            modifier = Modifier.testTag("resign_button")
        ) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Resign", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun SquareIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GeometricSurfaceDark)
            .testTag(testTag),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = GeometricAccentLavender,
            disabledContentColor = Color.White.copy(alpha = 0.25f)
        ),
        content = content
    )
}

@Composable
private fun PromotionDialog(
    pending: PendingPromotion,
    onPick: (PieceType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Text(
                text = "Promote to",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${pending.color.displayName} pawn reaches ${Square.name(pending.to)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    PieceType.QUEEN,
                    PieceType.ROOK,
                    PieceType.BISHOP,
                    PieceType.KNIGHT
                ).forEach { type ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                GeometricAccentLavender.copy(alpha = 0.4f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onPick(type) }
                            .testTag("promote_${type.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        PieceGlyph(
                            piece = Piece(pending.color, type),
                            fontSize = 38.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun NewGameDialog(
    timeControls: List<GameTimeControl>,
    current: GameTimeControl,
    autoFlip: Boolean,
    onToggleAutoFlip: () -> Unit,
    onStart: (GameTimeControl) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(current) }

    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Text(
                text = "New game",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pick a time control. Presets are shared with the clock mode.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .height(260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                timeControls.forEach { control ->
                    val isSelected = control.label == selected.label &&
                        control.initialTimeMs == selected.initialTimeMs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    GeometricAccentLavender.copy(alpha = 0.16f)
                                } else {
                                    Color.White.copy(alpha = 0.04f)
                                }
                            )
                            .border(
                                1.dp,
                                if (isSelected) GeometricAccentLavender else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selected = control }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = control.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isSelected) GeometricAccentLavender else Color.White
                            )
                            Text(
                                text = control.describe(),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rotate board each move", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = "For passing one phone back and forth",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = autoFlip,
                    onCheckedChange = { onToggleAutoFlip() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GeometricPurpleDark,
                        checkedTrackColor = GeometricAccentLavender
                    ),
                    modifier = Modifier.testTag("auto_flip_switch")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onStart(selected) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeometricAccentLavender,
                        contentColor = GeometricPurpleDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("start_game_button")
                ) {
                    Text("Start", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeometricAccentLavender,
                        contentColor = GeometricPurpleDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("confirm_button")
                ) {
                    Text(confirmLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GameOverDialog(
    result: GameResult,
    onNewGame: () -> Unit,
    onReviewBoard: () -> Unit
) {
    Dialog(onDismissRequest = onReviewBoard) {
        DialogCard {
            Text(
                text = result.headline(),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = GeometricActiveCyan,
                modifier = Modifier.testTag("game_over_headline")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${result.scoreLine} · ${result.detail()}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(22.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onReviewBoard,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Text("Review board")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onNewGame,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeometricAccentLavender,
                        contentColor = GeometricPurpleDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("game_over_new_game")
                ) {
                    Text("New game", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Shared dialog shell for the game screens. */
@Composable
internal fun DialogCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = GeometricSurfaceDark,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(modifier = Modifier.padding(22.dp)) { content() }
    }
}

// region Presentation helpers

internal val PieceColor.displayName: String
    get() = if (this == PieceColor.WHITE) "White" else "Black"

internal fun GameTimeControl.describe(): String {
    if (isUnlimited) return "No clock · play untimed"
    val minutes = initialTimeMs / 60_000
    val seconds = (initialTimeMs % 60_000) / 1000
    val base = if (seconds > 0) "$minutes:${"%02d".format(seconds)}" else "$minutes min"
    return when (timingStyle) {
        TimingStyle.INCREMENT -> "$base · +${incrementSeconds}s Fischer increment"
        TimingStyle.DELAY -> "$base · ${delaySeconds}s USCF delay"
        TimingStyle.NONE -> "$base · sudden death"
    }
}

internal fun ChessGameUiState.statusLine(): String {
    val result = game.result
    return when {
        result != null -> "${result.headline()} · ${result.scoreLine}"
        isPaused -> "Paused"
        game.isCheck -> "${sideToMove.displayName} is in check"
        else -> "${sideToMove.displayName} to move"
    }
}

internal fun GameResult.headline(): String = when (outcome) {
    GameOutcome.WHITE_WINS -> "White wins"
    GameOutcome.BLACK_WINS -> "Black wins"
    GameOutcome.DRAW -> "Draw"
}

internal fun GameResult.detail(): String = when (reason) {
    GameEndReason.CHECKMATE -> "by checkmate"
    GameEndReason.STALEMATE -> "by stalemate"
    GameEndReason.FIFTY_MOVE_RULE -> "by the fifty-move rule"
    GameEndReason.THREEFOLD_REPETITION -> "by threefold repetition"
    GameEndReason.INSUFFICIENT_MATERIAL -> "by insufficient material"
    GameEndReason.TIMEOUT -> "on time"
    GameEndReason.TIMEOUT_VS_INSUFFICIENT_MATERIAL -> "flag fell, but no mating material"
    GameEndReason.RESIGNATION -> "by resignation"
    GameEndReason.DRAW_AGREED -> "by agreement"
}

// endregion
