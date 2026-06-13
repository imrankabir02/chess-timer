package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performScrollTo
import com.example.ui.CustomClockBuilderDialog
import com.example.model.TimingStyle
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomClockBuilderDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testMinutesIncrementAndDecrement() {
        var savedTotalTimeMs: Long? = null
        var savedStyle: TimingStyle? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                CustomClockBuilderDialog(
                    onDismiss = {},
                    onSave = { _, totalTimeMs, style, _, _ ->
                        savedTotalTimeMs = totalTimeMs
                        savedStyle = style
                    }
                )
            }
        }

        // By default, minutes starts at 5.0. Let's click "+" twice to make it 7.
        composeTestRule.onNodeWithTag("minutes_plus").performClick()
        composeTestRule.onNodeWithTag("minutes_plus").performClick()

        // Let's click "-" once to make it 6.
        composeTestRule.onNodeWithTag("minutes_minus").performClick()

        // Scroll to and click the submit button
        composeTestRule.onNodeWithTag("apply_preset_submit").performScrollTo().performClick()

        // 6 minutes should equal 360,000 ms (6 * 60 * 1000)
        assertEquals(360000L, savedTotalTimeMs)
        assertEquals(TimingStyle.NONE, savedStyle)
    }

    @Test
    fun testSecondsIncrementAndDecrement() {
        var savedTotalTimeMs: Long? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                CustomClockBuilderDialog(
                    onDismiss = {},
                    onSave = { _, totalTimeMs, _, _, _ ->
                        savedTotalTimeMs = totalTimeMs
                    }
                )
            }
        }

        // Minutes starts at 5.0 (300,000 ms). Seconds starts at 0.0.
        // Let's click seconds "+" three times to make it 3 seconds.
        composeTestRule.onNodeWithTag("seconds_plus").performClick()
        composeTestRule.onNodeWithTag("seconds_plus").performClick()
        composeTestRule.onNodeWithTag("seconds_plus").performClick()

        // Let's click seconds "-" once to make it 2 seconds.
        composeTestRule.onNodeWithTag("seconds_minus").performClick()

        // Scroll to and click submit
        composeTestRule.onNodeWithTag("apply_preset_submit").performScrollTo().performClick()

        // Total time should be 5 mins (300,000 ms) + 2 seconds (2,000 ms) = 302,000 ms
        assertEquals(302000L, savedTotalTimeMs)
    }
}
