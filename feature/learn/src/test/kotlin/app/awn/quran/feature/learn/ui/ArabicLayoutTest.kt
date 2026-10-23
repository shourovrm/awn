package app.awn.quran.feature.learn.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Arabic letters in answers are drawn as large as the letters of a grid cell, and a bullet sits on
 * the first line of its item even when that line holds a larger Arabic run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArabicLayoutTest {

    @get:Rule val composeRule = createComposeRule()

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = composeRule.onNodeWithText(text, substring = true).fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.first()
    }

    @Test
    fun anArabicAnswerLetterIsAsLargeAsAGridLetter() {
        composeRule.setContent {
            AwnTheme(field = LearnField) {
                AnswerRow(markup = "[[ب]]", state = AnswerRowState.Open, onClick = null)
            }
        }
        composeRule.waitForIdle()

        val layout = layoutOf("ب")
        val letterSpans = layout.layoutInput.text.spanStyles
        assertTrue(letterSpans.isNotEmpty())
        assertTrue(
            "answer letter size ${letterSpans.first().item.fontSize}",
            letterSpans.first().item.fontSize >= GRID_LETTER_SIZE,
        )
    }

    @Test
    fun aBulletSharesTheFirstBaselineOfItsItem() {
        composeRule.setContent {
            AwnTheme(field = LearnField) { BulletList(listOf("[[ب]] [[ت]]: one dot below")) }
        }
        composeRule.waitForIdle()

        val bulletNode = composeRule.onNodeWithText("•").fetchSemanticsNode()
        val itemNode = composeRule.onNodeWithText("one dot below", substring = true).fetchSemanticsNode()
        val bulletBaseline = bulletNode.boundsInRoot.top + layoutOf("•").getLineBaseline(0)
        val itemBaseline = itemNode.boundsInRoot.top + layoutOf("one dot below").getLineBaseline(0)

        assertEquals(itemBaseline, bulletBaseline, 1f)
    }

    private companion object {
        val GRID_LETTER_SIZE = 26.sp
    }
}
