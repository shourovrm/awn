package app.awn.quran.feature.read

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
@Config(sdk = [34])
class QuranScreenRenderTest {
    @get:Rule val composeRule = createComposeRule()

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

        composeRule.onNodeWithText(ReadFixture.FIRST_WORD).assertIsDisplayed()
        // Opened at a target, so the bars start hidden; a tap on the page would show them.
        assertEquals(0, searchOpened)
    }

    @Test
    fun translationViewShowsTheReferenceChipAndTheSurahBand() {
        fixture.store.let { store ->
            kotlinx.coroutines.runBlocking {
                store.update {
                    it.copy(pageView = app.awn.quran.feature.read.settings.PageView.Translation)
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

        composeRule.onNodeWithText("Al-Fatihah").assertIsDisplayed()
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
