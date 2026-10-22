package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.text.isCombiningMark
import app.awn.quran.core.ui.text.normalizeFallbackText
import app.awn.quran.core.ui.text.startsWithCombiningMark
import app.awn.quran.feature.read.page.LineToken
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.buildPageRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Quran.com Unicode text puts a space before each pause mark and starts 199 words with the
 * quarter sign U+06DE. A combining mark with no letter before it draws on a dotted circle, so the
 * fallback text must never hand one to the text engine.
 */
class FallbackTextTest {

    private fun chars(vararg codePoints: Int): String =
        codePoints.map { it.toChar() }.joinToString("")

    private val reh = 0x0631
    private val fatha = 0x064E
    private val space = 0x20
    private val pauseMarks = listOf(0x06D6, 0x06D7, 0x06D8, 0x06D9, 0x06DA, 0x06DB, 0x06DC)

    @Test
    fun everyPauseMarkLosesTheSpaceBeforeIt() {
        for (mark in pauseMarks) {
            val word = chars(reh, fatha, space, mark)

            val drawn = normalizeFallbackText(word)

            assertEquals("U+${mark.toString(16)}", chars(reh, fatha, mark), drawn)
            assertFalse(startsWithCombiningMark(drawn))
        }
    }

    @Test
    fun twoMarksAfterSpacesStayOnTheLetter() {
        // 36:52 word 6 is "مَّرْقَدِنَا ۜ ۗ": two stop marks, each after a space.
        val drawn = normalizeFallbackText(chars(reh, space, 0x06DC, space, 0x06D7))

        assertEquals(chars(reh, 0x06DC, 0x06D7), drawn)
    }

    @Test
    fun doubleSpaceBeforeAMarkIsRemovedToo() {
        assertEquals(chars(reh, 0x06D6), normalizeFallbackText(chars(reh, space, space, 0x06D6)))
    }

    @Test
    fun marksInsideAWordAreKept() {
        // The silent-letter circle stays on its letter, in the Hafs font's code.
        val word = chars(0x0642, 0x064E, 0x0627, 0x06DF, 0x0644)
        assertEquals(chars(0x0642, 0x064E, 0x0627, 0x0652, 0x0644), normalizeFallbackText(word))
    }

    @Test
    fun theQuarterSignStaysBecauseItIsASymbolWithAGlyphOfItsOwn() {
        val word = chars(0x06DE, space, 0x0625, 0x0650, 0x0646)

        assertFalse(isCombiningMark(0x06DE.toChar()))
        assertEquals(word, normalizeFallbackText(word))
        assertFalse(startsWithCombiningMark(word))
    }

    @Test
    fun sajdahSignAfterASpaceIsKeptAsASign() {
        // U+06E9 is a symbol, not a combining mark: it needs no letter under it.
        assertFalse(isCombiningMark(0x06E9.toChar()))
        assertEquals(chars(reh, space, 0x06E9), normalizeFallbackText(chars(reh, space, 0x06E9)))
    }

    @Test
    fun directionMarkIsRemoved() {
        assertEquals(
            chars(reh, space, 0x06E9),
            normalizeFallbackText(chars(reh, space, 0x06E9, 0x200F)),
        )
    }

    @Test
    fun everyMarkTypeOfTheDataThatCombinesIsClassifiedAsCombining() {
        val combining =
            listOf(0x06ED, 0x06DF, 0x06E2, 0x06DA, 0x06D6, 0x06D7, 0x06D9, 0x06E0) +
                listOf(0x06E7, 0x06D8, 0x06DB, 0x06DC)
        for (mark in combining) {
            assertTrue("U+${mark.toString(16)}", isCombiningMark(mark.toChar()))
        }
    }

    @Test
    fun noWordTokenOfAFallbackPageStartsWithACombiningMark() {
        val words =
            listOf(
                word(1, chars(0x06DE, space, 0x0625, 0x0650, 0x0646)),
                word(2, chars(reh, 0x064E, space, 0x06DB)),
                word(3, chars(0x0642, 0x0644, 0x06D6)),
            )
        val layout =
            PageLayout(
                2,
                ScriptId.QpcHafs,
                listOf(
                    PageLine(
                        1,
                        words.map { LineItem.WordItem(it) } + LineItem.AyahEnd(AyahRef(2, 1)),
                    ),
                ),
            )

        val tokens =
            buildPageRows(layout, hasGlyphFont = false)
                .filterIsInstance<PageRow.TextRow>()
                .flatMap { it.tokens }

        val wordTexts = tokens.filterIsInstance<LineToken.WordToken>().map { it.text }
        assertEquals(3, wordTexts.size)
        for (text in wordTexts) {
            assertFalse(text, startsWithCombiningMark(text))
            assertFalse(text, text.endsWith(' '))
        }
    }

    @Test
    fun glyphPagesDrawTheirCodesUntouchedWithThePrintedEndSign() {
        val code = chars(0xFC41, 0xFC42)
        val word = Word(WordKey(2, 1, 1), "x", null, code)
        val layout =
            PageLayout(
                3,
                ScriptId.V2,
                listOf(
                    PageLine(
                        1,
                        listOf(
                            LineItem.WordItem(word),
                            LineItem.AyahEnd(AyahRef(2, 1), chars(0xFC43)),
                        ),
                    ),
                ),
            )

        val tokens =
            buildPageRows(layout, hasGlyphFont = true)
                .filterIsInstance<PageRow.TextRow>()
                .single()
                .tokens

        assertEquals(code, (tokens[0] as LineToken.WordToken).text)
        assertEquals(chars(0xFC43), (tokens[1] as LineToken.RosetteToken).glyphCode)
    }

    @Test
    fun silentLetterCircleAndSukunUseTheHafsFontCodes() {
        // 4:176 "ٱمْرُؤٌا۟": the Hafs font draws U+06DF as a dotted ring. In its own text the
        // silent-letter circle is U+0652 and sukun is U+06E1, so the two codes swap at once.
        val alef = 0x0627
        val meem = 0x0645
        val source = chars(meem, 0x0652, reh, alef, 0x06DF)

        assertEquals(chars(meem, 0x06E1, reh, alef, 0x0652), normalizeFallbackText(source))
    }

    @Test
    fun rareSeenAndIshmamMarksUseTheHafsFontCodes() {
        // 52:37 writes the small seen with U+06E3 and 12:11 the ishmam sign with U+06EB; the
        // Hafs font draws both as dotted rings and expects U+06DC and U+06EC.
        val sad = 0x0635
        val meem = 0x0645

        assertEquals(chars(sad, 0x06DC), normalizeFallbackText(chars(sad, 0x06E3)))
        assertEquals(chars(meem, 0x06EC), normalizeFallbackText(chars(meem, 0x06EB)))
    }

    private fun word(position: Int, text: String) = Word(WordKey(2, 1, position), text, null, null)
}
