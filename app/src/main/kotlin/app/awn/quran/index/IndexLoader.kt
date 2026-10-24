package app.awn.quran.index

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.SavedAyah
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey

/**
 * The print used for page numbers when the user's print has no page table (IndoPak not installed).
 */
val FALLBACK_INDEX_SCRIPT = ScriptId.V2

/**
 * The parts of the index that depend on the print but not on what the user has read. [script] is
 * the print whose page numbers the rows carry.
 */
class IndexContent(
    val script: ScriptId,
    val chapters: List<Chapter>,
    val surahRows: List<IndexRow>,
    val juzRows: List<IndexRow>,
    /** How many pages [script] has, for the Go to page dialog. */
    val pageCount: Int,
    /** The first page of each juz, keyed by juz number, in [script]. */
    val juzStartPages: Map<Int, Int>,
)

/** The card that opens the Quran screen where the user stopped. */
data class ContinueReading(
    val page: Int,
    val chapter: Chapter,
    val juz: Int,
    /** The first word of the first line of text of [page]; null when the page has no words. */
    val firstWord: WordKey?,
    /** True when nothing was read yet, so the card starts at page 1. */
    val isStart: Boolean,
)

/** The juz (1 to 30) that holds [page], given the first page of every juz. */
fun juzOfPage(juzStartPages: Map<Int, Int>, page: Int): Int =
    (1..JuzStarts.JUZ_COUNT).last { juzStartPages.getValue(it) <= page }

/** Reads the surah, juz and page lists from the Quran text in the user's print. */
class IndexLoader(private val quranText: QuranTextRepository) {

    private val surahOfPage = mutableMapOf<Pair<ScriptId, Int>, Int>()

    /**
     * The print whose pages the index can number: [wanted] when the Quran text has a page table for
     * it, else [FALLBACK_INDEX_SCRIPT]. Scripts without a page table throw
     * [UnsupportedOperationException] from `pageOf`.
     */
    suspend fun printFor(wanted: ScriptId): ScriptId =
        try {
            quranText.pageOf(AyahRef(1, 1), wanted)
            wanted
        } catch (expectedFailure: UnsupportedOperationException) {
            FALLBACK_INDEX_SCRIPT
        }

    suspend fun loadContent(wanted: ScriptId): IndexContent {
        val script = printFor(wanted)
        val chapters = quranText.chapters()
        val surahPages = chapters.associate {
            it.number to quranText.pageOf(AyahRef(it.number, 1), script)
        }
        val juzPages =
            JuzStarts.ayahs
                .mapIndexed { index, ayah -> index + 1 to quranText.pageOf(ayah, script) }
                .toMap()
        return IndexContent(
            script = script,
            chapters = chapters,
            surahRows = buildSurahRows(chapters, surahPages, juzPages),
            juzRows = buildJuzRows(chapters, juzPages),
            pageCount = quranText.pageCount(script),
            juzStartPages = juzPages,
        )
    }

    /** The surah that begins [page]'s text, used to name a bookmarked or resumed page. */
    suspend fun surahOnPage(page: Int, script: ScriptId): Int =
        surahOfPage.getOrPut(script to page) {
            quranText.ayahsOnPage(page, script).firstOrNull()?.surah ?: 1
        }

    /** The card for [lastPage], or for page 1 when nothing was read yet. */
    suspend fun continueReading(lastPage: Int?, content: IndexContent): ContinueReading {
        val page = lastPage ?: FIRST_PAGE
        val surah = surahOnPage(page, content.script)
        return ContinueReading(
            page = page,
            chapter = content.chapters.first { it.number == surah },
            juz = juzOfPage(content.juzStartPages, page),
            firstWord = firstWordOf(page, content.script),
            isStart = lastPage == null,
        )
    }

    private suspend fun firstWordOf(page: Int, script: ScriptId): WordKey? {
        val layout = quranText.pageLayout(page, script)
        // A line that holds only a surah banner or the Basmalah has no words; the first line of
        // text is the first one that does.
        return layout.lines
            .asSequence()
            .flatMap { line -> line.items.filterIsInstance<LineItem.WordItem>() }
            .firstOrNull()
            ?.word
            ?.key
    }

    suspend fun toAyahMark(saved: SavedAyah, script: ScriptId): BookmarkEntry.AyahMark =
        BookmarkEntry.AyahMark(
            ayah = saved.ayah,
            page = quranText.pageOf(saved.ayah, script),
            createdAt = saved.createdAt,
        )

    private companion object {
        const val FIRST_PAGE = 1
    }
}
