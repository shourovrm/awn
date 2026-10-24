package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey

/**
 * Finds the Mushaf page each word is printed on in a glyph [script]. The word table of an ayah does
 * not say, so the page layouts tell: a layout lists exactly the words printed on its page.
 *
 * An ayah that runs over a page break has words on two (rarely more) pages. For such an ayah the
 * layouts are read from the page of its first word onward until every word is placed. Layouts are
 * kept for the life of the index, so the many ayahs of one page read each layout once. One index
 * serves one page load, because it is not thread safe.
 */
class GlyphPageIndex(
    private val quranText: QuranTextRepository,
    private val script: ScriptId,
) {
    private val wordsByPage = mutableMapOf<Int, Set<WordKey>>()

    /** The page of each of [words], all belonging to [ayah]; a word no layout holds is absent. */
    suspend fun pagesOf(ayah: AyahRef, words: List<Word>): Map<WordKey, Int> {
        val unplaced = words.mapTo(mutableSetOf()) { it.key }
        val placed = mutableMapOf<WordKey, Int>()
        var page = quranText.pageOf(ayah, script)
        while (unplaced.isNotEmpty() && page <= quranText.pageCount(script)) {
            val printedHere = wordsPrintedOn(page)
            val found = unplaced.filter { it in printedHere }
            found.forEach { placed[it] = page }
            unplaced.removeAll(found.toSet())
            // An ayah's words are on consecutive pages, so a page without any ends the search.
            if (found.isEmpty() && placed.isNotEmpty()) break
            page++
        }
        return placed
    }

    private suspend fun wordsPrintedOn(page: Int): Set<WordKey> =
        wordsByPage.getOrPut(page) {
            quranText
                .pageLayout(page, script)
                .lines
                .flatMap { line -> line.items }
                .filterIsInstance<LineItem.WordItem>()
                .mapTo(mutableSetOf()) { item -> item.word.key }
        }
}
