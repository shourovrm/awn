package app.awn.quran.feature.read.page

/** The 30 parts of the Quran by the first page of each in the 604-page Madani Mushaf. */
object JuzTable {

    private val startPages =
        listOf(
            1,
            22,
            42,
            62,
            82,
            102,
            121,
            142,
            162,
            182,
            201,
            222,
            242,
            262,
            282,
            302,
            322,
            342,
            362,
            382,
            402,
            422,
            442,
            462,
            482,
            502,
            522,
            542,
            562,
            582,
        )

    const val JUZ_COUNT = 30

    /** Returns the part (1 to 30) that [page] belongs to. */
    fun juzOfPage(page: Int): Int = startPages.indexOfLast { it <= page }.coerceAtLeast(0) + 1

    /** Returns the first page of [juz] (1 to 30). */
    fun startPageOf(juz: Int): Int = startPages[juz - 1]
}
