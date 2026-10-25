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

    private fun newTracker(strictness: Strictness = Strictness.Normal): AlignmentTracker {
        val tracker = AlignmentTracker(TrackerConfig(strictness))
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
    fun continuingKeepsTheEarlierWordsAndFollowsOnInANewRun() {
        val tracker = newTracker()
        tracker.hear(wordTexts.take(3).joinToString(""))

        // A new microphone run: its phoneme times start at zero again.
        tracker.continueFrom(tracker.currentIndex())
        tracker.hear(wordTexts.slice(3 until 6).joinToString(""))

        for (index in 0 until 6) {
            assertEquals("word $index", WordState.Confirmed, tracker.stateOfWord(index))
        }
        assertEquals(6, tracker.currentIndex())
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
    fun jumpingFarAheadMarksTheWordsBetweenUnreadAndNotWrong() {
        val tracker = newTracker()
        tracker.hear(wordTexts.take(3).joinToString(""))

        // Ayahs 2 and 3 are left out: the user goes on at ayah 4 and keeps reciting.
        val events = tracker.hear(wordTexts.slice(9 until 15).joinToString(""))

        val jumps = events.filterIsInstance<TrackerEvent.JumpedAhead>()
        assertEquals("events $events", 1, jumps.size)
        assertEquals(3, jumps.single().fromWord)
        assertTrue(events.filterIsInstance<TrackerEvent.Anchored>().isNotEmpty())
        for (index in 3 until 9) {
            assertEquals("word $index", WordState.Unread, tracker.stateOfWord(index))
        }
        assertEquals(WordState.Confirmed, tracker.stateOfWord(13))
        assertTrue(events.none { it is TrackerEvent.Skipped })
    }

    @Test
    fun goingBackToAnEarlierAyahKeepsEveryMark() {
        val tracker = newTracker()
        tracker.hear(wordTexts.take(9).joinToString(""))

        // The user goes back to ayah 2 and recites ayahs 2 and 3 again, then goes on.
        val events = tracker.hear(wordTexts.slice(3 until 10).joinToString(""))

        assertTrue("events $events", events.any { it is TrackerEvent.Anchored })
        val marked = events.filterIsInstance<TrackerEvent.WordChanged>().map { it.state }
        assertTrue("marks $marked", marked.none { it == WordState.Wrong || it == WordState.Unread })
        for (index in 0 until 10) {
            assertEquals("word $index", WordState.Confirmed, tracker.stateOfWord(index))
        }
        assertEquals(10, tracker.currentIndex())
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
    fun normalStrictnessGivesTheTunedThresholds() {
        val thresholds = AlignmentThresholds.forStrictness(Strictness.Normal)

        assertEquals(0.20, thresholds.allowedCost(3), 0.0)
        assertEquals(0.26, thresholds.allowedCost(4), 0.0)
        assertEquals(0.30, thresholds.allowedCost(8), 0.0)
        assertEquals(6, thresholds.insertionPhonemes)
        assertEquals(30, thresholds.lostPhonemes)
    }

    @Test
    fun aStricterLevelAllowsLessInEveryLengthClass() {
        val lenient = AlignmentThresholds.forStrictness(Strictness.Lenient)
        val normal = AlignmentThresholds.forStrictness(Strictness.Normal)
        val strict = AlignmentThresholds.forStrictness(Strictness.Strict)

        for (length in listOf(2, 5, 9)) {
            assertTrue(strict.allowedCost(length) < normal.allowedCost(length))
            assertTrue(normal.allowedCost(length) < lenient.allowedCost(length))
        }
    }

    @Test
    fun theOldSpeedValueMapsToTheThreeLevels() {
        assertEquals(Strictness.Lenient, Strictness.fromSpeed(0f))
        assertEquals(Strictness.Normal, Strictness.fromSpeed(0.5f))
        assertEquals(Strictness.Strict, Strictness.fromSpeed(1f))
        assertEquals(Strictness.Strict, Strictness.fromSpeed(7f))
        assertEquals(Strictness.Lenient, Strictness.fromSpeed(-3f))
    }

    @Test
    fun blankSymbolsAreIgnored() {
        val tracker = newTracker()

        val events = tracker.onPhonemes(listOf(PhonemeEvent("<blank>", 0f, -1f)))

        assertFalse(events.isNotEmpty())
        assertEquals(WordState.Neutral, tracker.stateOfWord(0))
    }

    // ---- Appending reference words ------------------------------------------------------

    private fun AlignmentTracker.loadFirst(wordCount: Int) = load(reference.take(wordCount))

    @Test
    fun appendedWordsContinueWithoutForgettingEarlierResults() {
        val tracker = AlignmentTracker(TrackerConfig())
        tracker.loadFirst(6)
        tracker.hear(wordTexts.take(5).joinToString(""))

        tracker.append(reference.drop(6).take(6))
        tracker.hear(wordTexts.drop(5).take(5).joinToString(""))

        for (index in 0 until 10) {
            assertEquals("word $index", WordState.Confirmed, tracker.stateOfWord(index))
        }
        assertEquals(10, tracker.currentIndex())
    }

    @Test
    fun theLastLoadedWordSettlesOnceTheUserReachesAppendedWords() {
        val tracker = AlignmentTracker(TrackerConfig())
        tracker.loadFirst(6)
        tracker.hear(wordTexts.take(6).joinToString(""))
        assertEquals(6, tracker.currentIndex())

        // The user recites on past the end of the loaded words and the app extends them.
        tracker.append(reference.drop(6).take(3))
        val events = tracker.hear(wordTexts.drop(6).take(3).joinToString(""))

        assertTrue(events.none { it is TrackerEvent.Skipped })
        assertEquals(WordState.Confirmed, tracker.stateOfWord(5))
        assertEquals(WordState.Confirmed, tracker.stateOfWord(8))
        assertEquals(9, tracker.currentIndex())
    }

    @Test
    fun appendedWordsReportEventsWithIndexesAfterTheLoadedOnes() {
        val tracker = AlignmentTracker(TrackerConfig())
        tracker.loadFirst(6)
        tracker.hear(wordTexts.take(6).joinToString(""))
        tracker.append(reference.drop(6).take(6))

        // Word 9 is left out; the skip carries its index in the extended list.
        val events =
            tracker.hear(
                wordTexts[6] + wordTexts[7] + wordTexts[8] + wordTexts[10] + wordTexts[11],
            )

        assertTrue(TrackerEvent.Skipped(9) in events)
    }

    @Test
    fun appendingNothingChangesNothing() {
        val tracker = AlignmentTracker(TrackerConfig())
        tracker.loadFirst(6)
        tracker.hear(wordTexts.take(3).joinToString(""))

        tracker.append(emptyList())

        assertEquals(WordState.Confirmed, tracker.stateOfWord(0))
        assertEquals(3, tracker.currentIndex())
    }

    @Test
    fun appendingSeveralTimesIsTheSameAsLoadingEverythingAtOnce() {
        val grown = AlignmentTracker(TrackerConfig())
        grown.loadFirst(3)
        grown.append(reference.subList(3, 9))
        grown.append(reference.subList(9, reference.size))
        val whole = newTracker()
        val recital = wordTexts.take(12).joinToString("")

        val grownEvents = grown.hear(recital)
        val wholeEvents = whole.hear(recital)

        assertEquals(wholeEvents, grownEvents)
        assertEquals(whole.currentIndex(), grown.currentIndex())
    }
}
