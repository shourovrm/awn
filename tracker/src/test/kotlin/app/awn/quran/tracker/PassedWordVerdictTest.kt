package app.awn.quran.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A word the user has moved past gets a final verdict: said (Confirmed, with its substitutions
 * reported so the screen can show them) or not said (Wrong). Only the word still being recited may
 * be Tentative. The old tracker left many passed words Tentative, which the screen showed as
 * "unclear".
 */
class PassedWordVerdictTest {

    private val alphabet = "abcdefghijklmnopqrstuvwxyz"

    // Six-phoneme words of letters spaced through the alphabet, so neighbours differ.
    private val wordTexts =
        List(12) { wordIndex ->
            String(
                CharArray(6) { offset -> alphabet[(wordIndex * 5 + offset * 3) % alphabet.length] },
            )
        }

    private val reference = wordTexts.mapIndexed { wordIndex, text ->
        ReferenceWord(
            WordKeyLike(1, wordIndex / 3 + 1, wordIndex % 3 + 1),
            text.map { it.toString() },
        )
    }

    private fun newTracker(): AlignmentTracker =
        AlignmentTracker(TrackerConfig()).also { it.load(reference) }

    private fun AlignmentTracker.hear(text: String): List<TrackerEvent> {
        val reported = mutableListOf<TrackerEvent>()
        for (piece in text.chunked(3)) {
            reported +=
                onPhonemes(
                    piece.mapIndexed { position, character ->
                        PhonemeEvent(character.toString(), position * 0.04f, -1f)
                    },
                )
        }
        return reported
    }

    // Replaces the phoneme at each position with a digit, which no word holds.
    private fun withWrongSounds(word: String, positions: List<Int>): String =
        String(
            word
                .mapIndexed { index, character -> if (index in positions) '7' else character }
                .toCharArray(),
        )

    @Test
    fun aPassedWordWithOneWrongSoundIsSaidAndReportsIt() {
        val tracker = newTracker()

        val events =
            tracker.hear(
                wordTexts[0] +
                    withWrongSounds(wordTexts[1], listOf(2)) +
                    wordTexts[2] +
                    wordTexts[3],
            )

        assertEquals(WordState.Confirmed, tracker.stateOfWord(1))
        assertTrue(events.filterIsInstance<TrackerEvent.Substitution>().any { it.wordIndex == 1 })
    }

    @Test
    fun aPassedWordWithMostSoundsWrongIsNotSaid() {
        val tracker = newTracker()

        tracker.hear(
            wordTexts[0] +
                withWrongSounds(wordTexts[1], listOf(0, 2, 3, 5)) +
                wordTexts[2] +
                wordTexts[3],
        )

        assertEquals(WordState.Wrong, tracker.stateOfWord(1))
    }

    @Test
    fun noPassedWordIsLeftUnsure() {
        val tracker = newTracker()

        tracker.hear(
            wordTexts[0] + wordTexts[1].take(3) + wordTexts[2] + wordTexts[3] + wordTexts[4],
        )

        for (index in 0..3) {
            assertNotEquals("word $index", WordState.Tentative, tracker.stateOfWord(index))
        }
    }
}
