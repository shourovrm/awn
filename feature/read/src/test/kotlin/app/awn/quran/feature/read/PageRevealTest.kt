package app.awn.quran.feature.read

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.MushafPage
import app.awn.quran.feature.read.page.ROWS_REVEALED_PER_FRAME
import app.awn.quran.feature.read.page.nextRevealedRowCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A page built ahead of the swipe adds a few rows per frame, so no single frame composes and lays
 * out the whole page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageRevealTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val lineCount = 15
    private val wordsPerLine = 3

    private fun fullPage() =
        PageLayout(
            5,
            ScriptId.QpcHafs,
            (1..lineCount).map { line ->
                PageLine(
                    line,
                    (1..wordsPerLine).map { position ->
                        LineItem.WordItem(
                            Word(WordKey(2, line, position), "L${line}W$position", null, null),
                        )
                    },
                )
            },
        )

    private fun wordCount(): Int =
        composeRule.onAllNodesWithText("L", substring = true).fetchSemanticsNodes().size

    @Test
    fun theRevealAddsAFixedNumberOfRowsPerFrameAndStopsAtTheLastRow() {
        assertEquals(ROWS_REVEALED_PER_FRAME, nextRevealedRowCount(0, 15))
        assertEquals(15, nextRevealedRowCount(14, 15))
        assertEquals(15, nextRevealedRowCount(15, 15))
    }

    @Test
    fun aPageBuiltAheadAppearsInSeveralFramesAndEndsComplete() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AwnTheme {
                MushafPage(
                    layout = fullPage(),
                    chapters = ReadFixture().text.chapterList,
                    fontFamily = null,
                    plainFamily = null,
                    showPageInfo = false,
                    revealGradually = true,
                )
            }
        }

        val counts = mutableListOf<Int>()
        repeat(FRAMES_TO_WATCH) {
            composeRule.mainClock.advanceTimeByFrame()
            counts += wordCount()
        }

        assertEquals("never shrinks", counts.sorted(), counts)
        assertTrue(
            "some frame shows part of the page: $counts",
            counts.any { it in 1 until lineCount * wordsPerLine },
        )
        assertEquals(lineCount * wordsPerLine, counts.last())
    }

    @Test
    fun aPageOnScreenShowsAllRowsAtOnce() {
        composeRule.setContent {
            AwnTheme {
                MushafPage(
                    layout = fullPage(),
                    chapters = ReadFixture().text.chapterList,
                    fontFamily = null,
                    plainFamily = null,
                    showPageInfo = false,
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(lineCount * wordsPerLine, wordCount())
    }

    private companion object {
        const val FRAMES_TO_WATCH = 40
    }
}
