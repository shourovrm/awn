package app.awn.quran.feature.learn.words

object LevelComplete {

    /**
     * The level that a session finished and that has not been celebrated: every word of it is met
     * in [metAfter] but not in [metBefore]. Null when no level was finished by this session, which
     * includes a level that was already whole before it and one in [celebrated]. A word list that
     * ends inside a level ([formCount]) finishes that level at its last form.
     */
    fun toCelebrate(
        metBefore: Set<Int>,
        metAfter: Set<Int>,
        formCount: Int,
        celebrated: Set<Int>,
    ): WordLevel? =
        WordLevels.all.firstOrNull { level ->
            level.number !in celebrated &&
                isWhole(level, metAfter, formCount) &&
                !isWhole(level, metBefore, formCount)
        }

    private fun isWhole(level: WordLevel, metRanks: Set<Int>, formCount: Int): Boolean {
        if (level.firstRank > formCount) return false
        return (level.firstRank..minOf(level.lastRank, formCount)).all { it in metRanks }
    }
}
