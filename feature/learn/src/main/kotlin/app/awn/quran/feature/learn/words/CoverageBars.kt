package app.awn.quran.feature.learn.words

/** The share of all Quran words that the [wordCount] most frequent forms cover, 0.0 to 1.0. */
data class CoverageMilestone(val wordCount: Int, val share: Double)

object CoverageBars {

    /**
     * The share covered at the end of each word level, counted from [frequency] so it follows the
     * word list the app ships.
     */
    fun milestones(frequency: WordFrequency): List<CoverageMilestone> =
        WordLevels.all.map { level ->
            CoverageMilestone(
                wordCount = level.lastRank,
                share = WordProgress.coverageOfTopForms(level.lastRank, frequency),
            )
        }
}
