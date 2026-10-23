package app.awn.quran.index

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private val surahPages = (1..114).associateWith { it * 5 - 4 }
    private val juzPages = (1..30).associateWith { it * 20 - 19 }

    private val surahRows = buildSurahRows(chapters, surahPages, juzPages)

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
    fun everyJuzHasOneHeadingAndEverySurahOneRowInQuranOrder() {
        assertEquals((1..30).toList(), surahRows.filterIsInstance<JuzHeading>().map { it.juz })
        assertEquals(
            (1..114).toList(),
            surahRows.filterIsInstance<SurahRow>().map { it.chapter.number },
        )
    }

    @Test
    fun aSurahSitsUnderTheJuzItBeginsIn() {
        fun juzOfSurah(surah: Int) =
            surahRows.filterIsInstance<SurahRow>().first { it.chapter.number == surah }.juz

        assertEquals(1, juzOfSurah(2))
        assertEquals(3, juzOfSurah(3))
        assertEquals(4, juzOfSurah(4))
        assertEquals(29, juzOfSurah(67))
        assertEquals(30, juzOfSurah(114))
    }

    @Test
    fun aJuzWithNoSurahBeginningInItNamesTheSurahThatContinues() {
        val heading = surahRows.filterIsInstance<JuzHeading>().first { it.juz == 2 }

        assertEquals(2, heading.continuedChapter?.number)
        assertNull(surahRows.filterIsInstance<JuzHeading>().first { it.juz == 3 }.continuedChapter)
    }

    @Test
    fun theFirstSurahOnTheHeadingsPageDoesNotRepeatThePage() {
        val pages = (1..114).associateWith { 10 }
        val rows = buildSurahRows(chapters, pages, (1..30).associateWith { 10 })

        val firstSurah = rows.filterIsInstance<SurahRow>().first()
        assertFalse(firstSurah.showPage)
        assertTrue(rows.filterIsInstance<SurahRow>().drop(1).first().showPage)
    }

    @Test
    fun aSurahThatStartsAwayFromTheHeadingShowsItsPage() {
        val row = surahRows.filterIsInstance<SurahRow>().first { it.chapter.number == 3 }

        assertTrue(row.showPage)
        assertEquals(11, row.page)
    }

    @Test
    fun theJuzListHasThirtyRowsWithTheirStartAyahAndPage() {
        val rows = buildJuzRows(chapters, juzPages).filterIsInstance<JuzStartRow>()

        assertEquals(30, rows.size)
        assertEquals(AyahRef(17, 1), rows[14].startAyah)
        assertEquals(17, rows[14].chapter.number)
        assertEquals(281, rows[14].page)
    }

    @Test
    fun theRowOfTheLastReadSurahIsTheSurahRowNotItsHeading() {
        val row = surahRows[checkNotNull(rowIndexOfSurah(surahRows, surah = 18))]

        assertEquals(18, (row as SurahRow).chapter.number)
        assertNull(rowIndexOfSurah(surahRows, surah = null))
        assertNull(rowIndexOfSurah(surahRows, surah = 200))
    }

    @Test
    fun aJuzNumberFindsItsHeading() {
        val heading = surahRows[rowIndexOfJuz(surahRows, juz = 15)]

        assertEquals(JuzHeading::class, heading::class)
        assertEquals(15, heading.juz)
    }

    @Test
    fun aPageOfTheBookFindsItsJuz() {
        assertEquals(1, juzOfPage(juzPages, 1))
        assertEquals(1, juzOfPage(juzPages, 20))
        assertEquals(2, juzOfPage(juzPages, 21))
        assertEquals(30, juzOfPage(juzPages, 604))
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
