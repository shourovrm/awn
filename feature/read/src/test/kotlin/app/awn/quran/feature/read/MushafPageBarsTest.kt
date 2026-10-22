package app.awn.quran.feature.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.MushafPage
import app.awn.quran.feature.read.page.PageBarInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * No Quran text is drawn under the bars: a page that fits sits between the strips of the bars, and
 * a page that scrolls has the strips as padding at both ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MushafPageBarsTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val wordsPerLine = 3
    private val lineCount = 15
    private val barInsets = PageBarInsets(topPx = 100, bottomPx = 160)

    private fun word(line: Int, position: Int) =
        Word(WordKey(2, line, position), "L${line}W$position", null, null)

    private fun fullPage() =
        PageLayout(
            5,
            ScriptId.QpcHafs,
            (1..lineCount).map { line ->
                PageLine(
                    line,
                    (1..wordsPerLine).map { position ->
                        LineItem.WordItem(word(line, position))
                    },
                )
            },
        )

    private fun show(width: Dp, height: Dp, sizeFactor: Float = 1f) {
        composeRule.setContent {
            AwnTheme {
                Box(Modifier.requiredSize(width, height).testTag(AREA_TAG)) {
                    MushafPage(
                        layout = fullPage(),
                        chapters = ReadFixture().text.chapterList,
                        fontFamily = null,
                        plainFamily = null,
                        showPageInfo = false,
                        sizeFactor = sizeFactor,
                        barInsets = barInsets,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun Dp.toPx(): Float = value * composeRule.density.density

    /** The bounds of the word in px, counted from the top of the area the page is shown in. */
    private fun wordTopAndBottom(text: String): Pair<Float, Float> {
        val areaTop = composeRule.onNodeWithTag(AREA_TAG).getUnclippedBoundsInRoot().top
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        return (bounds.top - areaTop).toPx() to (bounds.bottom - areaTop).toPx()
    }

    @Test
    fun aPageThatFitsSitsBetweenTheTopAndBottomStrips() {
        show(400.dp, 800.dp)

        val (firstTop, _) = wordTopAndBottom("L1W1")
        val (_, lastBottom) = wordTopAndBottom("L${lineCount}W1")
        assertTrue("top $firstTop", firstTop >= barInsets.topPx - TEXT_REACH_PX)
        assertTrue(
            "bottom $lastBottom",
            lastBottom <= 800.dp.toPx() - barInsets.bottomPx + TEXT_REACH_PX,
        )
        assertEquals(0, scrollContainers())
    }

    @Test
    fun aSmallerSizeStaysBetweenTheStripsToo() {
        show(400.dp, 800.dp, sizeFactor = 0.7f)

        val (firstTop, _) = wordTopAndBottom("L1W1")
        val (_, lastBottom) = wordTopAndBottom("L${lineCount}W1")
        assertTrue("top $firstTop", firstTop >= barInsets.topPx - TEXT_REACH_PX)
        assertTrue(lastBottom <= 800.dp.toPx() - barInsets.bottomPx + TEXT_REACH_PX)
        val wordCount = composeRule.onAllNodesWithText("L", substring = true).fetchSemanticsNodes()
        assertEquals(wordsPerLine * lineCount, wordCount.size)
    }

    @Test
    fun aScrollingPageStartsBelowTheTopStripAndEndsAboveTheBottomStrip() {
        show(800.dp, 300.dp)

        val (firstTop, _) = wordTopAndBottom("L1W1")
        assertTrue("top $firstTop", firstTop >= barInsets.topPx - TEXT_REACH_PX)

        // The page is as wide as the screen and 1.5 times as tall; the strips are added to it.
        val pageHeightPx = 800.dp.toPx() * 1.5f
        val contentHeightPx = barInsets.topPx + pageHeightPx + barInsets.bottomPx
        val expectedScrollRange = contentHeightPx - 300.dp.toPx()
        assertEquals(expectedScrollRange, scrollRangeMax(), 1.5f)
    }

    private companion object {
        /**
         * A glyph may reach a few pixels beyond the slot of its line; a missing strip is 100 px.
         */
        const val TEXT_REACH_PX = 10f
        const val AREA_TAG = "page-area"
    }

    private fun scrollContainers(): Int =
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNodes()
            .size

    private fun scrollRangeMax(): Float {
        val node =
            composeRule
                .onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
                )
                .fetchSemanticsNodes()
                .single()
        return node.config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
    }
}
