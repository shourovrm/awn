package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.MyWordMark
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.awnFieldColors
import app.awn.quran.feature.read.page.LineToken
import app.awn.quran.feature.read.page.PageDecorations
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.layoutPage
import app.awn.quran.feature.read.page.myWordFillColor
import app.awn.quran.feature.read.page.myWordFills
import app.awn.quran.feature.read.quran.PageContent
import app.awn.quran.feature.read.quran.wordKeysOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Show my words": which words of a page are asked about, and the fill each mark gets. */
class MyWordMarksTest {

    private fun word(position: Int) = Word(WordKey(1, 1, position), "w$position", null, null)

    private val layout =
        PageLayout(
            page = 1,
            script = ScriptId.V2,
            lines =
                listOf(
                    PageLine(0, listOf(LineItem.SurahHeader(1), LineItem.Basmala(1))),
                    PageLine(1, listOf(LineItem.WordItem(word(1)), LineItem.WordItem(word(2)))),
                    PageLine(
                        2,
                        listOf(LineItem.WordItem(word(3)), LineItem.AyahEnd(AyahRef(1, 1))),
                    ),
                ),
        )

    @Test
    fun theWordsOfAMushafPageAreItsWordItemsInReadingOrder() {
        assertEquals(
            listOf(WordKey(1, 1, 1), WordKey(1, 1, 2), WordKey(1, 1, 3)),
            wordKeysOf(layout),
        )
    }

    @Test
    fun aListPageThatIsNotLoadedHasNoWords() {
        assertTrue(wordKeysOf(null as PageContent?).isEmpty())
    }

    @Test
    fun aKnownWordIsFilledStrongerThanAWordBeingLearned() {
        ColourMode.entries.forEach { mode ->
            val field = awnFieldColors(AwnField.Paper, mode)

            val known = myWordFillColor(MyWordMark.Known, field)
            val learning = myWordFillColor(MyWordMark.Learning, field)

            assertNotEquals(known, learning)
            assertTrue(known.alpha > learning.alpha)
        }
    }

    @Test
    fun theKnownFillIsGreenerOnDayColoursAndLifterOnNightColours() {
        val day = myWordFillColor(MyWordMark.Known, awnFieldColors(AwnField.Paper, ColourMode.Awn))
        val night =
            myWordFillColor(MyWordMark.Known, awnFieldColors(AwnField.Paper, ColourMode.Dark))

        assertNotEquals(day.copy(alpha = 1f), night.copy(alpha = 1f))
    }

    private val geometry =
        layoutPage(
            rows =
                listOf(
                    PageRow.TextRow(1, List(4) { LineToken.WordToken(WordKey(2, 1, it + 1), "w") }),
                ),
            isCentredBlock = false,
            widthPx = 1000f,
            heightPx = 1500f,
            tokenWidthEm = { 2f },
        )

    @Test
    fun theMushafPageFillsEachMarkedWordSlotAndNoOther() {
        val field = awnFieldColors(AwnField.Paper, ColourMode.Awn)
        val decorations =
            PageDecorations(
                myWordMarks =
                    mapOf(
                        WordKey(2, 1, 1) to MyWordMark.Known,
                        WordKey(2, 1, 3) to MyWordMark.Learning,
                    ),
            )

        val fills = myWordFills(geometry, decorations, field)

        assertEquals(2, fills.size)
        assertEquals(myWordFillColor(MyWordMark.Known, field), fills[0].color)
        assertEquals(myWordFillColor(MyWordMark.Learning, field), fills[1].color)
        assertEquals(geometry.wordRect(WordKey(2, 1, 1)), fills[0].rect)
    }

    @Test
    fun aPageWithoutMarksHasNoMyWordFills() {
        val field = awnFieldColors(AwnField.Paper, ColourMode.Awn)

        assertTrue(myWordFills(geometry, PageDecorations(), field).isEmpty())
    }
}
