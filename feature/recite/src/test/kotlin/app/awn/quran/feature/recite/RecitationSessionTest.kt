package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.tracker.DurationCheck
import app.awn.quran.tracker.DurationVerdict
import app.awn.quran.tracker.TrackerConfig
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationSessionTest {

    @Test
    fun loadShowsEveryWordNeutralAndPointsAtTheFirst() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)

        assertEquals(RecitationPhase.Idle, fixture.session.phase.value)
        assertEquals(
            mapOf(
                fixture.first to WordState.Neutral,
                fixture.second to WordState.Neutral,
                fixture.third to WordState.Neutral,
            ),
            fixture.session.wordStates.value,
        )
        assertEquals(fixture.first, fixture.session.currentWord.value)
    }

    @Test
    fun anAyahWithoutReferenceEndsInError() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(ReciteRange(surah = 1, firstAyah = 1, lastAyah = 9))

        assertEquals(RecitationPhase.Error, fixture.session.phase.value)
        assertTrue(fixture.session.wordStates.value.isEmpty())
    }

    @Test
    fun wordChangedEventsUpdateTheMapAndMoveTheCurrentWord() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.script(fixture.confirmed(0))
        fixture.session.start()

        assertEquals(WordState.Confirmed, fixture.session.wordStates.value[fixture.first])
        assertEquals(fixture.second, fixture.session.currentWord.value)
        assertEquals(RecitationPhase.Stopped, fixture.session.phase.value)
    }

    @Test
    fun skippedEventSetsStatusTextDataAndMarksTheWordWrong() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.script(TrackerEvent.Skipped(index = 1))
        fixture.session.start()

        assertEquals(ReciteStatus.Skipped(ayah = 1, wordPosition = 2), fixture.session.status.value)
        assertEquals(WordState.Wrong, fixture.session.wordStates.value[fixture.second])
        assertEquals(setOf(fixture.second), fixture.session.skippedWords.value)
    }

    @Test
    fun insertedAndAnchoredEventsSetTheirStatus() = runTest {
        val fixture = SessionFixture(inputBlocks = 2)
        fixture.session.load(fixture.range)
        fixture.script(TrackerEvent.Inserted(afterIndex = 0), TrackerEvent.Anchored(index = 2))
        fixture.script(TrackerEvent.Anchored(index = 2))
        fixture.session.start()

        assertEquals(
            ReciteStatus.Anchored(ayah = 2, wordPosition = 1),
            fixture.session.status.value,
        )
    }

    @Test
    fun stopKeepsTheResultsAndStartingAgainClearsThem() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.script(fixture.confirmed(0))
        fixture.session.start()
        fixture.session.stop()
        assertEquals(WordState.Confirmed, fixture.session.wordStates.value[fixture.first])

        fixture.session.start()

        assertEquals(WordState.Neutral, fixture.session.wordStates.value[fixture.first])
    }

    @Test
    fun resetClearsResultsAndReturnsToIdle() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.script(fixture.confirmed(0), TrackerEvent.Skipped(1))
        fixture.session.start()

        fixture.session.reset()

        assertEquals(RecitationPhase.Idle, fixture.session.phase.value)
        assertNull(fixture.session.status.value)
        assertTrue(fixture.session.wordStates.value.values.all { it == WordState.Neutral })
    }

    @Test
    fun setSpeedReachesTheTracker() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)

        fixture.session.setSpeed(0.8f)

        assertEquals(TrackerConfig(0.8f), fixture.tracker.config)
        assertEquals(0.8f, fixture.session.speed.value)
    }

    @Test
    fun speedIsClampedToTheSliderRange() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)

        fixture.session.setSpeed(3f)

        assertEquals(1f, fixture.tracker.config.speed)
    }

    @Test
    fun hifzHidesUnconfirmedWordsAndRevealsConfirmedOnes() = runTest {
        val fixture = SessionFixture(inputBlocks = 2)
        fixture.session.load(fixture.range)
        fixture.session.setHifzEnabled(true)
        fixture.script(TrackerEvent.WordChanged(0, WordState.Tentative))
        fixture.script(fixture.confirmed(0))
        fixture.session.start()

        val states = fixture.session.wordStates.value
        assertEquals(WordState.Confirmed, states[fixture.first])
        assertEquals(WordState.Hidden, states[fixture.second])
        assertEquals(WordState.Hidden, states[fixture.third])
    }

    @Test
    fun hifzShowsASkippedWordAsWrong() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.session.setHifzEnabled(true)
        fixture.script(TrackerEvent.Skipped(0))
        fixture.session.start()

        assertEquals(WordState.Wrong, fixture.session.wordStates.value[fixture.first])
    }

    @Test
    fun hintRevealsTheFirstLetterOfTheNextHiddenWord() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.session.setHifzEnabled(true)

        fixture.session.requestHint()

        assertEquals(ReciteHint(fixture.first, "ب"), fixture.session.hint.value)
    }

    @Test
    fun hintIsClearedOnceItsWordIsConfirmed() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.session.setHifzEnabled(true)
        fixture.session.requestHint()
        fixture.script(fixture.confirmed(0))
        fixture.session.start()

        assertNull(fixture.session.hint.value)
    }

    @Test
    fun hintDoesNothingOutsideHifz() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)

        fixture.session.requestHint()

        assertNull(fixture.session.hint.value)
    }

    @Test
    fun anAyahWithEveryWordConfirmedIsReportedGreenOnce() = runTest {
        val fixture = SessionFixture(inputBlocks = 3)
        fixture.session.load(fixture.range)
        fixture.script(fixture.confirmed(0))
        fixture.script(fixture.confirmed(1))
        fixture.script(TrackerEvent.WordChanged(1, WordState.Confirmed))
        fixture.session.start()

        assertEquals(listOf(AyahRef(1, 1)), fixture.progress.greenAyahs)
        assertTrue(fixture.progress.hifzCompletions.isEmpty())
    }

    @Test
    fun hifzReportsTheAyahWithItsSkipCount() = runTest {
        val fixture = SessionFixture(inputBlocks = 2)
        fixture.session.load(fixture.range)
        fixture.session.setHifzEnabled(true)
        fixture.script(TrackerEvent.Skipped(0))
        fixture.script(fixture.confirmed(1))
        fixture.session.start()

        assertEquals(listOf(AyahRef(1, 1) to 1), fixture.progress.hifzCompletions)
        assertTrue(fixture.progress.greenAyahs.isEmpty())
    }

    @Test
    fun aSubstitutionEventAppearsInTheReviewOfItsWord() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        val substitution = TrackerEvent.Substitution(0, 2, heard = "س", expected = "ص")
        fixture.script(fixture.confirmed(0), substitution)
        fixture.session.start()

        val review = fixture.session.reviews.value.getValue(1).single()

        assertEquals(fixture.first, review.key)
        assertEquals(listOf(ReviewFinding.Pronunciation(substitution)), review.findings)
    }

    @Test
    fun anOverheldDurationEventFlagsItsWord() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        val check = DurationCheck(1, "ا", "natural", 1.2f, 0.4f, DurationVerdict.Overheld)
        fixture.script(fixture.confirmed(0), fixture.confirmed(1), TrackerEvent.Duration(check))
        fixture.session.start()

        val review = fixture.session.reviews.value.getValue(1).single()

        assertEquals(fixture.second, review.key)
        assertEquals(listOf(ReviewFinding.Duration(check)), review.findings)
    }

    @Test
    fun aWrongWordCountsAsPassedAndAsAMistakeInHifz() = runTest {
        val fixture = SessionFixture(inputBlocks = 2)
        fixture.session.load(fixture.range)
        fixture.session.setHifzEnabled(true)
        fixture.script(TrackerEvent.WordChanged(0, WordState.Wrong))
        fixture.script(fixture.confirmed(1))
        fixture.session.start()

        assertEquals(fixture.third, fixture.session.currentWord.value)
        assertEquals(listOf(AyahRef(1, 1) to 1), fixture.progress.hifzCompletions)
    }
}
