package app.awn.quran.feature.read

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.QuranScreen
import app.awn.quran.feature.read.quran.QuranScreenSlots
import app.awn.quran.feature.read.quran.QuranTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val RECITE_BAR_MARK = "recite bar"
private const val HIFZ_BAR_MARK = "hifz bar"

/** The `hifzBar` slot takes the place of the recite bar while recite mode is on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class HifzBarSlotTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val fixture = ReadFixture()

    private fun show(slots: QuranScreenSlots) {
        composeRule.setContent {
            AwnTheme { QuranScreen(fixture.dependencies, slots, target = QuranTarget.Page(1)) }
        }
        composeRule.waitForIdle()
    }

    private val reciting =
        QuranScreenSlots.Empty.copy(
            reciteBar = { Text(RECITE_BAR_MARK) },
            isReciteActive = true,
        )

    @Test
    fun withoutAHifzBarTheReciteBarShows() {
        show(reciting)

        composeRule.onNodeWithText(RECITE_BAR_MARK).assertExists()
    }

    @Test
    fun aHifzBarReplacesTheReciteBarInReciteMode() {
        show(reciting.copy(hifzBar = { Text(HIFZ_BAR_MARK) }))

        composeRule.onNodeWithText(HIFZ_BAR_MARK).assertExists()
        composeRule.onNodeWithText(RECITE_BAR_MARK).assertDoesNotExist()
    }

    @Test
    fun aHifzBarDoesNotShowOutsideReciteMode() {
        show(reciting.copy(isReciteActive = false, hifzBar = { Text(HIFZ_BAR_MARK) }))

        composeRule.onNodeWithText(HIFZ_BAR_MARK).assertDoesNotExist()
    }
}
