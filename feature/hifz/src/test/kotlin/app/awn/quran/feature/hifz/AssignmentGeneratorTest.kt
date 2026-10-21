package app.awn.quran.feature.hifz

import app.awn.quran.feature.hifz.method.AssignmentGenerator
import app.awn.quran.feature.hifz.method.FinishedPart
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.LessonSize
import app.awn.quran.feature.hifz.method.MethodSettings
import app.awn.quran.feature.hifz.method.PageProgressRules
import app.awn.quran.feature.hifz.method.PageRecord
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentGeneratorTest {

    private val table = smallTable()
    private val zone = ZoneOffset.UTC

    private fun recordsOf(vararg records: PageRecord) = records.associateBy { it.page }

    private fun generate(
        plan: app.awn.quran.feature.hifz.method.HifzPlan,
        records: Map<Int, PageRecord>,
        finished: List<FinishedPart> = emptyList(),
        daysLater: Long = 0,
    ) =
        AssignmentGenerator.generate(
            plan,
            table,
            records,
            finished,
            testDay.plusDays(daysLater),
            zone,
        )

    @Test
    fun recentPagesAreTheOnesTaughtInTheLastWeekButNotToday() {
        val records =
            recordsOf(
                known(1, learnedDaysAgo = 10),
                known(2, learnedDaysAgo = 3),
                known(3, learnedDaysAgo = 0),
                PageRecord(4, PageStatus.Learning, learnedOn = testDay.minusDays(1)),
            )

        val assignment = generate(planOf(), records)

        assertEquals(listOf(2, 4), assignment.recent?.pages)
        assertEquals(listOf(1), assignment.old?.pages)
    }

    @Test
    fun theNewLessonSkipsKnownPagesAndStartsAtTheFirstOpenOne() {
        val records = recordsOf(known(1), known(2))

        val lesson = generate(planOf(), records).newLesson

        assertEquals(3, lesson?.part?.firstPage)
        assertEquals(false, lesson?.isDone)
    }

    @Test
    fun aHalfPageLessonIsHalfThePagesAyahs() {
        val plan = planOf(settings = MethodSettings(lessonSize = LessonSize.HalfPage))

        val lesson = generate(plan, emptyMap()).newLesson

        assertEquals(ayah(1)..ayah(2), lesson?.part?.first!!..lesson.part.last)
    }

    @Test
    fun theOldPortionRotatesOverTheCycleAndTheOldestRevisedComeFirst() {
        val records =
            (1..100).associateWith { known(it, revisedDaysAgo = (100 - it).toLong() + 1) } +
                (101..105).associateWith { PageRecord(it, PageStatus.Solid) }
        val settings = MethodSettings(manzilCycleDays = 7, revisionPageLimit = 50)

        val old = generate(planOf(settings = settings), records).old!!

        // 105 old pages over 7 days is 15 a day. Pages never revised (101 to 105) come first, then
        // the oldest revised, which is page 1.
        assertEquals(15, old.pages.size)
        assertTrue(old.pages.containsAll(listOf(101, 102, 103, 104, 105, 1, 2)))
        assertTrue(old.pages.none { it in 20..100 })
    }

    @Test
    fun theCycleLengthSetsHowManyOldPagesComeEachDay() {
        val records = (1..100).associateWith { known(it) }

        fun oldCount(cycleDays: Int) =
            generate(
                    planOf(
                        settings =
                            MethodSettings(manzilCycleDays = cycleDays, revisionPageLimit = 50),
                    ),
                    records,
                )
                .old!!
                .pages
                .size

        assertEquals(15, oldCount(7))
        assertEquals(7, oldCount(15))
        assertEquals(4, oldCount(30))
    }

    @Test
    fun theDailyLimitCapsRevisionAndRecentPagesComeFirst() {
        val records =
            recordsOf(
                known(1, learnedDaysAgo = 1),
                known(2, learnedDaysAgo = 2),
                known(3, learnedDaysAgo = 3),
                known(4, learnedDaysAgo = 30),
                known(5, learnedDaysAgo = 30),
            )

        val assignment = generate(planOf(settings = MethodSettings(revisionPageLimit = 3)), records)

        assertEquals(listOf(1, 2, 3), assignment.recent?.pages)
        assertNull(assignment.old)
        assertEquals(3, assignment.revisionPageCount)
    }

    @Test
    fun aMissedStretchOfDaysDoesNotMakeALargerDay() {
        val records = (1..12).associateWith { known(it, revisedDaysAgo = 40) }
        val settings = MethodSettings(manzilCycleDays = 7, revisionPageLimit = 10)

        val normalDay = generate(planOf(settings = settings), records)
        val afterAWeekAway = generate(planOf(settings = settings), records, daysLater = 9)

        assertEquals(normalDay.revisionPageCount, afterAWeekAway.revisionPageCount)
        assertTrue(afterAWeekAway.revisionPageCount <= 10)
    }

    @Test
    fun pagesRevisedYesterdayWaitWhileOlderOnesComeFirst() {
        val records =
            recordsOf(
                known(1, revisedDaysAgo = 1),
                known(2, revisedDaysAgo = 6),
                known(3, revisedDaysAgo = 5),
                known(4, revisedDaysAgo = 7),
            )
        val settings = MethodSettings(manzilCycleDays = 2, revisionPageLimit = 10)

        val old = generate(planOf(settings = settings), records).old!!

        assertEquals(listOf(2, 4), old.pages)
    }

    @Test
    fun aFinishedPartStaysInTheDayAsDoneWithItsOwnPages() {
        val records = recordsOf(known(1, learnedDaysAgo = 2), known(2, learnedDaysAgo = 3))
        val finished =
            listOf(
                FinishedPart(testDay, PartKind.Recent, listOf(1, 2), null, null, PartGrade.Good),
            )

        val assignment = generate(planOf(), records, finished)

        assertEquals(true, assignment.recent?.isDone)
        assertEquals(listOf(1, 2), assignment.recent?.pages)
        assertEquals(PartKind.NewLesson, assignment.nextPart())
    }

    @Test
    fun theNextPartWalksFromTheLessonToRecentToOldThenEnds() {
        val records = recordsOf(known(1, learnedDaysAgo = 2), known(2, learnedDaysAgo = 30))
        val first = generate(planOf(), records)
        assertEquals(PartKind.NewLesson, first.nextPart())

        val lessonDone =
            FinishedPart(testDay, PartKind.NewLesson, listOf(3), ayah(9), ayah(12), PartGrade.Good)
        val recentDone =
            FinishedPart(testDay, PartKind.Recent, listOf(1), null, null, PartGrade.Good)
        val oldDone = FinishedPart(testDay, PartKind.Old, listOf(2), null, null, PartGrade.Good)

        assertEquals(PartKind.Recent, generate(planOf(), records, listOf(lessonDone)).nextPart())
        assertEquals(
            PartKind.Old,
            generate(planOf(), records, listOf(lessonDone, recentDone)).nextPart(),
        )
        assertNull(
            generate(planOf(), records, listOf(lessonDone, recentDone, oldDone)).nextPart(),
        )
    }

    @Test
    fun aFinishedLessonShowsTheAyahsThatWereDone() {
        val lessonDone =
            FinishedPart(
                testDay,
                PartKind.NewLesson,
                listOf(3, 4),
                ayah(9),
                ayah(12),
                PartGrade.Good,
            )

        val lesson = generate(planOf(), emptyMap(), listOf(lessonDone)).newLesson

        assertEquals(true, lesson?.isDone)
        assertEquals(3, lesson?.part?.firstPage)
        assertEquals(4, lesson?.part?.lastPage)
    }

    @Test
    fun methodsWithoutARecentSplitRevisePagesWhoseMemoryIsDue() {
        val nowMillis = testDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val dueCard =
            checkNotNull(
                PageProgressRules.markPage(1, PageStatus.Memorised, testDay, nowMillis).card,
            )
        val dueNow = known(1).copy(card = dueCard.copy(dueMillis = nowMillis - 1_000))
        val notDue = known(2).copy(card = dueCard.copy(dueMillis = nowMillis + 5 * 86_400_000L))
        val plan =
            planOf(
                method = HifzMethodId.FixedAyahs,
                settings = MethodSettings(ayahsPerDay = 3),
            )

        val assignment = generate(plan, recordsOf(dueNow, notDue))

        assertNull(assignment.recent)
        assertEquals(listOf(1), assignment.old?.pages)
        assertEquals(3, assignment.newLesson?.part?.ayahCount)
    }

    @Test
    fun trackOnlyAsksForNothing() {
        val plan = planOf(method = HifzMethodId.TrackOnly)

        val assignment = generate(plan, recordsOf(known(1, learnedDaysAgo = 2)))

        assertNull(assignment.newLesson)
        assertNull(assignment.recent)
        assertNull(assignment.old)
        assertNull(assignment.nextPart())
        assertNotNull(assignment)
    }
}
