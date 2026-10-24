package app.awn.quran.feature.hifz

import app.awn.quran.feature.hifz.method.AssignmentGenerator
import app.awn.quran.feature.hifz.method.DueRevision
import app.awn.quran.feature.hifz.method.FinishedPart
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.MethodSettings
import app.awn.quran.feature.hifz.method.PageRecord
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.hifz.method.RevisionSpacing
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parts saved before their end stay on Today, and old pages follow the chosen spacing. */
class PartialPartsTest {

    private val table = smallTable()
    private val zone = ZoneOffset.UTC

    private fun generate(
        plan: app.awn.quran.feature.hifz.method.HifzPlan,
        records: Map<Int, PageRecord>,
        finished: List<FinishedPart> = emptyList(),
    ) = AssignmentGenerator.generate(plan, table, records, finished, testDay, zone)

    @Test
    fun aLessonSavedPartWayStaysOnTodayFromItsFirstNotYetAyah() {
        val partial =
            FinishedPart(
                testDay,
                PartKind.NewLesson,
                listOf(1),
                ayah(1),
                ayah(4),
                PartGrade.Good,
                isComplete = false,
            )

        val lesson = generate(planOf(cursor = ayah(3)), emptyMap(), listOf(partial)).newLesson!!

        assertFalse(lesson.isDone)
        assertTrue(lesson.isContinuation)
        assertEquals(ayah(3), lesson.part.first)
        assertEquals(ayah(4), lesson.part.last)
    }

    @Test
    fun aLessonFinishedAfterAPartialSaveShowsAllOfTodaysAyahsDone() {
        val partial =
            FinishedPart(
                testDay,
                PartKind.NewLesson,
                listOf(1),
                ayah(1),
                ayah(4),
                PartGrade.Good,
                isComplete = false,
            )
        val rest =
            FinishedPart(testDay, PartKind.NewLesson, listOf(1), ayah(3), ayah(4), PartGrade.Good)

        val lesson =
            generate(planOf(cursor = ayah(5)), emptyMap(), listOf(partial, rest)).newLesson!!

        assertTrue(lesson.isDone)
        assertEquals(ayah(1), lesson.part.first)
        assertEquals(ayah(4), lesson.part.last)
    }

    @Test
    fun aRevisionSavedPartWayKeepsItsPagesAndLeavesTheRest() {
        val records = (1..3).associateWith { known(it, learnedDaysAgo = 30) }
        val partial =
            FinishedPart(
                testDay,
                PartKind.Old,
                listOf(1, 2, 3),
                null,
                null,
                PartGrade.Good,
                isComplete = false,
                gradedPages = listOf(1),
            )

        val old = generate(planOf(), records, listOf(partial)).old!!

        assertEquals(listOf(1, 2, 3), old.pages)
        assertEquals(listOf(2, 3), old.pagesLeft)
        assertFalse(old.isDone)
    }

    @Test
    fun fixedStepsBringAPageBackAfterItsStep() {
        val spacing = RevisionSpacing.FixedSteps(listOf(1, 3, 7))
        val due = DueRevision(spacing, testDay, zone)
        // Step 1 is three days; revised three days ago it is due, two days ago it is not.
        val dueNow =
            PageRecord(1, known(1).status, lastRevisedOn = testDay.minusDays(3), spacingStep = 1)
        val notYet =
            PageRecord(2, known(2).status, lastRevisedOn = testDay.minusDays(2), spacingStep = 1)
        // A step past the end uses the last step, seven days.
        val pastEnd =
            PageRecord(3, known(3).status, lastRevisedOn = testDay.minusDays(7), spacingStep = 9)

        val picked = due.pick(listOf(dueNow, notYet, pastEnd), MethodSettings(), room = 10)

        assertEquals(listOf(1, 3), picked)
        assertEquals(testDay.plusDays(1), due.nextDayOf(notYet))
    }

    @Test
    fun theMostOverduePagesComeFirstWhenTheLimitCutsTheDay() {
        val due = DueRevision(RevisionSpacing.FixedSteps(listOf(1)), testDay, zone)
        val records =
            (1..4).map { page ->
                PageRecord(
                    page,
                    known(page).status,
                    lastRevisedOn = testDay.minusDays(page.toLong()),
                )
            }

        assertEquals(listOf(3, 4), due.pick(records, MethodSettings(), room = 2))
    }

    @Test
    fun sabaqStartsWithTheManzilCycleAndOtherMethodsWithAdaptiveSpacing() {
        assertEquals(
            RevisionSpacing.ManzilCycle,
            app.awn.quran.feature.hifz.method.MethodDefaults.settingsFor(
                    HifzMethodId.SabaqSabqiManzil,
                )
                .revisionSpacing,
        )
        assertEquals(
            RevisionSpacing.Adaptive,
            app.awn.quran.feature.hifz.method.MethodDefaults.settingsFor(HifzMethodId.FixedAyahs)
                .revisionSpacing,
        )
    }
}
