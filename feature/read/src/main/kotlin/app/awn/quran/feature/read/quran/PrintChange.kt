package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Quarters
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.read.page.indoPakScripts

private const val FIRST_PAGE = 1

/**
 * The page that shows the text the reader is on after the print changes from [from] to [to]. The
 * IndoPak prints have their own page numbers (610 and 548 pages), so the number cannot stay; the
 * Madani prints share theirs. When the first ayah of the page cannot be found in one of the prints
 * the page number stays, kept inside the new print.
 */
internal suspend fun pageAfterPrintChange(
    quranText: QuranTextRepository,
    page: Int,
    from: ScriptId,
    to: ScriptId,
): Int {
    val newPageCount = quranText.pageCount(to)
    if (from !in indoPakScripts && to !in indoPakScripts)
        return page.coerceIn(FIRST_PAGE, newPageCount)
    val firstAyah = firstAyahOnPage(quranText, page, from)
    val newPage = firstAyah?.let { pageOfOrNull(quranText, it, to) }
    return (newPage ?: page).coerceIn(FIRST_PAGE, newPageCount)
}

/** The juz of the first ayah on [page] of [script], or null while that cannot be read. */
internal suspend fun juzOfFirstAyahOnPage(
    quranText: QuranTextRepository,
    page: Int,
    script: ScriptId,
): Int? = firstAyahOnPage(quranText, page, script)?.let { Quarters.containing(it).juz }

// A print whose pack is missing or whose page is out of range has no first ayah; the callers fall
// back to the page number they already have.
private suspend fun firstAyahOnPage(
    quranText: QuranTextRepository,
    page: Int,
    script: ScriptId,
): AyahRef? =
    try {
        quranText.ayahsOnPage(page, script).firstOrNull()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }

private suspend fun pageOfOrNull(
    quranText: QuranTextRepository,
    ayah: AyahRef,
    script: ScriptId,
): Int? =
    try {
        quranText.pageOf(ayah, script)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }
