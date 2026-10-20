package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The verse count of each of the 114 surahs, in order. */
private val VERSE_COUNTS =
    listOf(
        7,
        286,
        200,
        176,
        120,
        165,
        206,
        75,
        129,
        109,
        123,
        111,
        43,
        52,
        99,
        128,
        111,
        110,
        98,
        135,
        112,
        78,
        118,
        64,
        77,
        227,
        93,
        88,
        69,
        60,
        34,
        30,
        73,
        54,
        45,
        83,
        182,
        88,
        75,
        85,
        54,
        53,
        89,
        59,
        37,
        35,
        38,
        29,
        18,
        45,
        60,
        49,
        62,
        55,
        78,
        96,
        29,
        22,
        24,
        13,
        14,
        11,
        11,
        18,
        12,
        12,
        30,
        52,
        52,
        44,
        28,
        28,
        20,
        56,
        40,
        31,
        50,
        40,
        46,
        42,
        29,
        19,
        36,
        25,
        22,
        17,
        19,
        26,
        30,
        20,
        15,
        21,
        11,
        8,
        8,
        19,
        5,
        8,
        8,
        11,
        11,
        8,
        3,
        9,
        5,
        4,
        7,
        3,
        6,
        3,
        5,
        4,
        5,
        6,
    )

fun fullQuranText(pageByAyah: Map<AyahRef, Int> = emptyMap()) =
    FakeQuranTextRepository(
        chapterList = VERSE_COUNTS.mapIndexed { index, verses -> chapter(index + 1, verses) },
        pageByAyah = pageByAyah.toMutableMap(),
    )

class DownloadPlanTest {

    /** A page that holds the end of surah 1 and the start of surah 2, then a page that follows. */
    private val pageWithTwoSurahs =
        listOf(AyahRef(1, 6), AyahRef(1, 7), AyahRef(2, 1), AyahRef(2, 2))

    private val plan =
        DownloadPlan(
            fullQuranText(
                pageByAyah =
                    pageWithTwoSurahs.associateWith { 7 } +
                        mapOf(AyahRef(2, 3) to 8, AyahRef(2, 255) to 42),
            ),
        )

    @Test
    fun theVerseCountsAddUpToTheWholeQuran() = runTest {
        assertEquals(6_236, plan.allAyahs().size)
    }

    @Test
    fun pageAmountIsTheStartingPageAndGroupsItsSurahs() = runTest {
        val range = pageWithTwoSurahs + AyahRef(2, 3)

        val ayahs = plan.ayahsFor(DownloadAmount.Page, range)

        assertEquals(pageWithTwoSurahs, ayahs)
        assertEquals(listOf(1, 2), ayahs.map { it.surah }.distinct())
    }

    @Test
    fun pageAmountStartsAtTheStartAyahNotAtTheTopOfThePage() = runTest {
        val range = listOf(AyahRef(1, 7), AyahRef(2, 1), AyahRef(2, 2), AyahRef(2, 3))

        val ayahs = plan.ayahsFor(DownloadAmount.Page, range)

        assertEquals(listOf(AyahRef(1, 7), AyahRef(2, 1), AyahRef(2, 2)), ayahs)
    }

    @Test
    fun surahAmountIsTheWholeStartingSurah() = runTest {
        val ayahs = plan.ayahsFor(DownloadAmount.Surah, pageWithTwoSurahs)

        assertEquals(7, ayahs.size)
        assertEquals(AyahRef(1, 1), ayahs.first())
        assertEquals(AyahRef(1, 7), ayahs.last())
    }

    @Test
    fun juzAmountIsTheWholeJuzOfTheStartAyah() = runTest {
        val ayahs = plan.ayahsFor(DownloadAmount.Juz, listOf(AyahRef(2, 255)))

        assertEquals(AyahRef(2, 253), ayahs.first())
        assertEquals(AyahRef(3, 92), ayahs.last())
        assertEquals(34 + 92, ayahs.size)
    }

    @Test
    fun anEmptyRangeNeedsNothing() = runTest {
        assertEquals(emptyList<AyahRef>(), plan.ayahsFor(DownloadAmount.Surah, emptyList()))
    }

    @Test
    fun juzTableSplitsTheQuranIntoThirtyConsecutiveParts() = runTest {
        val sizes = (1..JuzTable.COUNT).map { plan.juzAyahs(it).size }

        assertEquals(6_236, sizes.sum())
        assertEquals(30, sizes.size)
        assertEquals(30, JuzTable.juzOf(AyahRef(114, 6)))
        assertEquals(1, JuzTable.juzOf(AyahRef(2, 141)))
        assertEquals(2, JuzTable.juzOf(AyahRef(2, 142)))
    }
}
