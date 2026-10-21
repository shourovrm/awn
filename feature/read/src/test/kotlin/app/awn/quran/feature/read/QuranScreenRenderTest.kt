package app.awn.quran.feature.read

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.QuranScreen
import app.awn.quran.feature.read.quran.QuranScreenSlots
import app.awn.quran.feature.read.quran.QuranTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class QuranScreenRenderTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val fixture = ReadFixture()

    @Test
    fun openingAtAPageShowsItsWordsAndPressingTheViewButtonKeepsThePage() {
        var searchOpened = 0
        val slots = QuranScreenSlots.Empty.copy(onOpenSearch = { searchOpened++ })
        composeRule.setContent {
            AwnTheme {
                QuranScreen(fixture.dependencies, slots, target = QuranTarget.Page(1))
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(ReadFixture.FIRST_WORD_DRAWN).assertIsDisplayed()
        // The bars are shown for two seconds when the screen opens.
        assertEquals(0, searchOpened)
    }

    @Test
    fun lineByLineViewShowsTheReferenceChipAndTheSurahBand() {
        fixture.store.let { store ->
            kotlinx.coroutines.runBlocking {
                store.update {
                    it.copy(pageView = app.awn.quran.feature.read.settings.PageView.LineByLine)
                }
            }
        }
        composeRule.setContent {
            AwnTheme {
                QuranScreen(
                    fixture.dependencies,
                    QuranScreenSlots.Empty,
                    target = QuranTarget.Page(1),
                )
            }
        }
        composeRule.waitForIdle()

        // The title in the top bar (shown for two seconds at the start) and the surah band.
        composeRule.onAllNodesWithText("Al-Fatihah")[0].assertIsDisplayed()
        composeRule.onNodeWithText("1:1").assertIsDisplayed()
    }

    @Test
    fun theTopBarOffersTheSettingsIconWhenTheBarsAreShown() {
        composeRule.setContent {
            AwnTheme {
                QuranScreen(fixture.dependencies, QuranScreenSlots.Empty)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed().performClick()
    }
}
