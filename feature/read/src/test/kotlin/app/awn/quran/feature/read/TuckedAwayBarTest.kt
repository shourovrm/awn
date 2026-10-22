package app.awn.quran.feature.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.BottomBarStack
import app.awn.quran.feature.read.quran.BusyBarButton
import app.awn.quran.feature.read.quran.BusyState
import app.awn.quran.feature.read.quran.TuckedAwayWhenHidden
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A busy bar that the page tap hides must stay composed (the recite bar ends the recitation when it
 * leaves the composition), take no room in the bottom stack, and be replaced by a corner button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class TuckedAwayBarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun aTuckedAwayBarStaysComposedAndReportsNoHeight() {
        var isShown by mutableStateOf(true)
        var reportedPx = -1
        var leftTheComposition = false
        composeRule.setContent {
            BottomBarStack(onHeightChanged = { reportedPx = it }) {
                TuckedAwayWhenHidden(isShown) {
                    DisposableEffect(Unit) { onDispose { leftTheComposition = true } }
                    Box(Modifier.fillMaxWidth().height(120.dp))
                }
            }
        }
        composeRule.waitForIdle()
        assertFalse(reportedPx == 0)

        isShown = false
        composeRule.waitForIdle()

        assertEquals(0, reportedPx)
        assertFalse(leftTheComposition)
    }

    @Test
    fun theCornerButtonReportsATap() {
        var taps = 0
        composeRule.setContent {
            AwnTheme { BusyBarButton(state = BusyState.AudioPlaying, onToggle = { taps += 1 }) }
        }

        composeRule.onNodeWithContentDescription("Pause").performClick()

        assertEquals(1, taps)
    }
}
