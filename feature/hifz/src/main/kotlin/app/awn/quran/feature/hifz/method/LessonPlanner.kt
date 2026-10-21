package app.awn.quran.feature.hifz.method

import app.awn.quran.core.data.AyahRef

/** One new lesson: the ayahs from [first] to [last] and the pages they are printed on. */
data class LessonPart(
    val first: AyahRef,
    val last: AyahRef,
    val firstPage: Int,
    val lastPage: Int,
    val ayahCount: Int,
)

/** Cuts the Quran into lessons, one after the other, following the pages of the user's print. */
object LessonPlanner {

    /**
     * The next new lesson, starting at [cursor] (the first ayah of the Quran when null). Pages the
     * user already knows are skipped. Returns null when nothing is left after the cursor.
     *
     * A page lesson is the ayahs that start on the page, a half-page lesson is half of them
     * (rounded up for the first half; a page with one ayah is never cut), an ayah lesson is the
     * given number of ayahs and may cross a page.
     */
    fun nextPart(
        table: PageTable,
        size: LessonSize,
        cursor: AyahRef?,
        statusOf: (page: Int) -> PageStatus,
    ): LessonPart? {
        val start = firstUnknownFrom(table, cursor, statusOf) ?: return null
        val ayahs =
            when (size) {
                LessonSize.OnePage -> ownAyahsFrom(table, start)
                LessonSize.HalfPage -> halfPageAyahsFrom(table, start)
                is LessonSize.Ayahs -> ayahsFrom(table, start, size.count)
            }
        return partOf(table, ayahs.first(), ayahs.last(), ayahs.size)
    }

    /** The part that spans [first] to [last], for a lesson that was already done. */
    fun partBetween(table: PageTable, first: AyahRef, last: AyahRef): LessonPart =
        partOf(table, first, last, table.allAyahs.count { it in first..last })

    private fun partOf(table: PageTable, first: AyahRef, last: AyahRef, count: Int) =
        LessonPart(
            first = first,
            last = last,
            firstPage = table.startPageOf(first),
            lastPage = table.endPageOf(last),
            ayahCount = count,
        )

    private fun firstUnknownFrom(
        table: PageTable,
        cursor: AyahRef?,
        statusOf: (Int) -> PageStatus,
    ): AyahRef? {
        var candidate: AyahRef? = cursor ?: table.allAyahs.first()
        while (candidate != null && statusOf(table.startPageOf(candidate)).isKnown) {
            val lastOnPage = table.ayahsStartingOn(table.startPageOf(candidate)).last()
            candidate = table.ayahAfter(lastOnPage)
        }
        return candidate
    }

    private fun ownAyahsFrom(table: PageTable, start: AyahRef): List<AyahRef> =
        table.ayahsStartingOn(table.startPageOf(start)).filter { it >= start }

    private fun halfPageAyahsFrom(table: PageTable, start: AyahRef): List<AyahRef> {
        val own = table.ayahsStartingOn(table.startPageOf(start))
        val firstHalfSize = (own.size + 1) / 2
        val startIndex = own.indexOf(start)
        return if (startIndex < firstHalfSize) own.subList(startIndex, firstHalfSize)
        else own.subList(startIndex, own.size)
    }

    private fun ayahsFrom(table: PageTable, start: AyahRef, count: Int): List<AyahRef> =
        table.allAyahs.filter { it >= start }.take(count)
}
