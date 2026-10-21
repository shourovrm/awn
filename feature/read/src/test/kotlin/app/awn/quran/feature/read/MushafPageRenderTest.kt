package app.awn.quran.feature.read

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.MushafPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MushafPageRenderTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val fixture = ReadFixture()

    @Test
    fun pageOneShowsItsWordsAndTheAyahNumber() {
        val layout = runBlocking { fixture.text.pageLayout(1, ScriptId.QpcHafs) }

        composeRule.setContent {
            AwnTheme {
                MushafPage(
                    layout = layout,
                    chapters = fixture.text.chapterList,
                    fontFamily = null,
                    plainFamily = null,
                    showPageInfo = false,
                    onWordClick = {},
                )
            }
        }

        composeRule.onNodeWithText(ReadFixture.FIRST_WORD_DRAWN).assertIsDisplayed()
        composeRule.onNodeWithText(ReadFixture.SECOND_WORD).assertIsDisplayed()
        composeRule.onNodeWithText("\u0661").assertIsDisplayed()
    }

    @Test
    fun tappingAWordReportsItsKey() {
        val layout = runBlocking { fixture.text.pageLayout(1, ScriptId.QpcHafs) }
        val tapped = mutableListOf<WordKey>()

        composeRule.setContent {
            AwnTheme {
                MushafPage(
                    layout = layout,
                    chapters = fixture.text.chapterList,
                    fontFamily = null,
                    plainFamily = null,
                    showPageInfo = false,
                    onWordClick = { tapped += it },
                )
            }
        }
        composeRule.onNodeWithText(ReadFixture.SECOND_WORD).performClick()

        assertEquals(listOf(WordKey(1, 1, 2)), tapped)
    }
}
