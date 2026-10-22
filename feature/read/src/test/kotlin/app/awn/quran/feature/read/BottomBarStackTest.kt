package app.awn.quran.feature.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.awn.quran.feature.read.quran.BottomBarStack
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The recite bar covered the last lines of the page. The Quran screen kept a height for the bars
 * stacked at the bottom, meant to make the lists end above them and to move the Mushaf page up when
 * the word being recited goes under the bar, but nothing ever set it, so it stayed 0. The bottom
 * stack now reports its measured height.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class BottomBarStackTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun theStackReportsTheHeightOfWhatItHolds() {
        var reportedPx = -1
        var expectedPx = 0
        composeRule.setContent {
            expectedPx = with(LocalDensity.current) { 120.dp.toPx().roundToInt() }
            BottomBarStack(onHeightChanged = { reportedPx = it }) {
                Box(Modifier.fillMaxWidth().height(120.dp))
            }
        }
        composeRule.waitForIdle()

        assertEquals(expectedPx, reportedPx)
    }

    @Test
    fun anEmptyStackReportsZero() {
        var reportedPx = -1
        composeRule.setContent { BottomBarStack(onHeightChanged = { reportedPx = it }) {} }
        composeRule.waitForIdle()

        assertEquals(0, reportedPx)
    }
}

/** The Quran screen must put its bottom bars in [BottomBarStack] and keep the height it reports. */
class BottomBarStackWiringTest {

    private val source =
        File("src/main/kotlin/app/awn/quran/feature/read/quran/QuranScreen.kt").readText()

    @Test
    fun theQuranScreenStoresTheMeasuredHeight() {
        assertTrue(source.contains("BottomBarStack("))
        assertTrue(
            Regex("""onHeightChanged\s*=\s*\{\s*height\s*->[\s\S]*?onBottomBarMeasured\(height""")
                .containsMatchIn(source),
        )
    }
}
