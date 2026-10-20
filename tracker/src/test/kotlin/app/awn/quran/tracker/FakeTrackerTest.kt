package app.awn.quran.tracker

import app.awn.quran.tracker.fakes.FakeTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeTrackerTest {

    private val reference =
        listOf(
            ReferenceWord(WordKeyLike(1, 1, 1), listOf("b")),
            ReferenceWord(WordKeyLike(1, 1, 2), listOf("a")),
        )

    @Test
    fun scriptedConfirmationMovesCurrentIndex() {
        val tracker = FakeTracker()
        tracker.load(reference)
        tracker.scriptedBatches.add(listOf(TrackerEvent.WordChanged(0, WordState.Confirmed)))

        val events = tracker.onPhonemes(emptyList())

        assertEquals(listOf<TrackerEvent>(TrackerEvent.WordChanged(0, WordState.Confirmed)), events)
        assertEquals(1, tracker.currentIndex())
        assertEquals(WordState.Neutral, tracker.stateOfWord(1))
    }
}
