package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.quranfont.textToDraw
import app.awn.quran.feature.read.page.LineToken
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.buildPageRows
import app.awn.quran.feature.read.page.frameSlotCountOf
import app.awn.quran.feature.read.page.isCentredBlockPage
import app.awn.quran.feature.read.page.pageHeaderInfo
import app.awn.quran.feature.read.quran.pageAfterPrintChange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The IndoPak prints on the Mushaf page: slots, text and header. */
class IndoPakPageTest {

    private fun word(surah: Int, ayah: Int, position: Int) =
        Word(WordKey(surah, ayah, position), "uthmani", null, null, textNastaleeq = "nastaleeq")

    /**
     * Surah 46 starts on line 1: banner and Basmalah are lines -1 and 0, as on 15-line page 502.
     */
    private val headerAbovePage =
        PageLayout(
            page = 502,
            script = ScriptId.IndoPak15,
            lines =
                listOf(
                    PageLine(-1, listOf(LineItem.SurahHeader(46))),
                    PageLine(0, listOf(LineItem.Basmala(46))),
                    PageLine(1, listOf(LineItem.WordItem(word(46, 1, 1)))),
                    PageLine(2, listOf(LineItem.WordItem(word(46, 1, 2)))),
                ),
        )

    @Test
    fun linesNumberedZeroAndBelowMoveDownSoTheFirstSlotIsOne() {
        val rows = buildPageRows(headerAbovePage, hasGlyphFont = true)

        assertEquals(listOf(1, 2, 3, 4), rows.map { it.slot })
        assertEquals(PageRow.HeaderRow(1, 46), rows[0])
        assertEquals(PageRow.BasmalaRow(2), rows[1])
    }

    @Test
    fun aPageWithAnExtraHeaderLineDividesItsHeightIntoMoreSlots() {
        val fifteenLines = headerAbovePage.copy(lines = (1..15).map { PageLine(it, emptyList()) })
        val withExtraLines =
            headerAbovePage.copy(lines = listOf(PageLine(-1, emptyList())) + fifteenLines.lines)

        assertEquals(15, frameSlotCountOf(fifteenLines))
        assertEquals(17, frameSlotCountOf(withExtraLines))
    }

    @Test
    fun theIndoPakWordsDrawTheirNastaleeqTextWhenTheFontIsThere() {
        val rows = buildPageRows(headerAbovePage, hasGlyphFont = true)
        val tokens = (rows[2] as PageRow.TextRow).tokens

        assertEquals("nastaleeq", (tokens.single() as LineToken.WordToken).text)
    }

    @Test
    fun withoutTheFontTheUthmaniTextStandsIn() {
        val text = textToDraw(word(1, 1, 1), ScriptId.IndoPak15, hasGlyphFont = false)

        assertEquals("uthmani", text)
    }

    @Test
    fun theFirstIndoPakPagesMoveUpSoTheirLinesSitInTheMiddleOfTheFrame() {
        // Page 1 of the print: the banner on line 8 and words on lines 9 to 15.
        val bottomLines =
            headerAbovePage.copy(
                page = 1,
                lines =
                    listOf(PageLine(8, listOf(LineItem.SurahHeader(1)))) +
                        (9..15).map { PageLine(it, listOf(LineItem.WordItem(word(1, 1, it)))) },
            )

        val rows = buildPageRows(bottomLines, hasGlyphFont = true)

        // Eight used lines in fifteen slots: three empty above, four below.
        assertEquals(4, rows.first().slot)
        assertEquals(11, rows.last().slot)
        assertEquals(15, frameSlotCountOf(bottomLines))
        assertEquals(8, buildPageRows(bottomLines.copy(page = 3), hasGlyphFont = true).first().slot)
    }

    @Test
    fun theFirstPagesOfAnIndoPakPrintAreNotACentredBlock() {
        val first = headerAbovePage.copy(page = 1)

        assertFalse(isCentredBlockPage(first))
        assertEquals(true, isCentredBlockPage(first.copy(script = ScriptId.V2)))
    }

    @Test
    fun theHeaderNamesTheJuzOfTheFirstAyahOnTheIndoPakPage() {
        val info = pageHeaderInfo(headerAbovePage)

        // Surah 46, ayah 1 opens the 26th juz; its first word is on the page, so does the quarter.
        assertEquals(26, info.juz)
        assertEquals(AyahRef(46, 1), info.startingQuarter?.firstAyah)
    }

    @Test
    fun changingToAnIndoPakPrintFindsThePageOfTheSameAyah() = runBlocking {
        val madani = FakeQuranTextRepository(pageByAyah = mutableMapOf(AyahRef(46, 1) to 300))
        val text =
            object : QuranTextRepository by madani {
                override suspend fun pageOf(ayah: AyahRef, script: ScriptId): Int =
                    if (script == ScriptId.IndoPak15) 250 else madani.pageOf(ayah, script)

                override fun pageCount(script: ScriptId): Int =
                    if (script == ScriptId.IndoPak15) 610 else madani.pageCount(script)
            }

        assertEquals(250, pageAfterPrintChange(text, 300, ScriptId.V2, ScriptId.IndoPak15))
        assertEquals(300, pageAfterPrintChange(text, 300, ScriptId.V2, ScriptId.V1))
    }
}
