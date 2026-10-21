package app.awn.quran.index

import kotlin.math.roundToInt

/**
 * The maths of the juz rail at the right edge of the list. The rail is a strip of height 1.0 whose
 * top is juz 1 and whose bottom is juz 30; a finger at any height names one juz, so a drag scrubs
 * through all 30 even though only some numbers are printed on the rail.
 */
object JuzRail {

    /** The numbers printed on the rail; the other juz are reached by touching between them. */
    val landmarks: List<Int> = listOf(1, 5, 10, 15, 20, 25, 30)

    /** The juz under a finger at [fraction] of the rail's height (0 is the top, 1 the bottom). */
    fun juzAtFraction(fraction: Float): Int {
        val steps = JuzStarts.JUZ_COUNT - 1
        return 1 + (fraction.coerceIn(0f, 1f) * steps).roundToInt()
    }

    /** Where [juz] sits on the rail, as a fraction of its height. */
    fun fractionOfJuz(juz: Int): Float {
        require(juz in 1..JuzStarts.JUZ_COUNT) { "There is no juz $juz." }
        return (juz - 1).toFloat() / (JuzStarts.JUZ_COUNT - 1)
    }

    /**
     * The juz the list is showing: the juz of the first visible row, or of the last row when the
     * list has scrolled past its end. Null while the list is empty.
     */
    fun currentJuz(rows: List<IndexRow>, firstVisibleRow: Int): Int? =
        rows.getOrNull(firstVisibleRow.coerceAtLeast(0))?.juz ?: rows.lastOrNull()?.juz
}
