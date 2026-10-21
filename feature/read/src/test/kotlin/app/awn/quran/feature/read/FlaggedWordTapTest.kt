package app.awn.quran.feature.read

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.QuranScreen
import app.awn.quran.feature.read.quran.QuranScreenSlots
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tap on a flagged word while reciting opens the review card, not the plain word card. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class FlaggedWordTapTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val fixture = ReadFixture()
    private val flaggedWord = WordKey(1, 1, 1)
    private val focused = mutableListOf<WordKey>()

    private fun recitingSlots() =
        QuranScreenSlots.Empty.copy(
            isReciteActive = true,
            reciteWordStates = { mapOf(flaggedWord to WordState.Wrong) },
            reciteWordProblems = { mapOf(flaggedWord to WordProblem.Skipped) },
            onReciteFocusWord = { focused += it },
        )

    private fun showScreen(pageView: PageView) {
        runBlocking { fixture.store.update { it.copy(pageView = pageView) } }
        composeRule.setContent {
            AwnTheme {
                QuranScreen(fixture.dependencies, recitingSlots(), target = QuranTarget.Page(1))
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun onTheMushafPageTheTapAsksForTheReviewCard() {
        showScreen(PageView.Mushaf)

        composeRule.onNodeWithText(ReadFixture.FIRST_WORD_DRAWN).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(flaggedWord), focused)
    }

    @Test
    fun inTheLineViewTheTapAsksForTheReviewCard() {
        showScreen(PageView.LineByLine)

        composeRule.onNodeWithText(ReadFixture.FIRST_WORD_DRAWN).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(flaggedWord), focused)
    }
}
