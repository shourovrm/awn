package app.awn.quran.index

import app.awn.quran.core.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurahRowsTest {

    private val chapters =
        (1..114).map { number ->
            Chapter(
                number = number,
                nameArabic = "",
                nameSimple = "Surah $number",
                nameBangla = null,
                versesCount = 7,
                revelationPlace = "makkah",
                bismillahPre = true,
            )
        }
    private val groups =
        groupSurahsByJuz(chapters, (1..114).associateWith { it }, (1..30).associateWith { it })
    private val rows = surahListRows(groups)

    @Test
    fun everyGroupStartsWithItsHeaderAndListsItsSurahsAfterIt() {
        assertTrue(rows.first() is SurahListRow.Header)
        assertEquals(114, rows.count { it is SurahListRow.Surah })
        assertEquals(groups.size, rows.count { it is SurahListRow.Header })
    }

    @Test
    fun theRowOfTheLastReadSurahIsTheSurahRowNotItsHeader() {
        val index = rowIndexOfSurah(rows, surah = 18)

        val row = rows[checkNotNull(index)]
        assertEquals(18, (row as SurahListRow.Surah).entry.chapter.number)
    }

    @Test
    fun noSurahReadYetMeansNoScroll() {
        assertNull(rowIndexOfSurah(rows, surah = null))
    }

    @Test
    fun aSurahThatIsNotListedMeansNoScroll() {
        assertNull(rowIndexOfSurah(rows, surah = 200))
    }
}
