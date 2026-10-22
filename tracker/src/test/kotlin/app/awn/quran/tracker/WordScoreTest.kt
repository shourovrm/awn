package app.awn.quran.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How much a heard word may differ from the text and still count as said. The old rule (80 % of the
 * phonemes matched and at most one substitution) treated every wrong sound alike and gave long and
 * short words the same budget, so many words ended "unclear". The new score weighs each difference:
 * a sound swapped for a near one (س for ص, a short vowel for another) costs less than a swap for an
 * unrelated sound, a missing sound costs like an unrelated one, and longer words get a larger
 * share. The numbers are Awn's own, tuned on the tracker fixtures; the tests fix only the
 * relations.
 */
class WordScoreTest {

    @Test
    fun theSameSoundCostsNothing() {
        assertEquals(0.0, WordScore.substitutionCost(heard = "س", expected = "س"), 0.0)
    }

    @Test
    fun nearSoundsCostLessThanUnrelatedOnes() {
        val nearPairs =
            listOf(
                "س" to "ص",
                "ت" to "ط",
                "د" to "ض",
                "ذ" to "ز",
                "ذ" to "ظ",
                "ث" to "س",
                "ح" to "ه",
                "ء" to "ع",
                "ك" to "ق",
                "َ" to "ُ",
                "ِ" to "َ",
            )
        val unrelated = WordScore.substitutionCost(heard = "ب", expected = "م")
        assertEquals(1.0, unrelated, 0.0)
        nearPairs.forEach { (one, other) ->
            val cost = WordScore.substitutionCost(heard = one, expected = other)
            assertTrue("$one for $other costs $cost", cost > 0.0 && cost < unrelated)
            assertEquals(
                "near pairs cost the same both ways",
                cost,
                WordScore.substitutionCost(heard = other, expected = one),
                0.0,
            )
        }
    }

    @Test
    fun aMissingSoundCostsLikeAnUnrelatedOne() {
        assertEquals(
            WordScore.cost(
                substitutionCosts = listOf(1.0),
                missing = 0,
                extra = 0,
                referenceLength = 7,
            ),
            WordScore.cost(
                substitutionCosts = emptyList(),
                missing = 1,
                extra = 0,
                referenceLength = 7,
            ),
            1e-9,
        )
    }

    @Test
    fun longerWordsGetALargerShare() {
        assertTrue(WordScore.allowedCost(referenceLength = 2) <= WordScore.allowedCost(4))
        assertTrue(WordScore.allowedCost(4) <= WordScore.allowedCost(8))
        assertTrue(WordScore.allowedCost(2) < WordScore.allowedCost(8))
    }

    @Test
    fun oneUnrelatedSoundFailsAShortWordButPassesALongOne() {
        val shortWord = WordScore.cost(listOf(1.0), missing = 0, extra = 0, referenceLength = 3)
        val longWord = WordScore.cost(listOf(1.0), missing = 0, extra = 0, referenceLength = 7)

        assertFalse(WordScore.passes(shortWord, referenceLength = 3))
        assertTrue(WordScore.passes(longWord, referenceLength = 7))
    }

    @Test
    fun oneNearSoundPassesEvenAShortWord() {
        val near = WordScore.substitutionCost(heard = "س", expected = "ص")
        val cost = WordScore.cost(listOf(near), missing = 0, extra = 0, referenceLength = 3)

        assertTrue(WordScore.passes(cost, referenceLength = 3))
    }

    @Test
    fun twoUnrelatedSoundsFailAMiddleWord() {
        val cost = WordScore.cost(listOf(1.0, 1.0), missing = 0, extra = 0, referenceLength = 5)

        assertFalse(WordScore.passes(cost, referenceLength = 5))
    }
}
