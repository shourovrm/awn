package app.awn.quran.feature.hifz

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.LessonPart
import app.awn.quran.feature.hifz.method.LessonPlanner
import app.awn.quran.feature.hifz.method.LessonSize
import app.awn.quran.feature.hifz.method.PageMarking
import app.awn.quran.feature.hifz.method.PageProgressRules
import app.awn.quran.feature.hifz.method.PageRecord
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PartGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageProgressRulesTest {

    private val table = smallTable()
    private val nowMillis = 1_800_000_000_000L

    private fun lesson(size: LessonSize, from: Int) =
        LessonPlanner.nextPart(table, size, ayah(from)) { PageStatus.NotStarted }!!

    private fun ayahsOf(part: LessonPart) = table.allAyahs.filter { it in part.first..part.last }

    private fun complete(
        part: LessonPart,
        firmness: Map<AyahRef, AyahFirmness>,
        records: Map<Int, PageRecord> = emptyMap(),
    ) = PageProgressRules.completeLesson(table, part, firmness, testDay, nowMillis, records)

    private fun completeAll(
        part: LessonPart,
        firmness: AyahFirmness,
        records: Map<Int, PageRecord> = emptyMap(),
    ) = complete(part, ayahsOf(part).associateWith { firmness }, records)

    @Test
    fun aLessonSavedAfterFourOfSevenAyahsMovesTheCursorToTheFifth() {
        // Ayahs 1 to 7 cross from page 1 (ayahs 1 to 4) onto page 2 (ayahs 5 to 8).
        val part = LessonPlanner.partBetween(table, ayah(1), ayah(7))
        val answers =
            ayahsOf(part).associateWith {
                if (it <= ayah(4)) AyahFirmness.Firm else AyahFirmness.NotYet
            }

        val outcome = complete(part, answers)

        assertEquals(ayah(5), outcome.nextCursor)
        assertFalse(outcome.isComplete)
        assertEquals(
            listOf(PageStatus.Memorised, PageStatus.Learning),
            outcome.records.map { it.status },
        )
    }

    @Test
    fun aNotYetAyahStopsTheRunEvenWhenLaterAyahsAreFirm() {
        val part = lesson(LessonSize.OnePage, 1)
        val answers =
            ayahsOf(part).associateWith { AyahFirmness.Firm } + (ayah(2) to AyahFirmness.NotYet)

        val outcome = complete(part, answers)

        assertEquals(ayah(2), outcome.nextCursor)
        assertEquals(PageStatus.Learning, outcome.records.single().status)
    }

    @Test
    fun aPageTakesTheGradeOfItsWeakestAyah() {
        val part = lesson(LessonSize.OnePage, 1)
        val shakyOne =
            ayahsOf(part).associateWith { AyahFirmness.VeryFirm } + (ayah(3) to AyahFirmness.Shaky)
        val allFirm = ayahsOf(part).associateWith { AyahFirmness.Firm }

        val shakyCard = complete(part, shakyOne).records.single().card!!
        val firmCard = complete(part, allFirm).records.single().card!!

        // A Hard first review gives a shorter memory than a Good one.
        assertTrue(shakyCard.stability!! < firmCard.stability!!)
    }

    @Test
    fun aLessonWithEveryAyahMemorisedIsComplete() {
        val outcome = completeAll(lesson(LessonSize.OnePage, 1), AyahFirmness.Firm)

        assertTrue(outcome.isComplete)
    }

    @Test
    fun goodMovesTheFixedStepOnHardKeepsItAndForgotStartsAgain() {
        assertEquals(3, PageProgressRules.nextSpacingStep(2, PartGrade.Good))
        assertEquals(4, PageProgressRules.nextSpacingStep(2, PartGrade.Easy))
        assertEquals(2, PageProgressRules.nextSpacingStep(2, PartGrade.Hard))
        assertEquals(0, PageProgressRules.nextSpacingStep(2, PartGrade.Forgot))
    }

    @Test
    fun aGoodPageLessonMemorisesThePageAndMovesTheCursor() {
        val outcome = completeAll(lesson(LessonSize.OnePage, 1), AyahFirmness.Firm, emptyMap())

        val page = outcome.records.single()
        assertEquals(PageStatus.Memorised, page.status)
        assertEquals(testDay, page.learnedOn)
        assertNotNull(page.card)
        assertEquals(ayah(5), outcome.nextCursor)
    }

    @Test
    fun aHalfPageLessonLeavesThePageLearning() {
        val outcome = completeAll(lesson(LessonSize.HalfPage, 1), AyahFirmness.Firm, emptyMap())

        assertEquals(PageStatus.Learning, outcome.records.single().status)
        assertNull(outcome.records.single().card)
        assertEquals(ayah(3), outcome.nextCursor)
    }

    @Test
    fun theSecondHalfFinishesThePage() {
        val firstHalf = PageRecord(1, PageStatus.Learning, learnedOn = testDay.minusDays(1))

        val outcome =
            completeAll(lesson(LessonSize.HalfPage, 3), AyahFirmness.Firm, mapOf(1 to firstHalf))

        assertEquals(PageStatus.Memorised, outcome.records.single().status)
    }

    @Test
    fun anAyahThatRunsOverTheBreakLeavesTheNextPageLearning() {
        val outcome = completeAll(lesson(LessonSize.OnePage, 9), AyahFirmness.Firm, emptyMap())

        assertEquals(
            listOf(PageStatus.Memorised, PageStatus.Learning),
            outcome.records.map { it.status },
        )
        assertEquals(listOf(3, 4), outcome.records.map { it.page })
        assertEquals(ayah(13), outcome.nextCursor)
    }

    @Test
    fun aForgottenLessonKeepsTheCursorSoTheLessonComesBack() {
        val part = lesson(LessonSize.OnePage, 1)

        val outcome = completeAll(part, AyahFirmness.NotYet, emptyMap())

        assertEquals(PageStatus.Learning, outcome.records.single().status)
        assertEquals(part.first, outcome.nextCursor)
    }

    @Test
    fun forgettingARevisedPageSendsItBackToLearningAsARecentLesson() {
        val record = PageMarking.markPage(5, PageStatus.Memorised, testDay, nowMillis)

        val after = PageProgressRules.gradeRevision(record, PartGrade.Forgot, testDay, nowMillis)

        assertEquals(PageStatus.Learning, after.status)
        assertEquals(testDay, after.learnedOn)
        assertEquals(testDay, after.lastRevisedOn)
    }

    @Test
    fun reviewingALearningPageWithACardGoodMemorisesItAgain() {
        val record =
            PageMarking.markPage(5, PageStatus.Memorised, testDay, nowMillis)
                .copy(status = PageStatus.Learning)

        val after = PageProgressRules.gradeRevision(record, PartGrade.Good, testDay, nowMillis)

        assertEquals(PageStatus.Memorised, after.status)
    }

    @Test
    fun revisingHalfALessonOnlyChangesTheRevisionDate() {
        val half = PageRecord(1, PageStatus.Learning, learnedOn = testDay.minusDays(1))

        val after = PageProgressRules.gradeRevision(half, PartGrade.Easy, testDay, nowMillis)

        assertEquals(PageStatus.Learning, after.status)
        assertNull(after.card)
        assertEquals(testDay, after.lastRevisedOn)
    }

    @Test
    fun aPageBecomesSolidWhenItsMemoryReachesThreeWeeks() {
        var record = PageMarking.markPage(5, PageStatus.Memorised, testDay, nowMillis)
        var moment = nowMillis
        var reviews = 0
        while (record.status != PageStatus.Solid && reviews < 20) {
            // Review exactly when the page is due, the way a steady week-by-week routine does.
            moment = checkNotNull(record.card).dueMillis
            record = PageProgressRules.gradeRevision(record, PartGrade.Good, testDay, moment)
            reviews++
        }

        assertEquals(PageStatus.Solid, record.status)
    }

    @Test
    fun markingAPageSolidStartsItAtThreeWeeksOfMemory() {
        val record = PageMarking.markPage(7, PageStatus.Solid, testDay, nowMillis)

        assertEquals(PageProgressRules.SOLID_STABILITY_DAYS, record.card?.stability)
        assertNull(record.learnedOn)
    }

    @Test
    fun markingAPageNotStartedClearsEverything() {
        val record = PageMarking.markPage(7, PageStatus.NotStarted, testDay, nowMillis)

        assertEquals(PageRecord(7, PageStatus.NotStarted), record)
    }
}
