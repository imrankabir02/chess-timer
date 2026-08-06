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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.Piece
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.ui.theme.GeometricAccentLavender
import com.example.ui.theme.GeometricActiveCyan
import com.example.ui.theme.GeometricDarkBackground
import com.example.ui.theme.GeometricSurfaceDark
import com.example.ui.theme.LudoRed

/** Landing screen: play a real game, or use the device as a plain tournament clock. */
@Composable
fun HomeScreen(
    onPlayChess: () -> Unit,
    onOpenClock: () -> Unit,
    onPlayLudo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GeometricDarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PieceGlyph(
                piece = Piece(PieceColor.WHITE, PieceType.KING),
                fontSize = 44.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            PieceGlyph(
                piece = Piece(PieceColor.BLACK, PieceType.QUEEN),
                fontSize = 44.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "GAME TABLE",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            ),
            color = Color.White
        )
        Text(
            text = "Play a game on the device, or keep time for a board in front of you.",
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = Color.White.copy(alpha = 0.55f)
        )

        Spacer(modifier = Modifier.height(28.dp))

        ModeCard(
            title = "Play chess",
            subtitle = "Two players or three levels of computer opponent, full rules either way",
            accent = GeometricActiveCyan,
            glyph = "♞",
            onClick = onPlayChess,
            testTag = "mode_play_chess"
        )

        Spacer(modifier = Modifier.height(12.dp))

        ModeCard(
            title = "Play ludo",
            subtitle = "Two to four seats, computer opponents, captures and safe squares",
            accent = LudoRed,
            glyph = "🎲",
            onClick = onPlayLudo,
            testTag = "mode_play_ludo"
        )

        Spacer(modifier = Modifier.height(12.dp))

        ModeCard(
            title = "Chess clock",
            subtitle = "Two tap areas for a physical board, with your saved presets",
            accent = GeometricAccentLavender,
            glyph = "⏱",
            onClick = onOpenClock,
            testTag = "mode_chess_clock"
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    accent: Color,
    glyph: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(GeometricSurfaceDark)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.35f)), RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(20.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = glyph, fontSize = 28.sp, color = accent)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}
