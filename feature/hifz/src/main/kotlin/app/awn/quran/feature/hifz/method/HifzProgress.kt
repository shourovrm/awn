package app.awn.quran.feature.hifz.method

import app.awn.quran.core.data.AyahRef

/** One row of the progress map: the pages of a juz and how well each is known. */
data class JuzRow(val juz: Int, val firstPage: Int, val statuses: List<PageStatus>)

/** The progress map and the number the tab shows above it. */
data class ProgressMap(val rows: List<JuzRow>, val memorisedPages: Int, val pageCount: Int)

object HifzProgress {

    fun map(table: PageTable, records: Map<Int, PageRecord>): ProgressMap {
        // Only juz that have a page in this print get a row; a full print has all 30.
        val juzList = (1..table.pageCount).map(table::juzOf).distinct()
        val rows =
            juzList.map { juz ->
                val pages = table.pagesOfJuz(juz)
                JuzRow(
                    juz = juz,
                    firstPage = pages.first,
                    statuses = pages.map { records[it]?.status ?: PageStatus.NotStarted },
                )
            }
        val memorised = records.values.count { it.status.isKnown }
        return ProgressMap(rows, memorised, table.pageCount)
    }

    /**
     * Moves the records of one pagination to another. A page of the new print takes the record of
     * the old page that holds its first ayah, because the pages of the two prints do not break at
     * the same ayahs and only the first ayah tells which old page a new page continues.
     */
    fun remap(
        oldTable: PageTable,
        newTable: PageTable,
        records: Map<Int, PageRecord>,
    ): Map<Int, PageRecord> =
        buildMap {
            for (page in 1..newTable.pageCount) {
                val firstAyah = newTable.ayahsOn(page).firstOrNull() ?: continue
                val oldRecord = records[oldTable.startPageOf(firstAyah)] ?: continue
                put(page, oldRecord.copy(page = page))
            }
        }

    /** A suggested grade for a finished part from the words the user got wrong while reciting. */
    fun suggestedGrade(mistakenWords: Int, ayahCount: Int): PartGrade =
        when {
            mistakenWords == 0 -> PartGrade.Good
            mistakenWords <= ayahCount -> PartGrade.Hard
            else -> PartGrade.Forgot
        }
}

/** What went wrong in an ayah. */
enum class MistakeType {
    Skipped,
    OtherWord,
}

data class MistakeRecord(val ayah: AyahRef, val type: MistakeType, val atMillis: Long)

/** An ayah with repeated mistakes of one type; [count] is how many times in the window. */
data class WeakSpot(val ayah: AyahRef, val type: MistakeType, val count: Int)

object WeakSpots {
    const val MINIMUM_COUNT = 2
    const val LIST_SIZE = 5

    /** Ayahs with the same mistake at least [MINIMUM_COUNT] times, worst first, then in reading order. */
    fun of(mistakes: List<MistakeRecord>): List<WeakSpot> =
        mistakes
            .groupingBy { it.ayah to it.type }
            .eachCount()
            .filterValues { it >= MINIMUM_COUNT }
            .map { (key, count) -> WeakSpot(key.first, key.second, count) }
            .sortedWith(compareByDescending<WeakSpot> { it.count }.thenBy { it.ayah })
            .take(LIST_SIZE)
}
