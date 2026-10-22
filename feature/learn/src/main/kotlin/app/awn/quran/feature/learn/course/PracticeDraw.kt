package app.awn.quran.feature.learn.course

import kotlin.random.Random

/** Chooses the practice items of one visit to a unit. */
object PracticeDraw {

    /** How many items a visit asks; a smaller pool gives all of its items. */
    const val ITEMS_PER_VISIT = 10

    /**
     * Returns [count] items of [pool] in random order, or all of them when the pool is smaller. The
     * same [seed] gives the same items in the same order, so a screen that is drawn again during
     * one visit keeps its set.
     */
    fun <T> draw(pool: List<T>, count: Int, seed: Long): List<T> =
        pool.shuffled(Random(seed)).take(count)
}
