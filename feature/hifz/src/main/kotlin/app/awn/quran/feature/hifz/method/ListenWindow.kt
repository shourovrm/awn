package app.awn.quran.feature.hifz.method

import app.awn.quran.core.data.AyahRef

/**
 * The run of ayahs that the Hifz bar's Play listens to, from [first] to [last] in reading order.
 *
 * Play used to loop only the lesson's own ayahs, so the user could not listen on to the ayahs after
 * them or back to the ones before. The window starts as the lesson, and Previous and Next ayahs
 * move it by its own length, so the user can keep listening across pages and surahs.
 */
data class ListenWindow(val first: AyahRef, val last: AyahRef) {

    /**
     * The window of the same length that starts right after [last], cut short at the end of the
     * Quran. Null when no ayah follows [last].
     */
    fun next(table: PageTable): ListenWindow? {
        val ayahs = table.allAyahs
        val nextFirstIndex = indexOfAyah(table, last) + 1
        if (nextFirstIndex >= ayahs.size) {
            return null
        }
        val nextLastIndex = minOf(nextFirstIndex + lengthIn(table) - 1, ayahs.lastIndex)
        return ListenWindow(ayahs[nextFirstIndex], ayahs[nextLastIndex])
    }

    /**
     * The window of the same length that ends right before [first], cut short at the start of the
     * Quran. Null when no ayah comes before [first].
     */
    fun previous(table: PageTable): ListenWindow? {
        val ayahs = table.allAyahs
        val previousLastIndex = indexOfAyah(table, first) - 1
        if (previousLastIndex < 0) {
            return null
        }
        val previousFirstIndex = maxOf(previousLastIndex - lengthIn(table) + 1, 0)
        return ListenWindow(ayahs[previousFirstIndex], ayahs[previousLastIndex])
    }

    /** The number of ayahs from [first] to [last], both included. */
    private fun lengthIn(table: PageTable): Int =
        indexOfAyah(table, last) - indexOfAyah(table, first) + 1

    private fun indexOfAyah(table: PageTable, ayah: AyahRef): Int {
        val index = table.allAyahs.binarySearch(ayah)
        require(index >= 0) {
            "Ayah $ayah is not in this print, so a listen window cannot be placed on it."
        }
        return index
    }
}
