package app.awn.quran.core.quranfont

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShrinkToFitTest {
    @get:Rule val composeRule = createComposeRule()

    private var measuredWidthPx by mutableStateOf(0)

    private fun show(availablePx: Int, contentPx: Int) {
        composeRule.setContent {
            val density = LocalDensity.current
            val available = with(density) { availablePx.toDp() }
            val content = with(density) { contentPx.toDp() }
            Box(Modifier.width(available)) {
                ShrinkToFit(Modifier.onSizeChanged { measuredWidthPx = it.width }) {
                    Box(Modifier.requiredWidth(content).testTag("content"))
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun contentWiderThanTheSpaceIsScaledDownToIt() {
        show(availablePx = 200, contentPx = 400)

        assertEquals(200, measuredWidthPx)
    }

    @Test
    fun contentThatFitsKeepsItsSize() {
        show(availablePx = 400, contentPx = 200)

        assertEquals(200, measuredWidthPx)
    }
}
