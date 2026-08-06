package com.example.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ludo.LudoBoard
import com.example.ludo.LudoColor
import com.example.ludo.LudoGame
import com.example.ludo.LudoPlayer
import com.example.ludo.LudoRules
import com.example.model.LudoSetup
import com.example.model.LudoUiState
import com.example.ui.theme.GeometricAccentLavender
import com.example.ui.theme.GeometricDarkBackground
import com.example.ui.theme.GeometricPurpleDark
import com.example.ui.theme.GeometricSurfaceDark
import com.example.viewmodel.LudoViewModel

/** The Ludo table: four seats, one board and a die. */
@Composable
fun LudoGameScreen(
    viewModel: LudoViewModel,
    modifier: Modifier = Modifier,
    onExit: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var showSetup by remember { mutableStateOf(false) }

    // Offer the table setup once, so the choice of two, three or four players is the first
    // thing a player sees rather than something hidden behind an icon.
    LaunchedEffect(state.setupSeen) {
        if (!state.setupSeen) {
            showSetup = true
            viewModel.markSetupSeen()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GeometricDarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
    ) {
        LudoTopBar(
            state = state,
            onExit = onExit,
            onOpenSetup = { showSetup = true },
            onToggleSound = { viewModel.toggleSound() },
            onNewGame = { viewModel.newGame() }
        )

        // The four seats sit in the corners that match their yards on the board, and the die
        // belongs to whoever is on turn — so each player rolls from their own side of the table.
        SeatRow(
            left = LudoColor.RED,
            right = LudoColor.GREEN,
            state = state,
            rotated = state.setup.tableMode,
            onRoll = { viewModel.rollDice() }
        )

        Spacer(modifier = Modifier.height(6.dp))

        LudoBoardView(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            onTokenClick = { color, index -> viewModel.onTokenTapped(color, index) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        SeatRow(
            left = LudoColor.BLUE,
            right = LudoColor.YELLOW,
            state = state,
            rotated = false,
            onRoll = { viewModel.rollDice() }
        )

        Spacer(modifier = Modifier.height(6.dp))

        CommentaryBar(state = state)
    }

    if (showSetup) {
        LudoSetupDialog(
            setup = state.setup,
            onStart = {
                viewModel.newGame(it)
                showSetup = false
            },
            onDismiss = { showSetup = false }
        )
    }

    if (state.game.isOver && !state.resultDismissed) {
        LudoResultDialog(
            game = state.game,
            onNewGame = {
                viewModel.newGame()
                viewModel.dismissResult()
            },
            onDismiss = { viewModel.dismissResult() }
        )
    }
}

@Composable
private fun LudoTopBar(
    state: LudoUiState,
    onExit: () -> Unit,
    onOpenSetup: () -> Unit,
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
            modifier = Modifier.testTag("exit_ludo_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to modes")
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ludo",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                text = state.setup.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }

        // A plain "4P" pill: the number of players is a choice, not something buried in a menu.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(GeometricAccentLavender.copy(alpha = 0.16f))
                .border(1.dp, GeometricAccentLavender.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { onOpenSetup() }
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("players_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = "Choose players",
                tint = GeometricAccentLavender,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "${state.setup.playerCount}P",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GeometricAccentLavender
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onToggleSound,
            colors = IconButtonDefaults.iconButtonColors(contentColor = GeometricAccentLavender),
            modifier = Modifier.testTag("ludo_sound_button")
        ) {
            Icon(
                imageVector = if (state.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                contentDescription = "Toggle sound"
            )
        }
        IconButton(
            onClick = onNewGame,
            colors = IconButtonDefaults.iconButtonColors(contentColor = GeometricAccentLavender),
            modifier = Modifier.testTag("ludo_new_game_button")
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "New game")
        }
    }
}

/** The two seats along one edge of the board, in the corners their yards occupy. */
@Composable
private fun SeatRow(
    left: LudoColor,
    right: LudoColor,
    state: LudoUiState,
    rotated: Boolean,
    onRoll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Each die sits at the outer end of its panel, directly over that colour's own yard.
        SeatPanel(
            color = left,
            state = state,
            dieOnTheLeft = true,
            rotated = rotated,
            onRoll = onRoll,
            modifier = Modifier.weight(1f)
        )
        SeatPanel(
            color = right,
            state = state,
            dieOnTheLeft = false,
            rotated = rotated,
            onRoll = onRoll,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * One player's corner: who they are, how many tokens are home, and — while it is their turn —
 * the die itself. Empty corners keep their place so the board stays centred.
 */
@Composable
private fun SeatPanel(
    color: LudoColor,
    state: LudoUiState,
    dieOnTheLeft: Boolean,
    rotated: Boolean,
    onRoll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val player = state.seat(color)
    val isOnTurn = player != null && state.isOnTurn(color)
    val place = state.game.finishOrder.indexOf(color).takeIf { it >= 0 }?.plus(1)

    Box(
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    player == null -> Color.White.copy(alpha = 0.03f)
                    isOnTurn -> color.brand.copy(alpha = 0.20f)
                    else -> GeometricSurfaceDark
                }
            )
            .border(
                width = if (isOnTurn) 1.5.dp else 0.dp,
                color = if (isOnTurn) color.brand else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("seat_${color.name.lowercase()}")
    ) {
        val content = @Composable {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dieSlot = @Composable {
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isOnTurn) {
                            DiceButton(
                                value = state.diceFace,
                                color = color.brand,
                                enabled = state.canRoll,
                                isRolling = state.isRolling,
                                onClick = onRoll,
                                testTag = "dice_${color.name.lowercase()}"
                            )
                        }
                    }
                }

                if (dieOnTheLeft) {
                    dieSlot()
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(if (player == null) color.brand.copy(alpha = 0.3f) else color.brand)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                player == null -> "Empty"
                                player.isBot -> "Computer"
                                else -> "Player"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = if (isOnTurn) 1f else 0.65f)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = when {
                            player == null -> "—"
                            place != null -> "${placeLabel(place)} place"
                            else -> "${player.tokensHome}/${LudoBoard.TOKENS_PER_PLAYER} home"
                        },
                        fontSize = 11.sp,
                        fontWeight = if (place != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (place != null) color.brand else Color.White.copy(alpha = 0.5f)
                    )
                    if (isOnTurn) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (state.awaitingTokenChoice) "Pick a token" else "Your roll",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = color.brand
                        )
                    }
                }

                if (!dieOnTheLeft) {
                    Spacer(modifier = Modifier.width(8.dp))
                    dieSlot()
                }
            }
        }

        if (rotated) {
            Box(modifier = Modifier.fillMaxSize().rotate(180f)) { content() }
        } else {
            content()
        }
    }
}

/** A slim running commentary of the last thing that happened. */
@Composable
private fun CommentaryBar(state: LudoUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GeometricSurfaceDark)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (state.game.isOver) Color.White.copy(alpha = 0.4f) else state.currentColor.brand)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier
                .weight(1f)
                .testTag("ludo_message")
        )
    }
}

@Composable
private fun DiceButton(
    value: Int,
    color: Color,
    enabled: Boolean,
    isRolling: Boolean,
    onClick: () -> Unit,
    testTag: String = "dice_button"
) {
    val spin by rememberInfiniteTransition(label = "diceSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Restart
        ),
        label = "diceSpinAngle"
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .rotate(if (isRolling) spin else 0f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(
                width = 2.dp,
                color = if (enabled) color else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .alpha(if (enabled || isRolling) 1f else 0.55f)
            .clickable(enabled = enabled) { onClick() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        DiceFace(
            value = value,
            pipColor = Color(0xFF23242A),
            modifier = Modifier.size(36.dp)
        )
    }
}

/** The pips of a die face, laid out on the usual three-by-three grid. */
@Composable
fun DiceFace(value: Int, pipColor: Color, modifier: Modifier = Modifier) {
    val low = 0.22f
    val mid = 0.5f
    val high = 0.78f
    val pips: List<Pair<Float, Float>> = when (value) {
        1 -> listOf(mid to mid)
        2 -> listOf(low to low, high to high)
        3 -> listOf(low to low, mid to mid, high to high)
        4 -> listOf(low to low, high to low, low to high, high to high)
        5 -> listOf(low to low, high to low, mid to mid, low to high, high to high)
        else -> listOf(
            low to low, high to low,
            low to mid, high to mid,
            low to high, high to high
        )
    }

    Canvas(modifier = modifier.testTag("dice_face_$value")) {
        val radius = size.minDimension * 0.085f
        pips.forEach { (x, y) ->
            drawCircle(color = pipColor, radius = radius, center = Offset(x * size.width, y * size.height))
        }
    }
}

@Composable
private fun LudoSetupDialog(
    setup: LudoSetup,
    onStart: (LudoSetup) -> Unit,
    onDismiss: () -> Unit
) {
    var playerCount by remember { mutableStateOf(setup.playerCount) }
    var bots by remember { mutableStateOf(setup.botColors) }
    var blockades by remember { mutableStateOf(setup.rules.blockades) }
    val seats = LudoGame.seatsFor(playerCount)

    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Text(
                text = "New Ludo game",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text("Players", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4).forEach { count ->
                    val isSelected = count == playerCount
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    GeometricAccentLavender.copy(alpha = 0.18f)
                                } else {
                                    Color.White.copy(alpha = 0.05f)
                                }
                            )
                            .border(
                                1.dp,
                                if (isSelected) GeometricAccentLavender else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { playerCount = count }
                            .padding(vertical = 10.dp)
                            .testTag("player_count_$count"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$count",
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) GeometricAccentLavender else Color.White
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.padding(vertical = 14.dp)
            )

            Text("Who plays each colour", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            seats.forEach { color ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(color.brand)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = color.displayName,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (color in bots) "Computer" else "Human",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = color in bots,
                        onCheckedChange = { isBot ->
                            bots = if (isBot) bots + color else bots - color
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GeometricPurpleDark,
                            checkedTrackColor = GeometricAccentLavender
                        ),
                        modifier = Modifier.testTag("bot_switch_${color.name.lowercase()}")
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.padding(vertical = 14.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Blockades", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = "Two tokens on a square bar everyone else from passing",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = blockades,
                    onCheckedChange = { blockades = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GeometricPurpleDark,
                        checkedTrackColor = GeometricAccentLavender
                    ),
                    modifier = Modifier.testTag("blockades_switch")
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onStart(
                            LudoSetup(
                                playerCount = playerCount,
                                botColors = bots,
                                rules = LudoRules(blockades = blockades)
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeometricAccentLavender,
                        contentColor = GeometricPurpleDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("ludo_start_button")
                ) {
                    Text("Start", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LudoResultDialog(
    game: LudoGame,
    onNewGame: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Casino,
                    contentDescription = null,
                    tint = game.winner?.brand ?: GeometricAccentLavender
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${game.winner?.displayName ?: "Nobody"} wins!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = game.winner?.brand ?: Color.White,
                    modifier = Modifier.testTag("ludo_result_headline")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            game.standings.forEachIndexed { index, color ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = placeLabel(index + 1),
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.width(44.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(color.brand)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = color.displayName, modifier = Modifier.weight(1f))
                    Text(
                        text = "${game.player(color).tokensHome}/${LudoBoard.TOKENS_PER_PLAYER}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Text("Close")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onNewGame,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeometricAccentLavender,
                        contentColor = GeometricPurpleDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("ludo_result_new_game")
                ) {
                    Text("Play again", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

internal fun placeLabel(place: Int): String = when (place) {
    1 -> "1st"
    2 -> "2nd"
    3 -> "3rd"
    else -> "${place}th"
}
