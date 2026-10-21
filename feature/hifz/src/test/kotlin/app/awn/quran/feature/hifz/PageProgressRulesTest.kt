package app.awn.quran.feature.hifz

import app.awn.quran.feature.hifz.method.LessonPlanner
import app.awn.quran.feature.hifz.method.LessonSize
import app.awn.quran.feature.hifz.method.PageProgressRules
import app.awn.quran.feature.hifz.method.PageRecord
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PartGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PageProgressRulesTest {

    private val table = smallTable()
    private val nowMillis = 1_800_000_000_000L

    private fun lesson(size: LessonSize, from: Int) =
        LessonPlanner.nextPart(table, size, ayah(from)) { PageStatus.NotStarted }!!

    @Test
    fun aGoodPageLessonMemorisesThePageAndMovesTheCursor() {
        val outcome =
            PageProgressRules.completeLesson(
                table,
                lesson(LessonSize.OnePage, 1),
                PartGrade.Good,
                testDay,
                nowMillis,
                emptyMap(),
            )

        val page = outcome.records.single()
        assertEquals(PageStatus.Memorised, page.status)
        assertEquals(testDay, page.learnedOn)
        assertNotNull(page.card)
        assertEquals(ayah(5), outcome.nextCursor)
    }

    @Test
    fun aHalfPageLessonLeavesThePageLearning() {
        val outcome =
            PageProgressRules.completeLesson(
                table,
                lesson(LessonSize.HalfPage, 1),
                PartGrade.Good,
                testDay,
                nowMillis,
                emptyMap(),
            )

        assertEquals(PageStatus.Learning, outcome.records.single().status)
        assertNull(outcome.records.single().card)
        assertEquals(ayah(3), outcome.nextCursor)
    }

    @Test
    fun theSecondHalfFinishesThePage() {
        val firstHalf = PageRecord(1, PageStatus.Learning, learnedOn = testDay.minusDays(1))

        val outcome =
            PageProgressRules.completeLesson(
                table,
                lesson(LessonSize.HalfPage, 3),
                PartGrade.Good,
                testDay,
                nowMillis,
                mapOf(1 to firstHalf),
            )

        assertEquals(PageStatus.Memorised, outcome.records.single().status)
    }

    @Test
    fun anAyahThatRunsOverTheBreakLeavesTheNextPageLearning() {
        val outcome =
            PageProgressRules.completeLesson(
                table,
                lesson(LessonSize.OnePage, 9),
                PartGrade.Good,
                testDay,
                nowMillis,
                emptyMap(),
            )

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

        val outcome =
            PageProgressRules.completeLesson(
                table,
                part,
                PartGrade.Forgot,
                testDay,
                nowMillis,
                emptyMap(),
            )

        assertEquals(PageStatus.Learning, outcome.records.single().status)
        assertEquals(part.first, outcome.nextCursor)
    }

    @Test
    fun forgettingARevisedPageSendsItBackToLearningAsARecentLesson() {
        val record = PageProgressRules.markPage(5, PageStatus.Memorised, testDay, nowMillis)

        val after =
            PageProgressRules.gradeRevision(record, PartGrade.Forgot, testDay, nowMillis)

        assertEquals(PageStatus.Learning, after.status)
        assertEquals(testDay, after.learnedOn)
        assertEquals(testDay, after.lastRevisedOn)
    }

    @Test
    fun reviewingALearningPageWithACardGoodMemorisesItAgain() {
        val record =
            PageProgressRules.markPage(5, PageStatus.Memorised, testDay, nowMillis)
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
        var record = PageProgressRules.markPage(5, PageStatus.Memorised, testDay, nowMillis)
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
        val record = PageProgressRules.markPage(7, PageStatus.Solid, testDay, nowMillis)

        assertEquals(PageProgressRules.SOLID_STABILITY_DAYS, record.card?.stability)
        assertNull(record.learnedOn)
    }

    @Test
    fun markingAPageNotStartedClearsEverything() {
        val record = PageProgressRules.markPage(7, PageStatus.NotStarted, testDay, nowMillis)

        assertEquals(PageRecord(7, PageStatus.NotStarted), record)
    }
}
