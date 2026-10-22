package app.awn.quran.feature.recite

import app.awn.quran.core.data.WordKey
import app.awn.quran.tracker.DurationCheck
import app.awn.quran.tracker.DurationVerdict
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The user (2026-10-22): Awn "just said unknown", while Natlu tells whether a word was missed or
 * mispronounced. The page now has three outcomes: green (said right), yellow (said, with a wrong
 * sound or a madd held too short or too long: Mispronounced) and red (missed or another word).
 * A word the tracker is still unsure about is not marked at all.
 */
class WordVerdictRulesTest {

    private val keys = List(5) { WordKey(1, 1, it + 1) }

    private fun substitution(index: Int) =
        TrackerEvent.Substitution(wordIndex = index, phonemeIndex = 1, heard = "س", expected = "ص")

    private fun duration(index: Int, verdict: DurationVerdict) =
        DurationCheck(
            wordIndex = index,
            letter = "ا",
            rule = "madd_2",
            measuredSeconds = 0.2f,
            expectedSeconds = 0.4f,
            verdict = verdict,
        )

    private val states =
        listOf(
            WordState.Confirmed, // said cleanly
            WordState.Confirmed, // said, with a wrong sound
            WordState.Confirmed, // said, madd too short
            WordState.Wrong, // another word
            WordState.Tentative, // still being recited
        )

    private fun problems(skipped: Set<Int> = emptySet()) =
        ReviewRules.problemsByWord(
            keys = keys,
            trackerStates = states,
            skippedIndexes = skipped,
            substitutions = mapOf(keys[1] to listOf(substitution(1))),
            durations =
                mapOf(
                    keys[0] to listOf(duration(0, DurationVerdict.Ok)),
                    keys[2] to listOf(duration(2, DurationVerdict.Underheld)),
                ),
        )

    @Test
    fun aCleanWordHasNoProblemEvenWithAnOkMadd() {
        assertFalse(keys[0] in problems())
    }

    @Test
    fun aSaidWordWithAWrongSoundIsMispronounced() {
        assertEquals(WordProblem.Mispronounced, problems()[keys[1]])
    }

    @Test
    fun aSaidWordWithAShortMaddIsMispronounced() {
        assertEquals(WordProblem.Mispronounced, problems()[keys[2]])
    }

    @Test
    fun anotherWordIsADifferentWord() {
        assertEquals(WordProblem.DifferentWord, problems()[keys[3]])
    }

    @Test
    fun aSkippedWordIsSkipped() {
        assertEquals(WordProblem.Skipped, problems(skipped = setOf(3))[keys[3]])
    }

    @Test
    fun aWordStillBeingRecitedIsNotMarked() {
        assertFalse(keys[4] in problems())
    }

    @Test
    fun theMispronouncedWordOpensTheCardWithWhatWasHeard() {
        val reviews =
            flaggedReviews(
                ReviewRules.reviewsByAyah(
                    keys = keys,
                    trackerStates = states,
                    skippedIndexes = emptySet(),
                    durations = emptyMap(),
                    substitutions = mapOf(keys[1] to listOf(substitution(1))),
                ),
                problems(),
            )

        val card = reviews.single { it.key == keys[1] }
        assertTrue(card.findings.any { it is ReviewFinding.Pronunciation })
    }
}
