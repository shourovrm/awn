package app.awn.quran.index

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexLoaderTest {

    private fun word(text: String, position: Int) = Word(WordKey(18, 5, position), text, null, null)

    private val chapters =
        (1..114).map { Chapter(it, "", "Surah $it", null, versesCount = 7, "makkah", true) }

    private val pageByAyah: MutableMap<AyahRef, Int> =
        (JuzStarts.ayahs + (1..114).map { AyahRef(it, 1) })
            .associateWith { it.surah * 5 - 4 }
            .toMutableMap()
            .also { it[AyahRef(18, 5)] = 294 }

    private val textPage =
        PageLayout(
            page = 294,
            script = ScriptId.V2,
            lines =
                listOf(
                    PageLine(1, listOf(LineItem.SurahHeader(18))),
                    PageLine(
                        2,
                        listOf("a", "b", "c").mapIndexed { index, text ->
                            LineItem.WordItem(word(text, index + 1))
                        },
                    ),
                ),
        )

    private val fakeText =
        FakeQuranTextRepository(
            chapterList = chapters,
            pageByAyah = pageByAyah,
            pages = mutableMapOf(294 to textPage, 1 to textPage.copy(page = 1)),
        )

    @Test
    fun contentCarriesThePagesOfTheFirstAyahsInThePrintAsked() = runBlocking {
        val content = IndexLoader(fakeText).loadContent(ScriptId.V1)

        assertEquals(ScriptId.V1, content.script)
        val baqarah =
            content.surahRows.filterIsInstance<SurahRow>().first { it.chapter.number == 2 }
        assertEquals(6, baqarah.page)
        assertEquals(30, content.juzRows.size)
        assertEquals(604, content.pageCount)
    }

    @Test
    fun anInstalledIndoPakPrintKeepsItsOwnPageNumbersAndCount() = runBlocking {
        val indoPakText =
            object : QuranTextRepository by fakeText {
                override suspend fun pageOf(ayah: AyahRef, script: ScriptId): Int =
                    if (script == ScriptId.IndoPak16) ayah.surah * 4 - 3
                    else fakeText.pageOf(ayah, script)

                override fun pageCount(script: ScriptId): Int =
                    if (script == ScriptId.IndoPak16) INDOPAK_16_PAGES
                    else fakeText.pageCount(script)
            }

        val content = IndexLoader(indoPakText).loadContent(ScriptId.IndoPak16)

        assertEquals(ScriptId.IndoPak16, content.script)
        val baqarah =
            content.surahRows.filterIsInstance<SurahRow>().first { it.chapter.number == 2 }
        assertEquals(5, baqarah.page)
        assertEquals(INDOPAK_16_PAGES, content.pageCount)
    }

    @Test
    fun aScriptWithoutAPageTableFallsBackToTheMadaniPrint() = runBlocking {
        val withoutIndoPak =
            object : QuranTextRepository by fakeText {
                override suspend fun pageOf(ayah: AyahRef, script: ScriptId): Int {
                    if (script == ScriptId.IndoPak15) {
                        throw UnsupportedOperationException("no table")
                    }
                    return fakeText.pageOf(ayah, script)
                }
            }

        val content = IndexLoader(withoutIndoPak).loadContent(ScriptId.IndoPak15)

        assertEquals(FALLBACK_INDEX_SCRIPT, content.script)
    }

    @Test
    fun theContinueCardTakesTheFirstLineThatHasWords() = runBlocking {
        val loader = IndexLoader(fakeText)
        val content = loader.loadContent(ScriptId.V2)

        val reading = loader.continueReading(lastPage = 294, content = content)

        assertEquals(listOf("a", "b", "c"), reading.firstLineWords)
        assertEquals(294, reading.page)
        assertFalse(reading.isStart)
    }

    @Test
    fun withNothingReadTheCardStartsAtPageOne() = runBlocking {
        val loader = IndexLoader(fakeText)
        val content = loader.loadContent(ScriptId.V2)

        val reading = loader.continueReading(lastPage = null, content = content)

        assertEquals(1, reading.page)
        assertTrue(reading.isStart)
    }

    private companion object {
        const val INDOPAK_16_PAGES = 548
    }
}
