package app.awn.quran.tracker

/**
 * Where the latest heard phonemes were found: the [word], how many of its phonemes come before the
 * match, and the heard index where the match starts.
 */
internal data class LostPlace(val word: Int, val phonemesIntoWord: Int, val heardStart: Int)

/**
 * Searches all loaded words for the latest heard phonemes after every nearer candidate has failed
 * for a long stretch. The place is accepted only when it is clearly right and clearly the only one:
 * it scores [AlignmentThresholds.lostMinimumScoreShare] of a perfect match, and the best other
 * place anywhere scores at least [AlignmentThresholds.lostMinimumMargin] points less. A common
 * phrase that appears twice is a tie, and a tie means staying put.
 */
internal object LostSearch {

    // The best place is masked this many phonemes wider on each side before looking for the
    // second best, so a shifted copy of the same match does not count as another place.
    private const val MASK_MARGIN_PHONEMES = 4
    private const val MASKED = -1

    fun find(
        query: IntArray,
        tables: ReferenceTables,
        thresholds: AlignmentThresholds,
    ): LostPlace? {
        val best = PhonemeAligner.findBestPlace(query, tables.expectedIds) ?: return null
        val perfect = AlignmentScoring.MATCH * query.size
        val isGoodEnough = best.score >= thresholds.lostMinimumScoreShare * perfect
        val masked = tables.expectedIds.copyOf()
        val maskStart = (best.expectedStart - MASK_MARGIN_PHONEMES).coerceAtLeast(0)
        val maskEnd = (best.expectedEnd + MASK_MARGIN_PHONEMES).coerceAtMost(masked.size)
        for (offset in maskStart until maskEnd) masked[offset] = MASKED
        val secondScore = PhonemeAligner.findBestPlace(query, masked)?.score ?: 0
        val isUnique = best.score - secondScore >= thresholds.lostMinimumMargin
        return if (isGoodEnough && isUnique) {
            val word = tables.wordAtOffset(best.expectedStart)
            LostPlace(word, best.expectedStart - tables.wordStartOffsets[word], best.heardStart)
        } else {
            null
        }
    }
}
