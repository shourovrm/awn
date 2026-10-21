package app.awn.quran.index

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter

/** Where the first ayah of each of the 30 juz is; these are fixed by the Quran's division. */
object JuzStarts {

    val ayahs: List<AyahRef> =
        listOf(
            AyahRef(1, 1),
            AyahRef(2, 142),
            AyahRef(2, 253),
            AyahRef(3, 93),
            AyahRef(4, 24),
            AyahRef(4, 148),
            AyahRef(5, 82),
            AyahRef(6, 111),
            AyahRef(7, 88),
            AyahRef(8, 41),
            AyahRef(9, 93),
            AyahRef(11, 6),
            AyahRef(12, 53),
            AyahRef(15, 1),
            AyahRef(17, 1),
            AyahRef(18, 75),
            AyahRef(21, 1),
            AyahRef(23, 1),
            AyahRef(25, 21),
            AyahRef(27, 56),
            AyahRef(29, 46),
            AyahRef(33, 31),
            AyahRef(36, 28),
            AyahRef(39, 32),
            AyahRef(41, 47),
            AyahRef(46, 1),
            AyahRef(51, 31),
            AyahRef(58, 1),
            AyahRef(67, 1),
            AyahRef(78, 1),
        )

    const val JUZ_COUNT = 30

    /** The juz (1 to 30) that holds [ayah]. */
    fun juzOf(ayah: AyahRef): Int = ayahs.indexOfLast { it <= ayah } + 1
}

/**
 * One row of the index list. Every row knows the [juz] it belongs to, so the juz rail can find the
 * current juz from the first visible row and the first row of a juz from its number.
 */
sealed interface IndexRow {
    val juz: Int
}

/**
 * The heading of a juz. [continuedChapter] is set when the juz begins inside a surah and no surah
 * begins in it, which the heading says ("Juz 2 · Al-Baqarah continues"). [startPage] is written
 * only when [showPage] is true.
 */
data class JuzHeading(
    override val juz: Int,
    val startPage: Int,
    val continuedChapter: Chapter?,
    val showPage: Boolean = true,
) : IndexRow

/**
 * A surah row. [page] is the page its first ayah is on; [showPage] is false when the heading above
 * already shows that page, so the number is not written twice.
 */
data class SurahRow(
    override val juz: Int,
    val chapter: Chapter,
    val page: Int,
    val showPage: Boolean,
) : IndexRow

/** A row of the Juz list: where the juz starts and the page that holds it. */
data class JuzStartRow(
    override val juz: Int,
    val startAyah: AyahRef,
    val chapter: Chapter,
    val page: Int,
) : IndexRow

/** A row of the Pages list. [startsSurah] is true when [chapter] begins on this page. */
data class PageRow(
    override val juz: Int,
    val page: Int,
    val chapter: Chapter,
    val startsSurah: Boolean,
) : IndexRow

/**
 * The surahs in Quran order with a heading at the start of every juz, as the Surahs list shows
 * them. A surah sits under the juz its first ayah is in.
 *
 * [surahPages] maps a surah number to the page of its first ayah and [juzPages] maps a juz number
 * to the page it starts on.
 */
fun buildSurahRows(
    chapters: List<Chapter>,
    surahPages: Map<Int, Int>,
    juzPages: Map<Int, Int>,
): List<IndexRow> {
    val chaptersByJuz = chapters.groupBy { JuzStarts.juzOf(AyahRef(it.number, 1)) }
    return (1..JuzStarts.JUZ_COUNT).flatMap { juz ->
        val surahsOfJuz = chaptersByJuz[juz].orEmpty().sortedBy { it.number }
        val headingPage = juzPages.getValue(juz)
        val continuedChapter =
            if (surahsOfJuz.isEmpty()) {
                chapters.first { it.number == JuzStarts.ayahs[juz - 1].surah }
            } else {
                null
            }
        val surahRows = surahsOfJuz.mapIndexed { index, chapter ->
            val page = surahPages.getValue(chapter.number)
            // The heading already carries the page when the first surah starts on it.
            SurahRow(juz, chapter, page, showPage = !(index == 0 && page == headingPage))
        }
        listOf<IndexRow>(JuzHeading(juz, headingPage, continuedChapter)) + surahRows
    }
}

/** The 30 juz, each with the surah and ayah it starts at and its first page. */
fun buildJuzRows(chapters: List<Chapter>, juzPages: Map<Int, Int>): List<IndexRow> =
    JuzStarts.ayahs.mapIndexed { index, startAyah ->
        val juz = index + 1
        JuzStartRow(
            juz = juz,
            startAyah = startAyah,
            chapter = chapters.first { it.number == startAyah.surah },
            page = juzPages.getValue(juz),
        )
    }

/**
 * Every page with its surah, under juz headings. The surah of a page is the first one that begins
 * on it, else the one that continues from an earlier page.
 */
fun buildPageRows(
    chapters: List<Chapter>,
    surahPages: Map<Int, Int>,
    juzPages: Map<Int, Int>,
    pageCount: Int,
): List<IndexRow> {
    val juzStartingOnPage = juzPages.entries.associate { (juz, page) -> page to juz }
    var currentJuz = 1
    val rows = mutableListOf<IndexRow>()
    for (page in 1..pageCount) {
        juzStartingOnPage[page]?.let { juz ->
            currentJuz = juz
            rows += JuzHeading(juz, page, continuedChapter = null, showPage = false)
        }
        val startingHere = chapters.firstOrNull { surahPages.getValue(it.number) == page }
        val chapter = startingHere ?: chapters.last { surahPages.getValue(it.number) < page }
        rows += PageRow(currentJuz, page, chapter, startsSurah = startingHere != null)
    }
    return rows
}

/** The list position of [surah]'s row, or null when nothing was read yet or it is not listed. */
fun rowIndexOfSurah(rows: List<IndexRow>, surah: Int?): Int? {
    if (surah == null) return null
    val index = rows.indexOfFirst { it is SurahRow && it.chapter.number == surah }
    return index.takeIf { it >= 0 }
}

/** The list position of the first row of [juz]: its heading, or its own row in the Juz list. */
fun rowIndexOfJuz(rows: List<IndexRow>, juz: Int): Int {
    val index = rows.indexOfFirst { it.juz == juz }
    check(index >= 0) { "The list has no row for juz $juz." }
    return index
}

/** What a bookmarks list row stands for. */
sealed interface BookmarkEntry {
    val page: Int
    val createdAt: Long

    data class PageMark(override val page: Int, override val createdAt: Long, val surah: Int) :
        BookmarkEntry

    data class AyahMark(
        val ayah: AyahRef,
        override val page: Int,
        override val createdAt: Long,
    ) : BookmarkEntry
}

enum class BookmarkSort {
    /** Newest first. */
    Date,
    /** In reading order: by page, a page mark before the ayahs on its page. */
    Position,
}

fun sortBookmarks(entries: List<BookmarkEntry>, sort: BookmarkSort): List<BookmarkEntry> =
    when (sort) {
        BookmarkSort.Date -> entries.sortedByDescending { it.createdAt }
        BookmarkSort.Position ->
            entries.sortedWith(
                compareBy<BookmarkEntry> { it.page }
                    .thenBy {
                        when (it) {
                            is BookmarkEntry.PageMark -> 0
                            is BookmarkEntry.AyahMark ->
                                it.ayah.surah * AYAH_ORDER_FACTOR + it.ayah.ayah
                        }
                    },
            )
    }

private const val AYAH_ORDER_FACTOR = 1000
