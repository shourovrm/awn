package app.awn.quran.index

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexLoaderTest {

    private fun word(ayah: AyahRef, position: Int, text: String) =
        Word(WordKey(ayah.surah, ayah.ayah, position), text, null, null, null)

    private val quarterAyah = AyahRef(2, 142)
    private val fakeText =
        FakeQuranTextRepository(
            chapterList =
                (1..114).map {
                    Chapter(it, "", "Surah $it", null, versesCount = 7, "makkah", true)
                },
            wordsByAyah =
                mutableMapOf(
                    quarterAyah to
                        listOf("a", "b", "c", "d", "e").mapIndexed { index, text ->
                            word(quarterAyah, index + 1, text)
                        },
                ),
            pageByAyah =
                (JuzStarts.ayahs + (1..114).map { AyahRef(it, 1) })
                    .associateWith { it.surah * 10 + it.ayah }
                    .toMutableMap(),
        )

    private val table = QuarterTable {
        listOf(
            QuarterStart(juz = 2, quarterOfJuz = 2, firstAyah = quarterAyah),
            QuarterStart(juz = 2, quarterOfJuz = 1, firstAyah = quarterAyah),
        )
    }

    @Test
    fun surahGroupsCarryThePagesOfTheirFirstAyahs() = runBlocking {
        val content = IndexLoader(fakeText, table).loadContent()

        val baqarah = content.surahGroups.first().surahs.first { it.chapter.number == 2 }
        assertEquals(21, baqarah.page)
        assertEquals(11, content.surahGroups.first().startPage)
    }

    @Test
    fun aQuarterRowShowsTheFirstFourWordsAndItsPage() = runBlocking {
        val content = IndexLoader(fakeText, table).loadContent()

        val section = content.juzSections.single()
        assertEquals(2, section.juz)
        assertEquals(listOf(1, 2), section.quarters.map { it.fraction + 1 })
        assertEquals("a b c d", section.quarters.first().firstWords)
        assertEquals(162, section.quarters.first().page)
    }
}
