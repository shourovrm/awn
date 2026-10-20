package app.awn.quran.feature.read

import app.awn.quran.feature.read.quran.ResumeTracker
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeTrackerTest {
    private val saved = mutableListOf<Int>()
    private val testScope = TestScope(StandardTestDispatcher())
    private val tracker = ResumeTracker(testScope, { page -> saved += page }, debounceMs = 1_500)

    private fun TestScope.pass(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun aSwipeIsSavedAfterTheDebounce() =
        with(testScope) {
            tracker.onReading(5)
            pass(1_499)
            assertEquals(emptyList<Int>(), saved)

            pass(2)
            assertEquals(listOf(5), saved)
        }

    @Test
    fun swipingOnBeforeTheDebounceSavesOnlyTheLastPage() =
        with(testScope) {
            tracker.onReading(5)
            pass(1_000)
            tracker.onReading(6)
            pass(1_000)
            assertEquals(emptyList<Int>(), saved)

            pass(600)
            assertEquals(listOf(6), saved)
        }

    @Test
    fun stoppingSavesAReadingPageAtOnce() =
        with(testScope) {
            tracker.onReading(5)
            pass(200)
            tracker.onStop()
            runCurrent()

            assertEquals(listOf(5), saved)
            pass(5_000)
            assertEquals(listOf(5), saved)
        }

    @Test
    fun aLookupIsNotSavedUntilTheReaderStays() =
        with(testScope) {
            tracker.onLookup(40)
            pass(1_000)
            assertEquals(emptyList<Int>(), saved)

            pass(600)
            assertEquals(listOf(40), saved)
        }

    @Test
    fun stoppingBeforeALookupSettlesLeavesTheResumePointAlone() =
        with(testScope) {
            tracker.onReading(5)
            pass(2_000)
            tracker.onLookup(40)
            pass(300)
            tracker.onStop()
            pass(10_000)

            assertEquals(listOf(5), saved)
        }

    @Test
    fun aLookupDroppedByStopWaitsAgainWhenTheScreenReturns() =
        with(testScope) {
            tracker.onLookup(40)
            pass(300)
            tracker.onStop()
            pass(10_000)
            tracker.onStart()
            pass(1_000)
            assertEquals(emptyList<Int>(), saved)

            pass(600)
            assertEquals(listOf(40), saved)
        }

    @Test
    fun swipingAwayFromALookupMakesTheNewPageReading() =
        with(testScope) {
            tracker.onLookup(40)
            pass(300)
            tracker.onReading(41)
            tracker.onStop()
            runCurrent()

            assertEquals(listOf(41), saved)
        }
}
