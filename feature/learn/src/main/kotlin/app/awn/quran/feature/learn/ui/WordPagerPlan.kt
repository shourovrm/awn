package app.awn.quran.feature.learn.ui

import app.awn.quran.feature.learn.words.LearnSets

/** The words a word page swipes over, in order, and the one the page was opened on. */
internal data class WordPagerPlan(val ranks: List<Int>, val startIndex: Int)

/**
 * A word opened from a set swipes over the words of that set; opened from anywhere else, or when
 * [rank] is not in that set, it stands alone.
 */
internal fun wordPagerPlan(rank: Int, from: LearnRoute?, formCount: Int): WordPagerPlan {
    val setRanks =
        (from as? LearnRoute.SetPage)?.let { set ->
            LearnSets.ranksOf(set.number).filter { it <= formCount }
        }
    if (setRanks == null || rank !in setRanks) return WordPagerPlan(listOf(rank), 0)
    return WordPagerPlan(setRanks, setRanks.indexOf(rank))
}
