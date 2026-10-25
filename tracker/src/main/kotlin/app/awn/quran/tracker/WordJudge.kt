package app.awn.quran.tracker

internal enum class WordVerdict {
    /** Said: the word passes on cost, coverage and closure. */
    Pass,

    /**
     * Said so far except its last consonant: it passes on cost, but the user may still be saying it
     * or may have stopped short. It waits for the tail, the next word or a pause.
     */
    Held,
    Fail,
}

/**
 * Decides whether a fitted word was said. A word passes when its [WordScore.cost] is within the
 * allowed cost of its length class and enough of it was heard: a short word (1 to 3 phonemes) needs
 * every consonant heard, a longer one [AlignmentThresholds.minimumCoverage] of its phonemes heard
 * as themselves or as a near sound. "Consonant" is every phoneme except the vowels and the marks in
 * [NON_CONSONANTS].
 */
internal class WordJudge(private val tables: ReferenceTables) {

    var thresholds: AlignmentThresholds = AlignmentThresholds.forStrictness(Strictness.Normal)

    fun cost(outcome: WordOutcome): Double =
        WordScore.cost(
            substitutionCosts = outcome.substitutionCosts,
            missing = outcome.missing,
            extra = outcome.extra,
            referenceLength = outcome.length,
        )

    /** The verdict at the allowed cost of the word's length class, or at [allowedCost]. */
    fun verdict(
        outcome: WordOutcome,
        allowedCost: Double = thresholds.allowedCost(outcome.length),
    ): WordVerdict {
        val passes =
            if (isShort(outcome.word)) {
                cost(outcome) <= allowedCost && !anyConsonantMissing(outcome)
            } else {
                cost(outcome) <= allowedCost && coverage(outcome) >= thresholds.minimumCoverage
            }
        return when {
            !passes -> WordVerdict.Fail
            lastConsonantMissing(outcome) -> WordVerdict.Held
            else -> WordVerdict.Pass
        }
    }

    /** Passes outright at the stricter cost that proves a jump over the words before it. */
    fun passesAsSkipEvidence(outcome: WordOutcome): Boolean =
        verdict(
            outcome,
            minOf(thresholds.skipAllowedCost, thresholds.allowedCost(outcome.length)),
        ) == WordVerdict.Pass

    fun isShort(word: Int): Boolean =
        tables.wordLengths[word] <= AlignmentThresholds.SHORT_WORD_MAXIMUM_LENGTH

    /** Every phoneme heard as itself, with nothing extra inside the word. */
    fun isExact(outcome: WordOutcome): Boolean =
        outcome.matches == outcome.length && outcome.extra == 0

    private fun coverage(outcome: WordOutcome): Double =
        outcome.heardClosely.toDouble() / outcome.length

    private fun anyConsonantMissing(outcome: WordOutcome): Boolean =
        (0 until outcome.length).any {
            isConsonant(outcome.word, it) && outcome.alignedHeard[it] < 0
        }

    private fun lastConsonantMissing(outcome: WordOutcome): Boolean {
        val last = (outcome.length - 1 downTo 0).firstOrNull { isConsonant(outcome.word, it) }
        return last != null && outcome.alignedHeard[last] < 0
    }

    private fun isConsonant(word: Int, position: Int): Boolean {
        val symbol = tables.symbolOf(tables.expectedIds[tables.wordStartOffsets[word] + position])
        return symbol.none { it in NON_CONSONANTS }
    }

    private companion object {
        // Short vowels, long vowel letters, the pause-form tanween and the small marks.
        const val NON_CONSONANTS = "َُِاۥۦٲڇ۪ۜ۾ـ"
    }
}
