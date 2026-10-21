package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.headerSurah
import app.awn.quran.feature.read.page.pageHeaderInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageHeaderTest {

    private fun word(surah: Int, ayah: Int, position: Int) =
        LineItem.WordItem(Word(WordKey(surah, ayah, position), "w", null, null))

    private fun layout(page: Int, vararg items: LineItem) =
        PageLayout(page, ScriptId.V2, listOf(PageLine(1, items.toList())))

    @Test
    fun pageWithOneSurahMidwayNamesThatSurah() {
        assertEquals(3, headerSurah(layout(50, word(3, 41, 1), word(3, 41, 2))))
    }

    @Test
    fun surahThatStartsOnThePageWinsOverTheOneThatEndsOnIt() {
        // Page 77: the page starts with 4:1, the previous page ended in surah 3.
        assertEquals(4, headerSurah(layout(77, word(4, 1, 1), word(4, 1, 2))))
        // A page that begins with the end of surah 3 and then starts 4: surah 4 is named.
        assertEquals(4, headerSurah(layout(77, word(3, 200, 4), word(4, 1, 1))))
    }

    @Test
    fun pageWithThreeStartsNamesTheFirstOfThem() {
        val page604 =
            layout(
                604,
                word(112, 1, 1),
                word(112, 4, 2),
                word(113, 1, 1),
                word(114, 1, 1),
                LineItem.AyahEnd(AyahRef(114, 6)),
            )

        assertEquals(112, headerSurah(page604))
    }

    @Test
    fun surahContinuingOnThePageIsNamedWhenNoneStarts() {
        assertEquals(2, headerSurah(layout(10, word(2, 60, 3), word(2, 61, 1))))
    }

    @Test
    fun layoutWithoutWordsHasNoSurah() {
        assertEquals(0, headerSurah(layout(5, LineItem.SurahHeader(4))))
    }

    @Test
    fun headerCarriesTheJuzAndTheQuarterThatStartsOnThePage() {
        val page5 = layout(5, word(2, 26, 1))
        val info = pageHeaderInfo(page5)
        assertEquals(1, info.juz)
        assertEquals(2, info.startingQuarter?.number)

        assertNull(pageHeaderInfo(layout(3, word(2, 6, 1))).startingQuarter)
        assertEquals(2, pageHeaderInfo(layout(22, word(2, 142, 1))).juz)
        assertEquals(
            0,
            pageHeaderInfo(layout(22, word(2, 142, 1))).startingQuarter?.quartersIntoHizb,
        )
    }
}
