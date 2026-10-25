package app.awn.quran.tracker

/**
 * The numbers the tracker judges with: one row per [Strictness]. The Normal row is the one tuned on
 * the fixtures; docs/decisions/tracker.md lists all three. "Phoneme" here is one character of a
 * model token, after runs of one repeated character are merged into one.
 *
 * @property shortWordAllowedCost the largest [WordScore.cost] of a said word of 1 to 3 phonemes
 * @property middleWordAllowedCost the same for a word of 4 to 7 phonemes
 * @property longWordAllowedCost the same for a word of 8 phonemes or more
 * @property minimumCoverage the share of a word of 4 phonemes or more that must be heard as itself
 *   or as a near sound
 * @property skipAllowedCost the cost a later word must stay within to prove that the words before
 *   it were left out or said wrong
 * @property insertionPhonemes unexplained heard phonemes before a said word that count as extra
 *   words
 * @property lostPhonemes unexplained heard phonemes before the whole loaded text is searched
 * @property lostQueryPhonemes how many of the latest heard phonemes that search looks for
 * @property lostMinimumScoreShare the share of a perfect score the place found must reach
 * @property lostMinimumMargin how many points the place found must beat any other place by
 * @property entryMatches matched phonemes needed before the first word is judged, so that stray
 *   sounds do not colour words
 */
internal data class AlignmentThresholds(
    val shortWordAllowedCost: Double,
    val middleWordAllowedCost: Double,
    val longWordAllowedCost: Double,
    val minimumCoverage: Double,
    val skipAllowedCost: Double,
    val insertionPhonemes: Int,
    val lostPhonemes: Int,
    val lostQueryPhonemes: Int,
    val lostMinimumScoreShare: Double,
    val lostMinimumMargin: Int,
    val entryMatches: Int,
) {
    /** The largest cost that still counts as said, for a word of [length] merged phonemes. */
    fun allowedCost(length: Int): Double =
        when {
            length <= SHORT_WORD_MAXIMUM_LENGTH -> shortWordAllowedCost
            length < LONG_WORD_MINIMUM_LENGTH -> middleWordAllowedCost
            else -> longWordAllowedCost
        }

    companion object {
        /** A word of this many merged phonemes or fewer is short. */
        const val SHORT_WORD_MAXIMUM_LENGTH = 3

        /** A word of this many merged phonemes or more is long. */
        const val LONG_WORD_MINIMUM_LENGTH = 8

        private val normal =
            AlignmentThresholds(
                shortWordAllowedCost = 0.20,
                middleWordAllowedCost = 0.26,
                longWordAllowedCost = 0.30,
                minimumCoverage = 0.70,
                skipAllowedCost = 0.20,
                insertionPhonemes = 6,
                lostPhonemes = 30,
                lostQueryPhonemes = 24,
                lostMinimumScoreShare = 0.8,
                lostMinimumMargin = 12,
                entryMatches = 6,
            )

        private val lenient =
            normal.copy(
                shortWordAllowedCost = 0.24,
                middleWordAllowedCost = 0.30,
                longWordAllowedCost = 0.34,
                minimumCoverage = 0.65,
                skipAllowedCost = 0.22,
                insertionPhonemes = 8,
                lostPhonemes = 36,
                entryMatches = 7,
            )

        private val strict =
            normal.copy(
                shortWordAllowedCost = 0.17,
                middleWordAllowedCost = 0.22,
                longWordAllowedCost = 0.26,
                minimumCoverage = 0.75,
                skipAllowedCost = 0.17,
                insertionPhonemes = 5,
                lostPhonemes = 26,
                entryMatches = 5,
            )

        fun forStrictness(strictness: Strictness): AlignmentThresholds =
            when (strictness) {
                Strictness.Lenient -> lenient
                Strictness.Normal -> normal
                Strictness.Strict -> strict
            }
    }
}
