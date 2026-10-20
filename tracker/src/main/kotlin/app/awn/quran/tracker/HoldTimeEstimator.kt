package app.awn.quran.tracker

/**
 * Estimates how long the reciter holds one haraka (one count of a vowel), from the model's token
 * onsets. A short-vowel token (a consonant with a fatha, damma or kasra) lasts the time to the next
 * token's onset; the median of the latest such gaps, scaled by [HARAKA_PER_SHORT_VOWEL_GAP], is
 * taken as the length of one haraka. The factor 0.35 was fitted on the Husary fixtures: the token
 * of a short vowel holds its consonant as well, and the model's natural madd (two counts) lasts
 * about 0.35 of two such gaps.
 *
 * Onsets come in steps of 0.04 s (one frame after subsampling), so single gaps are coarse; the
 * median over many tokens is what makes the estimate usable.
 */
internal class HoldTimeEstimator {

    private val recentGaps = ArrayDeque<Float>()
    private var previousToken: PhonemeEvent? = null

    fun clear() {
        recentGaps.clear()
        previousToken = null
    }

    /** Takes the next token in the order the model emitted them. */
    fun observe(token: PhonemeEvent) {
        val previous = previousToken
        previousToken = token
        if (previous == null || !isShortVowelToken(previous.symbol)) return
        val gap = token.onsetSeconds - previous.onsetSeconds
        // A longer gap is a pause between words or ayahs, not a vowel.
        if (gap <= 0f || gap > MAXIMUM_GAP_SECONDS) return
        recentGaps.addLast(gap)
        if (recentGaps.size > RECENT_GAP_COUNT) recentGaps.removeFirst()
    }

    /** Returns the estimated seconds of one haraka, or null until enough tokens were heard. */
    fun harakaSeconds(): Float? {
        if (recentGaps.size < MINIMUM_GAP_COUNT) return null
        val sorted = recentGaps.sorted()
        return sorted[sorted.size / 2] * HARAKA_PER_SHORT_VOWEL_GAP
    }

    private fun isShortVowelToken(symbol: String): Boolean =
        symbol.any { it in SHORT_VOWEL_MARKS } && symbol.none { it in LONG_VOWEL_LETTERS }

    private companion object {
        const val SHORT_VOWEL_MARKS = "َُِ"
        const val LONG_VOWEL_LETTERS = "اۥۦ"
        const val MAXIMUM_GAP_SECONDS = 1.0f
        const val RECENT_GAP_COUNT = 40
        const val MINIMUM_GAP_COUNT = 6
        const val HARAKA_PER_SHORT_VOWEL_GAP = 0.35f
    }
}
