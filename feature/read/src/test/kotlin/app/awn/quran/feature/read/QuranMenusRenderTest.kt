package app.awn.quran.feature.read

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.QuranScreen
import app.awn.quran.feature.read.quran.QuranScreenSlots
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.settings.PageView
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The layers menu, the text-size rows and the Go to page dialog on the real screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuranMenusRenderTest {
    @get:Rule val composeRule = createComposeRule()

    private val fixture = ReadFixture()

    private fun showScreen(view: PageView) {
        runBlocking { fixture.store.update { it.copy(pageView = view) } }
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
    }

    @Test
    fun theLayersMenuTicksArabicEachTranslationTransliterationAndWordByWord() {
        showScreen(PageView.LineByLine)

        composeRule.onNodeWithContentDescription("Layers").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Arabic").assertIsDisplayed()
        composeRule.onNodeWithText("Saheeh International").assertIsDisplayed()
        composeRule.onNodeWithText("Transliteration").assertIsDisplayed()
        composeRule.onNodeWithText("Word by word").assertIsDisplayed()
    }

    @Test
    fun theMushafViewHasNoLayersButton() {
        showScreen(PageView.Mushaf)

        composeRule.onAllNodesWithContentDescription("Layers").assertCountEquals(0)
    }

    @Test
    fun lineByLineTextSizeHasOneRowPerVisibleLayer() {
        showScreen(PageView.LineByLine)

        composeRule.onNodeWithContentDescription("Text size").performClick()
        composeRule.waitForIdle()

        // Arabic, translations, transliteration and word by word are all on by default.
        composeRule.onAllNodesWithContentDescription("Smaller").assertCountEquals(4)
    }

    @Test
    fun mushafTextSizeHasTheArabicRowOnly() {
        showScreen(PageView.Mushaf)

        composeRule.onNodeWithContentDescription("Text size").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Smaller").assertCountEquals(1)
    }

    @Test
    fun goToPageOpensFocusedAndFilledWithTheCurrentPage() {
        showScreen(PageView.Mushaf)

        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Go to page").performClick()
        composeRule.waitForIdle()

        val pageField = composeRule.onAllNodes(hasSetTextAction())[0]
        pageField.assertIsFocused()
        pageField.assertTextContains("1")
    }
}
