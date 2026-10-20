package app.awn.quran.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignmentTrackerTest {

    private val wordsPerAyah = 3
    private val ayahCount = 5
    private val phonemesPerWord = 6
    private val alphabet = "abcdefghijklmnopqrstuvwxyz"

    // Word k is six consecutive letters of the alphabet starting at 5 * k, so neighbours differ.
    private val wordTexts =
        List(wordsPerAyah * ayahCount) { wordIndex ->
            String(
                CharArray(phonemesPerWord) { offset ->
                    alphabet[(wordIndex * 5 + offset * 3) % alphabet.length]
                },
            )
        }

    private val reference = wordTexts.mapIndexed { wordIndex, text ->
        val key = WordKeyLike(1, wordIndex / wordsPerAyah + 1, wordIndex % wordsPerAyah + 1)
        ReferenceWord(key, text.map { it.toString() })
    }

    private fun newTracker(speed: Float = 0.5f): AlignmentTracker {
        val tracker = AlignmentTracker(TrackerConfig(speed))
        tracker.load(reference)
        return tracker
    }

    private fun heard(text: String): List<PhonemeEvent> = text.mapIndexed { position, character ->
        PhonemeEvent(character.toString(), position * 0.04f, -1f)
    }

    // Feeds the text a few phonemes at a time, as the model delivers them.
    private fun AlignmentTracker.hear(text: String): List<TrackerEvent> {
        val reported = mutableListOf<TrackerEvent>()
        for (piece in text.chunked(3)) reported += onPhonemes(heard(piece))
        return reported
    }

    @Test
    fun everyWordOfAPerfectRecitationTurnsGreen() {
        val tracker = newTracker()

        tracker.hear(wordTexts.take(7).joinToString(""))

        for (index in 0 until 7) {
            assertEquals("word $index", WordState.Confirmed, tracker.stateOfWord(index))
        }
        assertEquals(WordState.Neutral, tracker.stateOfWord(7))
        assertEquals(7, tracker.currentIndex())
    }

    @Test
    fun halfAWordIsYellowUntilEnoughIsHeard() {
        val tracker = newTracker()

        tracker.hear(wordTexts[0] + wordTexts[1].take(3))

        assertEquals(WordState.Confirmed, tracker.stateOfWord(0))
        assertEquals(WordState.Tentative, tracker.stateOfWord(1))
    }

    @Test
    fun aFewPhonemesAtTheStartColourNothing() {
        val tracker = newTracker()

        val events = tracker.hear(wordTexts[0].take(3))

        assertTrue(events.isEmpty())
        assertEquals(WordState.Neutral, tracker.stateOfWord(0))
    }

    @Test
    fun aLeftOutWordIsSkippedOnceTheNextWordsAreHeard() {
        val tracker = newTracker()

        val events = tracker.hear(wordTexts[0] + wordTexts[1] + wordTexts[3] + wordTexts[4])

        assertTrue(TrackerEvent.Skipped(2) in events)
        assertEquals(1, events.filterIsInstance<TrackerEvent.Skipped>().size)
        assertEquals(WordState.Wrong, tracker.stateOfWord(2))
        assertEquals(WordState.Confirmed, tracker.stateOfWord(3))
    }

    @Test
    fun aWholeLeftOutAyahIsSkippedWordByWord() {
        val tracker = newTracker()

        val events = tracker.hear(wordTexts.take(3).joinToString("") + wordTexts[6] + wordTexts[7])

        val skipped = events.filterIsInstance<TrackerEvent.Skipped>().map { it.index }
        assertEquals(listOf(3, 4, 5), skipped)
    }

    @Test
    fun aRecitationWithoutOmissionsReportsNoSkip() {
        val tracker = newTracker()

        val events = tracker.hear(wordTexts.take(10).joinToString(""))

        assertTrue(events.filterIsInstance<TrackerEvent.Skipped>().isEmpty())
        assertTrue(events.filterIsInstance<TrackerEvent.Inserted>().isEmpty())
    }

    @Test
    fun extraSoundsBetweenWordsAreReportedAsInsertion() {
        val tracker = newTracker()
        val extraSounds = "ZXQWVUTS"

        val events = tracker.hear(wordTexts[0] + wordTexts[1] + extraSounds + wordTexts[2])

        assertEquals(
            listOf(TrackerEvent.Inserted(1)),
            events.filterIsInstance<TrackerEvent.Inserted>(),
        )
        assertEquals(WordState.Confirmed, tracker.stateOfWord(2))
    }

    @Test
    fun aWordWithTwoSubstitutionsTurnsRedWhenTheNextWordStarts() {
        val tracker = newTracker()
        val spoken = wordTexts[1].replaceRange(1, 3, "XY")

        val events = tracker.hear(wordTexts[0] + spoken + wordTexts[2])

        val substitutions = events.filterIsInstance<TrackerEvent.Substitution>()
        assertEquals(listOf(1, 2), substitutions.map { it.phonemeIndex })
        assertEquals(listOf("X", "Y"), substitutions.map { it.heard })
        assertEquals(
            wordTexts[1].substring(1, 3).map { it.toString() },
            substitutions.map { it.expected },
        )
        assertEquals(WordState.Wrong, tracker.stateOfWord(1))
        assertEquals(WordState.Confirmed, tracker.stateOfWord(2))
    }

    @Test
    fun aWordWithOneSubstitutionStillTurnsGreen() {
        val tracker = newTracker()
        val spoken = wordTexts[0].replaceRange(2, 3, "X")

        tracker.hear(spoken + wordTexts[1])

        assertEquals(WordState.Confirmed, tracker.stateOfWord(0))
    }

    @Test
    fun jumpingFarAheadAnchorsToTheNewPlace() {
        val tracker = newTracker()
        tracker.hear(wordTexts.take(3).joinToString(""))

        // Ayah 5 starts at word 12, beyond the window of the current and next two ayahs.
        val events = tracker.hear(wordTexts[12] + wordTexts[13] + wordTexts[14])

        assertEquals(
            listOf(TrackerEvent.Anchored(12)),
            events.filterIsInstance<TrackerEvent.Anchored>(),
        )
        assertEquals(WordState.Confirmed, tracker.stateOfWord(13))
    }

    @Test
    fun goingBackToAnEarlierAyahAnchorsThereAndClearsLaterWords() {
        val tracker = newTracker()
        tracker.hear(wordTexts.take(9).joinToString(""))
        assertEquals(WordState.Confirmed, tracker.stateOfWord(6))

        val events = tracker.hear(wordTexts[3] + wordTexts[4] + wordTexts[5])

        assertTrue("events $events", TrackerEvent.Anchored(3) in events)
        assertEquals(WordState.Confirmed, tracker.stateOfWord(4))
    }

    @Test
    fun resetForgetsEverythingHeard() {
        val tracker = newTracker()
        tracker.hear(wordTexts.take(3).joinToString(""))

        tracker.reset()

        assertEquals(WordState.Neutral, tracker.stateOfWord(0))
        assertEquals(0, tracker.currentIndex())
    }

    @Test
    fun loadingWithAStartIndexBeginsThere() {
        val tracker = AlignmentTracker()
        tracker.load(reference, startIndex = 3)

        tracker.hear(wordTexts[3] + wordTexts[4])

        assertEquals(WordState.Confirmed, tracker.stateOfWord(3))
        assertEquals(WordState.Neutral, tracker.stateOfWord(0))
    }

    @Test
    fun defaultSpeedGivesTheThresholdsOfThePlan() {
        val thresholds = AlignmentThresholds.forSpeed(0.5f)

        assertEquals(0.8f, thresholds.greenCoverage, 0.001f)
        assertEquals(6, thresholds.insertionPhonemes)
        assertEquals(12, thresholds.reanchorPhonemes)
        assertEquals(1, thresholds.maximumGreenSubstitutions)
        assertEquals(2, thresholds.wrongSubstitutions)
    }

    @Test
    fun fasterSpeedAsksForLessBeforeJudging() {
        val patient = AlignmentThresholds.forSpeed(0f)
        val eager = AlignmentThresholds.forSpeed(1f)

        assertTrue(eager.greenCoverage < patient.greenCoverage)
        assertTrue(eager.insertionPhonemes < patient.insertionPhonemes)
        assertTrue(eager.reanchorPhonemes < patient.reanchorPhonemes)
        assertTrue(eager.skipEvidencePhonemes < patient.skipEvidencePhonemes)
    }

    @Test
    fun speedOutsideZeroToOneIsClamped() {
        assertEquals(AlignmentThresholds.forSpeed(1f), AlignmentThresholds.forSpeed(7f))
        assertEquals(AlignmentThresholds.forSpeed(0f), AlignmentThresholds.forSpeed(-3f))
    }

    @Test
    fun blankSymbolsAreIgnored() {
        val tracker = newTracker()

        val events = tracker.onPhonemes(listOf(PhonemeEvent("<blank>", 0f, -1f)))

        assertFalse(events.isNotEmpty())
        assertEquals(WordState.Neutral, tracker.stateOfWord(0))
    }
}
