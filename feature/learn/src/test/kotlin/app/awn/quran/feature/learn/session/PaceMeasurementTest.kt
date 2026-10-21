package app.awn.quran.feature.learn.session

import org.junit.Assert.assertEquals
import org.junit.Test

class PaceMeasurementTest {

    private fun timings(kind: StepKind, vararg seconds: Int) =
        seconds.map { StepTiming(kind, it * 1000L) }

    @Test
    fun withoutTimingsTheDefaultsStand() {
        assertEquals(Pace.Default, PaceMeasurement.estimate(emptyList()))
    }

    @Test
    fun fewerThanFiveTimingsKeepTheDefault() {
        val pace = PaceMeasurement.estimate(timings(StepKind.Review, 4, 4, 4, 4))

        assertEquals(10.0, pace.secondsPerReview, 0.0)
    }

    @Test
    fun theMedianOfTheTimingsIsTheMeasuredPace() {
        val pace = PaceMeasurement.estimate(timings(StepKind.Review, 6, 7, 8, 9, 30))

        assertEquals(8.0, pace.secondsPerReview, 0.0)
    }

    @Test
    fun anEvenNumberOfTimingsUsesTheMiddlePair() {
        val pace = PaceMeasurement.estimate(timings(StepKind.Review, 5, 6, 8, 9, 7, 9))

        assertEquals(7.5, pace.secondsPerReview, 0.0)
    }

    @Test
    fun oneForgottenQuestionDoesNotMoveTheMedian() {
        val pace = PaceMeasurement.estimate(timings(StepKind.Review, 8, 8, 8, 8, 8, 8, 3600))

        assertEquals(8.0, pace.secondsPerReview, 0.0)
    }

    @Test
    fun aMeasuredPaceStaysWithinAFactorOfThreeOfTheDefault() {
        val slow = PaceMeasurement.estimate(timings(StepKind.Review, 120, 120, 120, 120, 120))
        val fast = PaceMeasurement.estimate(timings(StepKind.Review, 1, 1, 1, 1, 1))

        assertEquals(30.0, slow.secondsPerReview, 1e-9)
        assertEquals(10.0 / 3, fast.secondsPerReview, 1e-9)
    }

    @Test
    fun onlyTheLatestThirtyTimingsCount() {
        val old = timings(StepKind.Review, *IntArray(30) { 25 })
        val recent = timings(StepKind.Review, *IntArray(30) { 6 })

        val pace = PaceMeasurement.estimate(old + recent)

        assertEquals(6.0, pace.secondsPerReview, 0.0)
    }

    @Test
    fun eachKindIsMeasuredOnItsOwn() {
        val pace =
            PaceMeasurement.estimate(
                timings(StepKind.NewWord, 20, 20, 20, 20, 20) +
                    timings(StepKind.Lesson, 200, 200, 200, 200, 200),
            )

        assertEquals(10.0, pace.secondsPerReview, 0.0)
        assertEquals(20.0, pace.secondsPerNewWord, 0.0)
        assertEquals(200.0, pace.secondsPerLesson, 0.0)
    }
}
