package app.awn.quran.feature.hifz.method

import app.awn.quran.core.data.QuranTextRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads the pages of a print once and keeps them, because the table is the same for the whole life
 * of the process and building it asks the text database for every page.
 */
class PageTableLoader(private val quranText: QuranTextRepository) {

    private val mutex = Mutex()
    private val tables = mutableMapOf<PageLayoutFamily, PageTable>()

    suspend fun load(family: PageLayoutFamily): PageTable =
        mutex.withLock {
            tables.getOrPut(family) {
                val script = PageLayoutFamily.scriptFor(family)
                val pageCount = quranText.pageCount(script)
                PageTable.fromPages((1..pageCount).map { quranText.ayahsOnPage(it, script) })
            }
        }
}
