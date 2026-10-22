package app.awn.quran.feature.learn

import app.awn.quran.feature.learn.session.ItemRef
import app.awn.quran.feature.learn.session.PlanStep
import app.awn.quran.feature.learn.session.TodayPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the Learn row on the Quran tab's Today list says about the course. The row is one of at
 * most two Today rows, so the course and the ayah revision share the one Learn row: it shows the
 * course's time and new words when the course has started, and nothing for the course otherwise.
 */
class CourseTodayLineTest {

    private fun plan(newWords: Int, reviews: Int, seconds: Int): TodayPlan =
        TodayPlan(
            steps = List(newWords) { PlanStep.NewWord(rank = it + 1) } +
                List(reviews) { PlanStep.Review(ItemRef.Word(rank = 100 + it)) },
            plannedSeconds = seconds,
        )

    @Test
    fun aStartedCourseGivesItsMinutesNewWordsAndReviews() {
        val line = courseTodayLine(plan(newWords = 10, reviews = 3, seconds = 600), courseStarted = true)

        assertEquals(CourseTodayLine(minutes = 10, newWords = 10, reviews = 3), line)
    }

    @Test
    fun minutesRoundUpSoAShortDayNeverSaysZero() {
        val line = courseTodayLine(plan(newWords = 1, reviews = 0, seconds = 50), courseStarted = true)

        assertEquals(1, line?.minutes)
    }

    @Test
    fun aCourseThatHasNotStartedGivesNoLine() {
        assertNull(courseTodayLine(plan(newWords = 10, reviews = 0, seconds = 600), courseStarted = false))
    }

    @Test
    fun anEmptyDayGivesNoLine() {
        assertNull(courseTodayLine(TodayPlan(emptyList(), 0), courseStarted = true))
    }
}
