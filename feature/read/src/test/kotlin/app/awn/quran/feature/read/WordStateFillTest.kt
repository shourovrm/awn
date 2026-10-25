package app.awn.quran.feature.read

import androidx.compose.ui.graphics.Color
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnReciteFills
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.awnFieldColors
import app.awn.quran.feature.read.page.LineToken
import app.awn.quran.feature.read.page.PageDecorations
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.correctedWordRects
import app.awn.quran.feature.read.page.focusedWordRect
import app.awn.quran.feature.read.page.layoutPage
import app.awn.quran.feature.read.page.reciteFillColor
import app.awn.quran.feature.read.page.wordColor
import app.awn.quran.feature.read.page.wordStateFills
import app.awn.quran.tracker.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordStateFillTest {
    private fun word(position: Int) = LineToken.WordToken(WordKey(2, 1, position), "w$position")

    private val geometry =
        layoutPage(
            rows = listOf(PageRow.TextRow(1, List(4) { word(it + 1) })),
            isCentredBlock = false,
            widthPx = 1000f,
            heightPx = 1500f,
            tokenWidthEm = { 2f },
        )

    private val awnField = awnFieldColors(AwnField.Paper, ColourMode.Awn)

    private fun statesOf(vararg states: Pair<Int, WordState>): PageDecorations {
        val byKey = states.associate { (position, state) -> WordKey(2, 1, position) to state }
        return PageDecorations(wordStates = { key -> byKey[key] ?: WordState.Neutral })
    }

    @Test
    fun eachRecitedWordGetsOneFillTheSizeOfItsSlot() {
        val decorations =
            statesOf(1 to WordState.Confirmed, 2 to WordState.Mispronounced, 3 to WordState.Wrong)

        val fills = wordStateFills(geometry, decorations, awnField)

        assertEquals(3, fills.size)
        assertEquals(
            listOf(
                AwnReciteFills.dayConfirmed,
                AwnReciteFills.dayTentative,
                AwnReciteFills.dayWrong,
            ),
            fills.map { it.color },
        )
        for (fill in fills) assertEquals(geometry.slotHeightPx, fill.rect.height, 0.01f)
        assertEquals(geometry.wordRect(WordKey(2, 1, 1)), fills[0].rect)
    }

    @Test
    fun neutralAndHiddenWordsGetNoStateFill() {
        val decorations = statesOf(1 to WordState.Neutral, 2 to WordState.Hidden)

        assertTrue(wordStateFills(geometry, decorations, awnField).isEmpty())
    }

    @Test
    fun nightFieldsUseTheNightFamily() {
        for (mode in listOf(ColourMode.Dark, ColourMode.Black)) {
            val field = awnFieldColors(AwnField.Paper, mode)
            assertEquals(AwnReciteFills.nightWrong, reciteFillColor(WordState.Wrong, field))
        }
        for (mode in listOf(ColourMode.Awn, ColourMode.Light)) {
            val field = awnFieldColors(AwnField.Paper, mode)
            assertEquals(AwnReciteFills.dayWrong, reciteFillColor(WordState.Wrong, field))
        }
    }

    @Test
    fun theFillsAreSoftSoTheInkStaysReadable() {
        val tokens =
            listOf(
                AwnReciteFills.dayConfirmed,
                AwnReciteFills.dayTentative,
                AwnReciteFills.dayWrong,
                AwnReciteFills.nightConfirmed,
                AwnReciteFills.nightTentative,
                AwnReciteFills.nightWrong,
            )
        for (token in tokens) assertTrue("alpha ${token.alpha}", token.alpha in 0.2f..0.4f)
    }

    @Test
    fun theInkKeepsItsColourExceptWhereAHiddenBlockCoversIt() {
        assertEquals(awnField.content, wordColor(WordState.Confirmed, awnField))
        assertEquals(awnField.content, wordColor(WordState.Wrong, awnField))
        assertEquals(Color.Transparent, wordColor(WordState.Hidden, awnField))
        assertNull(reciteFillColor(WordState.Neutral, awnField))
    }

    @Test
    fun theFocusedWordGetsARingTheSizeOfItsSlot() {
        val decorations = PageDecorations(focusedWord = WordKey(2, 1, 3))

        assertEquals(geometry.wordRect(WordKey(2, 1, 3)), focusedWordRect(geometry, decorations))
        assertNull(focusedWordRect(geometry, PageDecorations(focusedWord = WordKey(2, 9, 1))))
        assertNull(focusedWordRect(geometry, PageDecorations()))
    }

    @Test
    fun aCorrectedWordGetsAnOutlineAndAPassedOverWordNoFill() {
        val decorations =
            PageDecorations(correctedWords = setOf(WordKey(2, 1, 3), WordKey(2, 9, 1)))

        assertEquals(
            listOf(geometry.wordRect(WordKey(2, 1, 3))),
            correctedWordRects(geometry, decorations),
        )
        assertNull(reciteFillColor(WordState.Unread, awnField))
    }
}
