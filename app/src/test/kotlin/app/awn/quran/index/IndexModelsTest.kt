package app.awn.quran.index

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexModelsTest {

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

    private val surahPages = (1..114).associateWith { it * 5 }
    private val juzPages = (1..30).associateWith { it * 20 }

    private val groups = groupSurahsByJuz(chapters, surahPages, juzPages)

    @Test
    fun thirtyJuzStartsRunInQuranOrder() {
        assertEquals(30, JuzStarts.ayahs.size)
        assertEquals(JuzStarts.ayahs.sorted(), JuzStarts.ayahs)
    }

    @Test
    fun anAyahBelongsToTheLatestJuzThatStartedAtOrBeforeIt() {
        assertEquals(1, JuzStarts.juzOf(AyahRef(1, 1)))
        assertEquals(1, JuzStarts.juzOf(AyahRef(2, 141)))
        assertEquals(2, JuzStarts.juzOf(AyahRef(2, 142)))
        assertEquals(30, JuzStarts.juzOf(AyahRef(114, 6)))
    }

    @Test
    fun everySurahAppearsExactlyOnceInQuranOrder() {
        val numbers = groups.flatMap { group -> group.surahs.map { it.chapter.number } }

        assertEquals((1..114).toList(), numbers)
    }

    @Test
    fun aSurahSitsUnderTheJuzItBeginsIn() {
        fun juzOfSurah(surah: Int) =
            groups.first { group -> group.surahs.any { it.chapter.number == surah } }.juz

        assertEquals(1, juzOfSurah(1))
        assertEquals(1, juzOfSurah(2))
        assertEquals(3, juzOfSurah(3))
        assertEquals(4, juzOfSurah(4))
        assertEquals(29, juzOfSurah(67))
        assertEquals(30, juzOfSurah(78))
        assertEquals(30, juzOfSurah(114))
    }

    @Test
    fun aJuzWithNoSurahBeginningInItHasNoGroup() {
        assertFalse(groups.any { it.juz == 2 })
    }

    @Test
    fun groupsAreInJuzOrderAndCarryThePagesGiven() {
        assertEquals(groups.map { it.juz }.sorted(), groups.map { it.juz })
        val firstGroup = groups.first()
        assertEquals(20, firstGroup.startPage)
        assertEquals(5, firstGroup.surahs.first().page)
    }

    @Test
    fun quarterOneOfEachHizbHasNoFraction() {
        assertEquals(0, fractionOfQuarter(1))
        assertEquals(1, fractionOfQuarter(2))
        assertEquals(3, fractionOfQuarter(4))
        assertEquals(0, fractionOfQuarter(5))
    }

    @Test
    fun theHizbCountsTwoPerJuz() {
        assertEquals(1, hizbOfQuarter(juz = 1, quarterOfJuz = 4))
        assertEquals(2, hizbOfQuarter(juz = 1, quarterOfJuz = 5))
        assertEquals(3, hizbOfQuarter(juz = 2, quarterOfJuz = 1))
        assertEquals(60, hizbOfQuarter(juz = 30, quarterOfJuz = 8))
    }

    @Test
    fun theStandInTableKnowsTheFirstQuarterOfEveryJuz() = runBlocking {
        val quarters = JuzStartQuarterTable.quarters()

        assertEquals(30, quarters.size)
        assertTrue(quarters.all { it.quarterOfJuz == 1 })
        assertEquals(AyahRef(2, 142), quarters[1].firstAyah)
    }

    private val entries =
        listOf(
            BookmarkEntry.PageMark(page = 50, createdAt = 100, surah = 3),
            BookmarkEntry.AyahMark(AyahRef(2, 255), page = 42, createdAt = 300),
            BookmarkEntry.AyahMark(AyahRef(2, 250), page = 42, createdAt = 200),
            BookmarkEntry.PageMark(page = 42, createdAt = 50, surah = 2),
        )

    @Test
    fun sortedByDateTheNewestComesFirst() {
        val sorted = sortBookmarks(entries, BookmarkSort.Date)

        assertEquals(listOf(300L, 200L, 100L, 50L), sorted.map { it.createdAt })
    }

    @Test
    fun sortedByPositionPagesRunInOrderAndAPageMarkPrecedesItsAyahs() {
        val sorted = sortBookmarks(entries, BookmarkSort.Position)

        assertEquals(listOf(50L, 200L, 300L, 100L), sorted.map { it.createdAt })
    }
}
