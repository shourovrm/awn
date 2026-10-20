package app.awn.quran.core.data

import app.awn.quran.core.data.quran.QuarterData

/**
 * One quarter (rub' al-hizb) of the Quran. [number] runs 1..240; [firstAyah] is where it starts.
 * [pageV1] and [pageV2] are the pages of that ayah in the 1405 and 1421 prints.
 */
data class Quarter(
    val number: Int,
    val firstAyah: AyahRef,
    val pageV1: Int,
    val pageV2: Int,
) {
    /** The juz (1..30) that holds this quarter; every juz has 8 quarters. */
    val juz: Int
        get() = (number - 1) / QUARTERS_PER_JUZ + 1

    /** The hizb (1..60) that holds this quarter; every hizb has 4 quarters. */
    val hizb: Int
        get() = (number - 1) / QUARTERS_PER_HIZB + 1

    /** 0 when the quarter starts its hizb, then 1, 2, 3 for a quarter, a half, three quarters. */
    val quartersIntoHizb: Int
        get() = (number - 1) % QUARTERS_PER_HIZB

    /** The page of this quarter's first ayah in [script]; the 1405 page only for [ScriptId.V1]. */
    fun pageIn(script: ScriptId): Int = if (script == ScriptId.V1) pageV1 else pageV2

    companion object {
        const val QUARTERS_PER_HIZB = 4
        const val QUARTERS_PER_JUZ = 8
    }
}

/**
 * The 240 quarters of the Quran with the first ayah of each (source and checksum in `QuarterData`,
 * built by `tools/build_quarters.py`).
 */
object Quarters {

    const val COUNT = 240
    private const val JUZ_COUNT = 30

    /** All quarters in order, number 1 first. */
    val all: List<Quarter> by lazy { QuarterData.rows.mapIndexed(::parseRow) }

    /** Returns quarter [number] (1..240). */
    fun quarter(number: Int): Quarter {
        require(number in 1..COUNT) { "Quarter must be 1..$COUNT but was $number." }
        return all[number - 1]
    }

    /** The eight quarters of [juz] (1..30), in order. */
    fun ofJuz(juz: Int): List<Quarter> {
        require(juz in 1..JUZ_COUNT) { "Juz must be 1..$JUZ_COUNT but was $juz." }
        val firstIndex = (juz - 1) * Quarter.QUARTERS_PER_JUZ
        return all.subList(firstIndex, firstIndex + Quarter.QUARTERS_PER_JUZ)
    }

    /** The quarters whose first ayah is printed on [page] of [script]; usually none or one. */
    fun startingOnPage(page: Int, script: ScriptId): List<Quarter> = all.filter {
        it.pageIn(script) == page
    }

    /** The quarter that contains [ayah]. */
    fun containing(ayah: AyahRef): Quarter = all.last { it.firstAyah <= ayah }

    private fun parseRow(index: Int, row: String): Quarter {
        val parts = row.split(':').map { it.toInt() }
        return Quarter(index + 1, AyahRef(parts[0], parts[1]), pageV1 = parts[2], pageV2 = parts[3])
    }
}
