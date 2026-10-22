package app.awn.quran.tracker

/**
 * Decides whether a heard word is close enough to the expected one to count as said.
 *
 * Each difference between the heard and the expected phonemes has a cost: a missing sound costs
 * [MISSING_COST], a sound swapped for an unrelated one [UNRELATED_COST], a sound swapped for a near
 * one (see [nearSoundCosts]) less, and an extra sound [EXTRA_COST]. The cost of a word is the sum
 * divided by the number of phonemes of the word, so one wrong sound weighs less in a long word. A
 * word passes when its cost is within [allowedCost], a share that also grows a little with the
 * length of the word.
 *
 * The costs and the budget are Awn's own and were tuned on the tracker fixtures
 * (docs/decisions/tracker.md).
 */
object WordScore {

    /** Cost of an expected phoneme that was not heard. */
    const val MISSING_COST = 1.0

    /**
     * Cost of a heard phoneme that is not in the word. It is less than a missing one because the
     * model often adds a stray sound.
     */
    const val EXTRA_COST = 0.5

    private const val UNRELATED_COST = 1.0
    private const val EMPHATIC_PAIR_COST = 0.4
    private const val INTERDENTAL_PAIR_COST = 0.5
    private const val THROAT_PAIR_COST = 0.5
    private const val BACK_OF_MOUTH_PAIR_COST = 0.5
    private const val SHORT_VOWEL_PAIR_COST = 0.5

    // The share of the word that may differ is this much for a word of SHORT_WORD_LENGTH phonemes
    // or fewer, grows by this much for each phoneme beyond that, and stops growing at the maximum.
    private const val SHORT_WORD_LENGTH = 3
    private const val SHORT_WORD_ALLOWED_SHARE = 0.22
    private const val ALLOWED_SHARE_PER_EXTRA_PHONEME = 0.02
    private const val MAXIMUM_ALLOWED_SHARE = 0.34

    private const val FATHA = '\u064E'
    private const val DAMMA = '\u064F'
    private const val KASRA = '\u0650'

    private val nearSoundCosts: Map<Set<Char>, Double> = buildMap {
        fun addPairs(cost: Double, vararg pairs: Pair<Char, Char>) {
            for ((one, other) in pairs) put(setOf(one, other), cost)
        }
        // Emphatic and plain consonants made at the same place.
        addPairs(EMPHATIC_PAIR_COST, 'س' to 'ص', 'ت' to 'ط', 'د' to 'ض', 'ز' to 'ظ', 'ذ' to 'ظ')
        // Interdental and alveolar consonants.
        addPairs(INTERDENTAL_PAIR_COST, 'ذ' to 'ز', 'ث' to 'س', 'ث' to 'ت', 'ذ' to 'د')
        // Throat consonants.
        addPairs(THROAT_PAIR_COST, 'ح' to 'ه', 'ء' to 'ع', 'ء' to 'ه', 'ع' to 'ح')
        // Consonants made at the back of the mouth.
        addPairs(BACK_OF_MOUTH_PAIR_COST, 'ك' to 'ق', 'غ' to 'خ', 'خ' to 'ح')
        // The three short vowels, which the model often swaps.
        addPairs(SHORT_VOWEL_PAIR_COST, FATHA to DAMMA, FATHA to KASRA, DAMMA to KASRA)
    }

    /** The cost of hearing [heard] where [expected] was due: 0 when equal, 1 when unrelated. */
    fun substitutionCost(heard: String, expected: String): Double {
        val isSingleCharacterEach = heard.length == 1 && expected.length == 1
        return when {
            heard == expected -> 0.0
            isSingleCharacterEach -> nearSoundCosts[setOf(heard[0], expected[0])] ?: UNRELATED_COST
            else -> UNRELATED_COST
        }
    }

    /** The cost of a word: the sum of its differences divided by [referenceLength]. */
    fun cost(
        substitutionCosts: List<Double>,
        missing: Int,
        extra: Int,
        referenceLength: Int,
    ): Double {
        require(referenceLength > 0) { "A word has at least one phoneme, got $referenceLength" }
        val total = substitutionCosts.sum() + missing * MISSING_COST + extra * EXTRA_COST
        return total / referenceLength
    }

    /** The largest [cost] that still counts as said, for a word of [referenceLength] phonemes. */
    fun allowedCost(referenceLength: Int): Double {
        val phonemesBeyondShort = (referenceLength - SHORT_WORD_LENGTH).coerceAtLeast(0)
        val share = SHORT_WORD_ALLOWED_SHARE + ALLOWED_SHARE_PER_EXTRA_PHONEME * phonemesBeyondShort
        return share.coerceAtMost(MAXIMUM_ALLOWED_SHARE)
    }

    fun passes(cost: Double, referenceLength: Int): Boolean = cost <= allowedCost(referenceLength)
}
