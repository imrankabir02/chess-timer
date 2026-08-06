package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ludo.BoardPoint
import com.example.ludo.LudoBoard
import com.example.ludo.LudoColor
import com.example.ludo.LudoToken
import com.example.model.LudoUiState
import com.example.ui.theme.LudoBlue
import com.example.ui.theme.LudoBoardCream
import com.example.ui.theme.LudoGreen
import com.example.ui.theme.LudoPathBorder
import com.example.ui.theme.LudoPathCell
import com.example.ui.theme.LudoRed
import com.example.ui.theme.LudoStarTint
import com.example.ui.theme.LudoYellow
import kotlin.math.cos
import kotlin.math.sin

/** The brand colour of each seat. */
val LudoColor.brand: Color
    get() = when (this) {
        LudoColor.RED -> LudoRed
        LudoColor.GREEN -> LudoGreen
        LudoColor.YELLOW -> LudoYellow
        LudoColor.BLUE -> LudoBlue
    }

/**
 * The board: a 15x15 grid painted in one pass, with the tokens laid over the top so they can be
 * tapped and animated independently.
 */
@Composable
fun LudoBoardView(
    state: LudoUiState,
    modifier: Modifier = Modifier,
    onTokenClick: (LudoColor, Int) -> Unit = { _, _ -> }
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(LudoBoardCream)
            .testTag("ludo_board")
    ) {
        val boardSize = minOf(maxWidth, maxHeight)
        val cell: Dp = boardSize / LudoBoard.GRID

        Canvas(modifier = Modifier.fillMaxSize()) { drawBoard() }

        // One shared pulse drives every highlighted token.
        val pulse by rememberInfiniteTransition(label = "tokenPulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.16f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 620, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tokenPulseScale"
        )

        val placements = tokenPlacements(state)
        placements.forEach { placement ->
            LudoTokenView(
                token = placement.token,
                point = placement.point,
                cell = cell,
                isMovable = state.isMovable(placement.token.color, placement.token.index),
                isLastMoved = state.lastMove?.let {
                    it.color == placement.token.color && it.token.index == placement.token.index
                } ?: false,
                pulse = pulse,
                onClick = { onTokenClick(placement.token.color, placement.token.index) }
            )
        }
    }
}

private data class TokenPlacement(val token: LudoToken, val point: BoardPoint)

/** Works out where each token is drawn, fanning out any that share a square. */
private fun tokenPlacements(state: LudoUiState): List<TokenPlacement> {
    val raw = state.game.allTokens.map { token ->
        token to LudoBoard.pointOf(token.color, token.index, token.progress)
    }
    val groups = raw.groupBy { (_, point) -> point.row to point.col }

    return raw.map { (token, point) ->
        val group = groups.getValue(point.row to point.col)
        if (group.size <= 1) {
            TokenPlacement(token, point)
        } else {
            val position = group.indexOfFirst { it.first == token }
            val angle = (2.0 * Math.PI * position / group.size) - Math.PI / 2
            val radius = 0.20f
            TokenPlacement(
                token = token,
                point = BoardPoint(
                    row = point.row + (radius * sin(angle)).toFloat(),
                    col = point.col + (radius * cos(angle)).toFloat()
                )
            )
        }
    }
}

@Composable
private fun LudoTokenView(
    token: LudoToken,
    point: BoardPoint,
    cell: Dp,
    isMovable: Boolean,
    isLastMoved: Boolean,
    pulse: Float,
    onClick: () -> Unit
) {
    val diameter = cell * 0.80f
    // Tokens glide between squares instead of jumping.
    val x by animateFloatAsState(
        targetValue = point.col,
        animationSpec = spring(),
        label = "tokenX"
    )
    val y by animateFloatAsState(
        targetValue = point.row,
        animationSpec = spring(),
        label = "tokenY"
    )

    Box(
        modifier = Modifier
            .offset(x = cell * x - diameter / 2, y = cell * y - diameter / 2)
            .size(diameter)
            .scale(if (isMovable) pulse else 1f)
            .clip(CircleShape)
            .background(token.color.brand)
            .border(
                width = if (isMovable || isLastMoved) 2.5.dp else 1.5.dp,
                color = if (isMovable) Color.White else Color.White.copy(alpha = 0.85f),
                shape = CircleShape
            )
            .clickable(enabled = isMovable) { onClick() }
            .testTag("token_${token.color.name.lowercase()}_${token.index}"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(diameter * 0.34f)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f))
        )
    }
}

// region Board painting

private fun DrawScope.drawBoard() {
    val cell = size.minDimension / LudoBoard.GRID
    val border = cell * 0.06f

    LudoColor.entries.forEach { color -> drawYard(color, cell) }
    LudoBoard.track.forEachIndexed { index, position ->
        val fill = LudoColor.entries.firstOrNull { LudoBoard.startIndex(it) == index }
            ?.brand
            ?.copy(alpha = 0.85f)
            ?: LudoPathCell
        drawCellBox(position.row, position.col, cell, fill, border)
        if (index in LudoBoard.safeTrackIndices && fill == LudoPathCell) {
            drawStar(position.row, position.col, cell)
        }
    }
    LudoColor.entries.forEach { color ->
        LudoBoard.homeColumns.getValue(color).forEach { position ->
            drawCellBox(position.row, position.col, cell, color.brand.copy(alpha = 0.75f), border)
        }
    }
    drawCentre(cell, border)
}

private fun DrawScope.drawCellBox(
    row: Int,
    col: Int,
    cell: Float,
    fill: Color,
    border: Float
) {
    val topLeft = Offset(col * cell, row * cell)
    val boxSize = Size(cell, cell)
    drawRect(color = fill, topLeft = topLeft, size = boxSize)
    drawRect(
        color = LudoPathBorder,
        topLeft = topLeft,
        size = boxSize,
        style = Stroke(width = border)
    )
}

private fun DrawScope.drawYard(color: LudoColor, cell: Float) {
    val origin = LudoBoard.yardOrigins.getValue(color)
    val top = origin.row * cell
    val left = origin.col * cell

    drawRoundRect(
        color = color.brand,
        topLeft = Offset(left, top),
        size = Size(cell * 6, cell * 6),
        cornerRadius = CornerRadius(cell * 0.5f, cell * 0.5f)
    )
    drawRoundRect(
        color = LudoPathCell,
        topLeft = Offset(left + cell, top + cell),
        size = Size(cell * 4, cell * 4),
        cornerRadius = CornerRadius(cell * 0.4f, cell * 0.4f)
    )
    LudoBoard.yardSlots(color).forEach { slot ->
        drawCircle(
            color = color.brand.copy(alpha = 0.28f),
            radius = cell * 0.44f,
            center = Offset(slot.col * cell, slot.row * cell)
        )
    }
}

/** The four triangles that make up the centre, each pointing at its own home column. */
private fun DrawScope.drawCentre(cell: Float, border: Float) {
    val left = 6 * cell
    val top = 6 * cell
    val right = 9 * cell
    val bottom = 9 * cell
    val middle = Offset(7.5f * cell, 7.5f * cell)

    val corners = mapOf(
        LudoColor.RED to listOf(Offset(left, top), Offset(left, bottom)),
        LudoColor.GREEN to listOf(Offset(left, top), Offset(right, top)),
        LudoColor.YELLOW to listOf(Offset(right, top), Offset(right, bottom)),
        LudoColor.BLUE to listOf(Offset(left, bottom), Offset(right, bottom))
    )

    corners.forEach { (color, edge) ->
        val path = Path().apply {
            moveTo(middle.x, middle.y)
            lineTo(edge[0].x, edge[0].y)
            lineTo(edge[1].x, edge[1].y)
            close()
        }
        drawPath(path = path, color = color.brand)
    }

    drawRect(
        color = LudoPathBorder,
        topLeft = Offset(left, top),
        size = Size(cell * 3, cell * 3),
        style = Stroke(width = border)
    )
}

private fun DrawScope.drawStar(row: Int, col: Int, cell: Float) {
    val centre = Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
    val outer = cell * 0.36f
    val inner = cell * 0.15f
    val path = Path()
    for (point in 0 until 10) {
        val radius = if (point % 2 == 0) outer else inner
        val angle = Math.PI / 5 * point - Math.PI / 2
        val x = centre.x + (radius * cos(angle)).toFloat()
        val y = centre.y + (radius * sin(angle)).toFloat()
        if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = LudoStarTint)
}

// endregion
