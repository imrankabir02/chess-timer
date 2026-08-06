package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.Move
import com.example.chess.Piece
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.Position
import com.example.chess.Square
import com.example.ui.theme.BoardBlackPiece
import com.example.ui.theme.BoardCheck
import com.example.ui.theme.BoardDarkSquare
import com.example.ui.theme.BoardLastMove
import com.example.ui.theme.BoardLightSquare
import com.example.ui.theme.BoardMoveHint
import com.example.ui.theme.BoardPieceOutline
import com.example.ui.theme.BoardSelected
import com.example.ui.theme.BoardWhitePiece

/** Filled chess glyphs, used for both colours so the two sides have identical silhouettes. */
private val SolidGlyphs = mapOf(
    PieceType.KING to "♚",
    PieceType.QUEEN to "♛",
    PieceType.ROOK to "♜",
    PieceType.BISHOP to "♝",
    PieceType.KNIGHT to "♞",
    PieceType.PAWN to "♟"
)

/** Outline glyphs, laid over the filled ones to give white pieces a dark contour. */
private val OutlineGlyphs = mapOf(
    PieceType.KING to "♔",
    PieceType.QUEEN to "♕",
    PieceType.ROOK to "♖",
    PieceType.BISHOP to "♗",
    PieceType.KNIGHT to "♘",
    PieceType.PAWN to "♙"
)

/**
 * An 8x8 board. Squares are addressed with the engine's own indices, so a tap reports the exact
 * square the rules code understands regardless of which way round the board is drawn.
 */
@Composable
fun ChessBoard(
    position: Position,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    selectedSquare: Int = Square.NONE,
    legalTargets: Set<Int> = emptySet(),
    lastMove: Move? = null,
    checkSquare: Int = Square.NONE,
    enabled: Boolean = true,
    onSquareClick: (Int) -> Unit = {}
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .testTag("chess_board")
    ) {
        val cellSize: Dp = maxWidth / 8
        val glyphSize = with(LocalDensity.current) { (cellSize * 0.76f).toSp() }
        val labelSize = with(LocalDensity.current) { (cellSize * 0.18f).toSp() }

        Column {
            for (row in 0..7) {
                val rank = if (flipped) row else 7 - row
                Row {
                    for (column in 0..7) {
                        val file = if (flipped) 7 - column else column
                        val square = Square.of(file, rank)
                        BoardSquare(
                            square = square,
                            piece = position.pieceAt(square),
                            size = cellSize,
                            glyphSize = glyphSize,
                            labelSize = labelSize,
                            isSelected = square == selectedSquare,
                            isTarget = square in legalTargets,
                            isLastMove = lastMove != null &&
                                (square == lastMove.from || square == lastMove.to),
                            isCheck = square == checkSquare,
                            rankLabel = if (column == 0) "${rank + 1}" else null,
                            fileLabel = if (row == 7) "${'a' + file}" else null,
                            enabled = enabled,
                            onClick = { onSquareClick(square) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardSquare(
    square: Int,
    piece: Piece?,
    size: Dp,
    glyphSize: TextUnit,
    labelSize: TextUnit,
    isSelected: Boolean,
    isTarget: Boolean,
    isLastMove: Boolean,
    isCheck: Boolean,
    rankLabel: String?,
    fileLabel: String?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val isLight = Square.isLight(square)
    val baseColor = if (isLight) BoardLightSquare else BoardDarkSquare
    val labelColor = if (isLight) BoardDarkSquare else BoardLightSquare

    Box(
        modifier = Modifier
            .size(size)
            .background(baseColor)
            .clickable(enabled = enabled) { onClick() }
            .testTag("square_${Square.name(square)}")
    ) {
        if (isLastMove) Overlay(BoardLastMove)
        if (isCheck) Overlay(BoardCheck)
        if (isSelected) Overlay(BoardSelected)

        rankLabel?.let {
            Text(
                text = it,
                fontSize = labelSize,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 2.dp, top = 1.dp)
            )
        }
        fileLabel?.let {
            Text(
                text = it,
                fontSize = labelSize,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 1.dp)
            )
        }

        if (piece != null) {
            PieceGlyph(
                piece = piece,
                fontSize = glyphSize,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isTarget) {
            Canvas(modifier = Modifier.matchParentSize()) {
                if (piece == null) {
                    drawCircle(color = BoardMoveHint, radius = this.size.minDimension * 0.16f)
                } else {
                    val stroke = this.size.minDimension * 0.09f
                    drawCircle(
                        color = BoardMoveHint,
                        radius = (this.size.minDimension - stroke) / 2f,
                        style = Stroke(width = stroke)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Overlay(color: Color) {
    Box(modifier = Modifier.matchParentSize().background(color))
}

/**
 * A single piece. White pieces are the filled glyph in white with the outline glyph stacked on
 * top, which gives them a dark contour on light and dark squares alike.
 */
@Composable
fun PieceGlyph(
    piece: Piece,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val solid = SolidGlyphs.getValue(piece.type)
    Box(modifier = modifier) {
        Text(
            text = solid,
            fontSize = fontSize,
            color = if (piece.color == PieceColor.WHITE) BoardWhitePiece else BoardBlackPiece
        )
        if (piece.color == PieceColor.WHITE) {
            Text(
                text = OutlineGlyphs.getValue(piece.type),
                fontSize = fontSize,
                color = BoardPieceOutline
            )
        }
    }
}

/** Small inline piece used for the captured-material rows. */
@Composable
fun CapturedPieceGlyph(
    type: PieceType,
    color: PieceColor,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp
) {
    PieceGlyph(piece = Piece(color, type), fontSize = fontSize, modifier = modifier)
}
