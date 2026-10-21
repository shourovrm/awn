package app.awn.quran.feature.hifz

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.HifzPlan
import app.awn.quran.feature.hifz.method.MethodSettings
import app.awn.quran.feature.hifz.method.PageLayoutFamily
import app.awn.quran.feature.hifz.method.PageRecord
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PageTable
import java.time.LocalDate

/** Shorthand for an ayah of surah 2, the only surah the test tables use. */
fun ayah(number: Int) = AyahRef(2, number)

/**
 * A small print of 12 pages in two juz (pages 1 to 6 and 7 to 12). Pages 1 to 3 hold four ayahs
 * each; ayah 12 starts on page 3 and runs onto page 4, which then holds ayahs 13 to 16. Pages 5 to
 * 12 hold four ayahs each (17 to 48).
 */
fun smallTable(): PageTable {
    val pages = buildList {
        add((1..4).map(::ayah))
        add((5..8).map(::ayah))
        add((9..12).map(::ayah))
        add((12..16).map(::ayah))
        for (page in 5..12) {
            val first = 17 + (page - 5) * 4
            add((first until first + 4).map(::ayah))
        }
    }
    return PageTable(pages, List(12) { if (it < 6) 1 else 2 })
}

val testDay: LocalDate = LocalDate.of(2026, 3, 10)

fun planOf(
    method: HifzMethodId = HifzMethodId.SabaqSabqiManzil,
    settings: MethodSettings = MethodSettings(),
    cursor: AyahRef? = null,
) = HifzPlan(method, settings, cursor, PageLayoutFamily.Madani1421, testDay.minusDays(30))

fun known(page: Int, learnedDaysAgo: Long? = null, revisedDaysAgo: Long? = null) =
    PageRecord(
        page = page,
        status = PageStatus.Memorised,
        learnedOn = learnedDaysAgo?.let { testDay.minusDays(it) },
        lastRevisedOn = revisedDaysAgo?.let { testDay.minusDays(it) },
    )
