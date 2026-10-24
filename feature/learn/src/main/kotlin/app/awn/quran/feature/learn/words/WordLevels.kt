package app.awn.quran.feature.learn.words

/** One of the seven stretches of the word course; its sets are [sets] and its words [ranks]. */
data class WordLevel(val number: Int, val firstRank: Int, val lastRank: Int) {
    val ranks: IntRange
        get() = firstRank..lastRank

    /** How many words the level holds, for its header. */
    val wordCount: Int
        get() = lastRank - firstRank + 1

    /** The numbers of the sets of ten words that make up the level. */
    val sets: IntRange
        get() = LearnSets.numberOf(firstRank)..LearnSets.numberOf(lastRank)
}

/**
 * The word course in seven levels, by word frequency rank: 1 to 100, 101 to 200, 201 to 300, 301 to
 * 500, 501 to 1,000, 1,001 to 2,000 and 2,001 to 5,000. Every level starts and ends at a multiple
 * of ten, so a set never straddles two levels.
 */
object WordLevels {

    private val levelEnds = listOf(100, 200, 300, 500, 1_000, 2_000, 5_000)

    val all: List<WordLevel> = levelEnds.mapIndexed { index, lastRank ->
        val firstRank = if (index == 0) 1 else levelEnds[index - 1] + 1
        WordLevel(number = index + 1, firstRank = firstRank, lastRank = lastRank)
    }

    /** The level of the word with [rank]; null for a rank above the last level. */
    fun levelOfRank(rank: Int): WordLevel? = all.firstOrNull { rank <= it.lastRank }

    /** The level of set [number]; null for a set above the last level. */
    fun levelOfSet(number: Int): WordLevel? = levelOfRank(LearnSets.ranksOf(number).first)
}
