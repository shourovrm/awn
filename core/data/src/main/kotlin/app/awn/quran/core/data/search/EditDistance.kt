package app.awn.quran.core.data.search

import kotlin.math.abs

/**
 * The Levenshtein distance between [first] and [second] (the fewest single-letter insertions,
 * deletions and substitutions that turn one into the other), or `limit + 1` when it is more than
 * [limit]. Stopping at the limit keeps a search over half a million words cheap: two words whose
 * lengths differ by more than [limit] are rejected without looking at a letter.
 */
fun editDistance(first: CharSequence, second: CharSequence, limit: Int): Int {
    if (abs(first.length - second.length) > limit) return limit + 1
    var previousRow = IntArray(second.length + 1) { it }
    var currentRow = IntArray(second.length + 1)
    for (firstIndex in 1..first.length) {
        currentRow[0] = firstIndex
        var smallestInRow = currentRow[0]
        for (secondIndex in 1..second.length) {
            val substitutionCost = if (first[firstIndex - 1] == second[secondIndex - 1]) 0 else 1
            currentRow[secondIndex] =
                minOf(
                    previousRow[secondIndex] + 1,
                    currentRow[secondIndex - 1] + 1,
                    previousRow[secondIndex - 1] + substitutionCost,
                )
            smallestInRow = minOf(smallestInRow, currentRow[secondIndex])
        }
        // Every later row is at least as large as the smallest value in this one.
        if (smallestInRow > limit) return limit + 1
        val swap = previousRow
        previousRow = currentRow
        currentRow = swap
    }
    return minOf(previousRow[second.length], limit + 1)
}
