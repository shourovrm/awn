package app.awn.quran.feature.read

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import app.awn.quran.feature.read.quran.rememberFixedLongPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose restarts every touch handler whose view configuration changes, which cancels a drag in
 * progress. The Quran screen recomposes whenever a page loads, so its configuration must stay the
 * same object or a page swipe that starts while a page loads does nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LongPressConfigurationTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    @Test
    fun theConfigurationIsTheSameObjectAfterARecomposition() {
        var recompositions by mutableIntStateOf(0)
        val seen = mutableListOf<ViewConfiguration>()
        composeRule.setContent {
            // Read so that changing it recomposes this block.
            recompositions.let { seen += rememberFixedLongPress() }
        }
        composeRule.runOnIdle { recompositions++ }
        composeRule.waitForIdle()

        assertEquals(2, seen.size)
        assertSame(seen[0], seen[1])
        assertEquals(500L, seen[1].longPressTimeoutMillis)
    }
}
