package app.awn.quran.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of the cursor tracker on made-up words, where every letter is one phoneme and no two
 * letters are near sounds. Words 1 and 4 are short (three phonemes); word 6 is long (eight). Ayahs
 * have four words; ayah 4 (words 12 to 15) repeats the texts of words 2 to 5, a phrase that comes
 * twice on the page.
 */
class CursorRulesTest {

    private val wordTexts =
        listOf(
            "abcdef",
            "ghi",
            "jklmno",
            "pqrstu",
            "vwx",
            "yzABCD",
            "EFGHIJKL",
            "MNOPQR",
            "STUVWX",
            "YZ0123",
            "456789",
            "!@#$%^",
            "jklmno",
            "pqrstu",
            "vwx",
            "yzABCD",
            "&*()+=",
        )

    private val reference = wordTexts.mapIndexed { index, text ->
        ReferenceWord(WordKeyLike(1, index / 4 + 1, index % 4 + 1), text.map { it.toString() })
    }

    private fun newTracker(): AlignmentTracker =
        AlignmentTracker(TrackerConfig()).also { it.load(reference) }

    // Feeds the text three phonemes at a time; onsets step 0.1 s from [startSeconds].
    private fun AlignmentTracker.hear(text: String, startSeconds: Float = 0f): List<TrackerEvent> {
        val reported = mutableListOf<TrackerEvent>()
        for ((pieceIndex, piece) in text.chunked(3).withIndex()) {
            val events = piece.mapIndexed { position, character ->
                val onset = startSeconds + (pieceIndex * 3 + position) * STEP_SECONDS
                PhonemeEvent(character.toString(), onset, -1f)
            }
            reported += onPhonemes(events)
        }
        return reported
    }

    private fun words(vararg indexes: Int) = indexes.joinToString("") { wordTexts[it] }

    private fun redWordsPerUpdate(tracker: AlignmentTracker, text: String): Int {
        var most = 0
        for (piece in text.chunked(3)) {
            val events = tracker.hear(piece)
            val red = events.count { it is TrackerEvent.WordChanged && it.state == WordState.Wrong }
            most = maxOf(most, red)
        }
        return most
    }

    @Test
    fun aShortWordHeardOnlyInPartIsNotGreen() {
        val tracker = newTracker()

        tracker.hear(words(0) + "gh")

        assertNotEquals(WordState.Confirmed, tracker.stateOfWord(1))
    }

    @Test
    fun aShortWordHeardExactlyIsGreen() {
        val tracker = newTracker()

        tracker.hear(words(0, 1))

        assertEquals(WordState.Confirmed, tracker.stateOfWord(1))
    }

    @Test
    fun aMispronouncedWordStaysTentativeUntilTheNextWordIsGreen() {
        val tracker = newTracker()
        tracker.hear(words(0, 1) + "jkXYZo")

        assertEquals(WordState.Tentative, tracker.stateOfWord(2))

        tracker.hear(words(3))

        assertEquals(WordState.Wrong, tracker.stateOfWord(2))
        assertEquals(WordState.Confirmed, tracker.stateOfWord(3))
    }

    @Test
    fun noStepColoursMoreThanTwoWordsRed() {
        val tracker = newTracker()
        tracker.hear(words(0))

        // Words 1 to 9 are passed over; the user goes on at word 10 and recites on.
        val most = redWordsPerUpdate(tracker, words(10, 11, 12, 13, 14, 15, 16))

        assertTrue("most red in one update $most", most <= 2)
        for (index in 1..9) assertNotEquals(
            "word $index",
            WordState.Wrong,
            tracker.stateOfWord(index),
        )
    }

    @Test
    fun aShortWordAloneDoesNotProveTheWordBeforeWasLeftOut() {
        val tracker = newTracker()

        val events = tracker.hear(words(0, 1, 2, 4))

        assertTrue(events.none { it is TrackerEvent.Skipped })
        assertNotEquals(WordState.Wrong, tracker.stateOfWord(3))
    }

    @Test
    fun repeatingTheLastWordsKeepsThemGreenAndMovesTheCurrentWordBack() {
        val tracker = newTracker()
        tracker.hear(words(0, 1, 2, 3))

        // The repeat shows once the word after the repeated one has begun.
        val events = tracker.hear(words(2) + "pq")

        assertTrue("events $events", TrackerEvent.Repeated(2, 3) in events)
        assertEquals(3, tracker.currentIndex())

        tracker.hear("rstu" + words(4, 5))

        for (index in 0..5) assertEquals(
            "word $index",
            WordState.Confirmed,
            tracker.stateOfWord(index),
        )
        assertEquals(6, tracker.currentIndex())
    }

    @Test
    fun aWrongWordSaidRightAgainIsCorrected() {
        val tracker = newTracker()
        tracker.hear(words(0, 1) + "jkXYZo" + words(3))
        assertEquals(WordState.Wrong, tracker.stateOfWord(2))

        val events = tracker.hear(words(2, 3, 4, 5))

        assertTrue("events $events", TrackerEvent.Corrected(2) in events)
        assertEquals(WordState.Confirmed, tracker.stateOfWord(2))
        assertEquals(WordState.Confirmed, tracker.stateOfWord(5))
    }

    @Test
    fun aSlipBeforeAPhraseThatComesAgainLaterDoesNotJump() {
        val tracker = newTracker()

        val events = tracker.hear(words(0, 1) + "jkXYZo" + words(3, 4, 5, 6))

        assertTrue(events.none { it is TrackerEvent.Anchored || it is TrackerEvent.JumpedAhead })
        for (index in 12..15) assertEquals(WordState.Neutral, tracker.stateOfWord(index))
        assertEquals(WordState.Confirmed, tracker.stateOfWord(6))
    }

    @Test
    fun aWordWithoutItsLastConsonantWaitsForAPause() {
        val tracker = newTracker()
        tracker.hear(words(0, 1, 2, 3, 4, 5))
        tracker.hear(wordTexts[6].dropLast(1), startSeconds = 10f)

        // A sound that is not the missing consonant, with no gap: the word still waits.
        tracker.onPhonemes(listOf(PhonemeEvent(UNRELATED, 10.7f, -1f)))

        assertEquals(WordState.Tentative, tracker.stateOfWord(6))

        // The next token comes after a breath.
        tracker.onPhonemes(listOf(PhonemeEvent("M", 12f, -1f)))

        assertEquals(WordState.Confirmed, tracker.stateOfWord(6))
    }

    @Test
    fun aWordWithoutItsLastConsonantClosesWhenTheNextWordStarts() {
        val tracker = newTracker()
        tracker.hear(words(0, 1, 2, 3, 4, 5) + wordTexts[6].dropLast(1) + UNRELATED)

        assertEquals(WordState.Tentative, tracker.stateOfWord(6))

        tracker.hear(wordTexts[7])

        assertEquals(WordState.Confirmed, tracker.stateOfWord(6))
        assertEquals(WordState.Confirmed, tracker.stateOfWord(7))
    }

    @Test
    fun aHeldWordIsSaidWhenTheRunStops() {
        val tracker = newTracker()
        tracker.hear(words(0, 1, 2, 3, 4, 5) + wordTexts[6].dropLast(1))

        val events = tracker.closePending()

        assertEquals(WordState.Confirmed, tracker.stateOfWord(6))
        assertTrue(TrackerEvent.WordChanged(6, WordState.Confirmed) in events)
        assertEquals(7, tracker.currentIndex())
    }

    @Test
    fun aHeldWordStaysGreenWhenTheUserGoesOnAfterListening() {
        val tracker = newTracker()
        tracker.hear(words(0, 1, 2, 3, 4, 5) + wordTexts[6].dropLast(1))

        // What the session does on Listen: close the run, then go on from the current word.
        tracker.closePending()
        tracker.continueFrom(tracker.currentIndex())
        tracker.hear(words(7, 8, 9))

        assertEquals(WordState.Confirmed, tracker.stateOfWord(6))
        assertEquals(WordState.Confirmed, tracker.stateOfWord(8))
    }

    @Test
    fun theLastLoadedWordHeldIsSaidWhenTheRunStops() {
        val tracker = newTracker()
        val lastWord = wordTexts.lastIndex
        tracker.hear(
            words(*(0 until lastWord).toList().toIntArray()) + wordTexts[lastWord].dropLast(1),
        )

        assertEquals(WordState.Tentative, tracker.stateOfWord(lastWord))

        tracker.closePending()

        assertEquals(WordState.Confirmed, tracker.stateOfWord(lastWord))
    }

    @Test
    fun closingWithNothingHeldChangesNothing() {
        val tracker = newTracker()
        tracker.hear(words(0, 1, 2))

        assertTrue(tracker.closePending().isEmpty())
        assertEquals(3, tracker.currentIndex())
    }

    @Test
    fun aHeldWordAfterLongUnrelatedSpeechIsJudgedAsWithoutIt() {
        val withPreamble = newTracker()
        withPreamble.hear(words(0, 1, 2, 3, 4, 5))
        // More unrelated speech than the heard audio keeps, so its front is trimmed.
        withPreamble.hear("?~".repeat(UNRELATED_PHONEMES / 2), startSeconds = 10f)
        val withPreambleEvents = heldWordWithASwapThenAPause(withPreamble, startSeconds = 20f)

        val without = newTracker()
        without.hear(words(0, 1, 2, 3, 4, 5))
        val withoutEvents = heldWordWithASwapThenAPause(without, startSeconds = 20f)

        assertEquals(WordState.Confirmed, without.stateOfWord(6))
        assertEquals(without.stateOfWord(6), withPreamble.stateOfWord(6))
        assertEquals(
            withoutEvents.filterIsInstance<TrackerEvent.Substitution>(),
            withPreambleEvents.filterIsInstance<TrackerEvent.Substitution>(),
        )
    }

    // Word 6 with J heard as X and its last consonant missing, in one update, then a token after a
    // pause.
    private fun heldWordWithASwapThenAPause(
        tracker: AlignmentTracker,
        startSeconds: Float,
    ): List<TrackerEvent> {
        val heldWord =
            "EFGHIXK"
                .mapIndexed { position, character ->
                    PhonemeEvent(character.toString(), startSeconds + position * STEP_SECONDS, -1f)
                }
        val reported = tracker.onPhonemes(heldWord).toMutableList()
        reported += tracker.onPhonemes(listOf(PhonemeEvent(UNRELATED, startSeconds + 3f, -1f)))
        return reported
    }

    @Test
    fun aPhraseFoundTwiceIsATieAndNotAPlace() {
        val tables = ReferenceTables().also { it.rebuild(reference) }
        val query = tables.idsOfWords(2, 5)

        val place =
            LostSearch.find(query, tables, AlignmentThresholds.forStrictness(Strictness.Normal))

        assertEquals(null, place)
    }

    private companion object {
        const val STEP_SECONDS = 0.1f

        // A sound that is in no word of the text.
        const val UNRELATED = "?"

        // More than the 64 heard phonemes the tracker keeps.
        const val UNRELATED_PHONEMES = 70
    }
}
