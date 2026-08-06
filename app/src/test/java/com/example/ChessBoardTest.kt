package com.example

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.chess.Position
import com.example.chess.Square
import com.example.ui.ChessBoard
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChessBoardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun showBoard(
        position: Position = Position.initial(),
        flipped: Boolean = false,
        onSquareClick: (Int) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoard(
                    position = position,
                    modifier = Modifier.size(320.dp),
                    flipped = flipped,
                    onSquareClick = onSquareClick
                )
            }
        }
    }

    @Test
    fun everySquareIsRendered() {
        showBoard()
        composeTestRule.onNodeWithTag("chess_board").assertIsDisplayed()
        listOf("a1", "h1", "a8", "h8", "e2", "e4", "d5").forEach { name ->
            composeTestRule.onNodeWithTag("square_$name").assertIsDisplayed()
        }
    }

    @Test
    fun tappingASquareReportsTheEngineSquareIndex() {
        val clicked = mutableListOf<Int>()
        showBoard(onSquareClick = { clicked.add(it) })

        composeTestRule.onNodeWithTag("square_e2").performClick()
        composeTestRule.onNodeWithTag("square_e4").performClick()

        assertEquals(listOf(Square.fromName("e2"), Square.fromName("e4")), clicked)
    }

    @Test
    fun squareIdentitySurvivesFlippingTheBoard() {
        val clicked = mutableListOf<Int>()
        showBoard(flipped = true, onSquareClick = { clicked.add(it) })

        composeTestRule.onNodeWithTag("square_e7").performClick()
        assertEquals(listOf(Square.fromName("e7")), clicked)
    }
}
