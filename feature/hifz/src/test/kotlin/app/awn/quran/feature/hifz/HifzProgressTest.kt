package app.awn.quran.feature.hifz

import app.awn.quran.feature.hifz.method.HifzProgress
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.MistakeType
import app.awn.quran.feature.hifz.method.PageRecord
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PageTable
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.WeakSpot
import app.awn.quran.feature.hifz.method.WeakSpots
import org.junit.Assert.assertEquals
import org.junit.Test

class HifzProgressTest {

    private val table = smallTable()

    @Test
    fun theMapHasOneRowPerJuzWithOneStatusPerPageAndCountsKnownPages() {
        val records =
            mapOf(
                1 to PageRecord(1, PageStatus.Solid),
                6 to PageRecord(6, PageStatus.Memorised),
                7 to PageRecord(7, PageStatus.Learning),
            )

        val map = HifzProgress.map(table, records)

        assertEquals(listOf(1, 2), map.rows.map { it.juz })
        assertEquals(listOf(1, 7), map.rows.map { it.firstPage })
        assertEquals(6, map.rows[0].statuses.size)
        assertEquals(PageStatus.Solid, map.rows[0].statuses.first())
        assertEquals(PageStatus.Memorised, map.rows[0].statuses.last())
        assertEquals(PageStatus.Learning, map.rows[1].statuses.first())
        assertEquals(2, map.memorisedPages)
        assertEquals(12, map.pageCount)
    }

    @Test
    fun aJuzIsTheJuzOfThePagesFirstAyah() {
        val real =
            PageTable.fromPages(
                listOf(listOf(ayah(1)), listOf(app.awn.quran.core.data.AyahRef(2, 142))),
            )

        assertEquals(1, real.juzOf(1))
        assertEquals(2, real.juzOf(2))
    }

    @Test
    fun anotherPrintTakesTheRecordOfTheOldPageThatHoldsItsFirstAyah() {
        val oldPrint =
            PageTable(
                listOf((1..4).map(::ayah), (5..8).map(::ayah), (9..12).map(::ayah)),
                listOf(1, 1, 1),
            )
        // The new print breaks pages one ayah later: 1-5, 6-10, 11-12.
        val newPrint =
            PageTable(
                listOf((1..5).map(::ayah), (6..10).map(::ayah), (11..12).map(::ayah)),
                listOf(1, 1, 1),
            )
        val records =
            mapOf(
                1 to PageRecord(1, PageStatus.Solid),
                2 to PageRecord(2, PageStatus.Memorised),
            )

        val moved = HifzProgress.remap(oldPrint, newPrint, records)

        assertEquals(PageStatus.Solid, moved.getValue(1).status)
        // New page 2 starts at ayah 6, which is on old page 2.
        assertEquals(PageStatus.Memorised, moved.getValue(2).status)
        assertEquals(2, moved.getValue(2).page)
        assertEquals(false, moved.containsKey(3))
    }

    @Test
    fun aCleanReciteSuggestsGoodAndMistakesLowerIt() {
        assertEquals(PartGrade.Good, HifzProgress.suggestedGrade(0, 5))
        assertEquals(PartGrade.Hard, HifzProgress.suggestedGrade(3, 5))
        assertEquals(PartGrade.Forgot, HifzProgress.suggestedGrade(6, 5))
    }

    @Test
    fun aWeakSpotNeedsTheSameMistakeTwiceAndTheWorstComeFirst() {
        val mistakes =
            listOf(
                MistakeRecord(ayah(155), MistakeType.Skipped, 1),
                MistakeRecord(ayah(155), MistakeType.Skipped, 2),
                MistakeRecord(ayah(155), MistakeType.Skipped, 3),
                MistakeRecord(ayah(149), MistakeType.OtherWord, 4),
                MistakeRecord(ayah(149), MistakeType.OtherWord, 5),
                MistakeRecord(ayah(150), MistakeType.Skipped, 6),
                MistakeRecord(ayah(150), MistakeType.OtherWord, 7),
            )

        val spots = WeakSpots.of(mistakes)

        assertEquals(
            listOf(
                WeakSpot(ayah(155), MistakeType.Skipped, 3),
                WeakSpot(ayah(149), MistakeType.OtherWord, 2),
            ),
            spots,
        )
    }
}
