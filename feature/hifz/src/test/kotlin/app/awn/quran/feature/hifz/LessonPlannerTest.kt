package app.awn.quran.feature.hifz

import app.awn.quran.feature.hifz.method.LessonPlanner
import app.awn.quran.feature.hifz.method.LessonSize
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PageTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonPlannerTest {

    private val table = smallTable()
    private val nothingKnown: (Int) -> PageStatus = { PageStatus.NotStarted }

    @Test
    fun aPageLessonIsTheAyahsThatStartOnThePage() {
        val part = LessonPlanner.nextPart(table, LessonSize.OnePage, null, nothingKnown)!!

        assertEquals(ayah(1), part.first)
        assertEquals(ayah(4), part.last)
        assertEquals(1, part.firstPage)
        assertEquals(1, part.lastPage)
        assertEquals(4, part.ayahCount)
    }

    @Test
    fun anAyahThatRunsOverThePageBreakBelongsToThePageWhereItStarts() {
        val part = LessonPlanner.nextPart(table, LessonSize.OnePage, ayah(9), nothingKnown)!!

        assertEquals(ayah(12), part.last)
        assertEquals(3, part.firstPage)
        assertEquals(4, part.lastPage)

        val next = LessonPlanner.nextPart(table, LessonSize.OnePage, ayah(13), nothingKnown)!!
        assertEquals(ayah(16), next.last)
        assertEquals(4, next.firstPage)
    }

    @Test
    fun aHalfPageLessonTakesHalfTheAyahsThenTheRest() {
        val first = LessonPlanner.nextPart(table, LessonSize.HalfPage, null, nothingKnown)!!
        assertEquals(ayah(1)..ayah(2), first.first..first.last)

        val second = LessonPlanner.nextPart(table, LessonSize.HalfPage, ayah(3), nothingKnown)!!
        assertEquals(ayah(3)..ayah(4), second.first..second.last)
    }

    @Test
    fun aHalfPageLessonOfAnOddPageRoundsTheFirstHalfUp() {
        val threeAyahPage = PageTable(listOf((1..3).map(::ayah)), listOf(1))

        val part = LessonPlanner.nextPart(threeAyahPage, LessonSize.HalfPage, null, nothingKnown)!!

        assertEquals(ayah(1)..ayah(2), part.first..part.last)
    }

    @Test
    fun anAyahLessonTakesThatManyAyahsAcrossAPageBreak() {
        val part = LessonPlanner.nextPart(table, LessonSize.Ayahs(3), ayah(3), nothingKnown)!!

        assertEquals(ayah(3)..ayah(5), part.first..part.last)
        assertEquals(1, part.firstPage)
        assertEquals(2, part.lastPage)
        assertEquals(3, part.ayahCount)
    }

    @Test
    fun pagesTheUserKnowsAreSkipped() {
        val knownPages = setOf(1, 2)

        val part =
            LessonPlanner.nextPart(table, LessonSize.OnePage, null) {
                if (it in knownPages) PageStatus.Solid else PageStatus.NotStarted
            }!!

        assertEquals(3, part.firstPage)
        assertEquals(ayah(9), part.first)
    }

    @Test
    fun nothingIsLeftAfterTheLastAyah() {
        val part = LessonPlanner.nextPart(table, LessonSize.OnePage, ayah(48), nothingKnown)!!
        assertEquals(ayah(48), part.last)

        val everythingKnown = LessonPlanner.nextPart(table, LessonSize.OnePage, null) {
            PageStatus.Memorised
        }
        assertNull(everythingKnown)
    }
}
