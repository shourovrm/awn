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

/** A surah row of the Surahs tab: the chapter and the page its first ayah is on. */
data class SurahEntry(val chapter: Chapter, val page: Int)

/** The surahs that begin in [juz], under a header that shows the page the juz starts on. */
data class JuzGroup(val juz: Int, val startPage: Int, val surahs: List<SurahEntry>)

/**
 * Groups the surahs by the juz they begin in. A juz in which no surah begins (the 2nd, for
 * instance, lies inside Al-Baqarah) has no group; the Juz tab lists it.
 *
 * [surahPages] maps a surah number to the page of its first ayah and [juzPages] maps a juz number
 * to the page it starts on.
 */
fun groupSurahsByJuz(
    chapters: List<Chapter>,
    surahPages: Map<Int, Int>,
    juzPages: Map<Int, Int>,
): List<JuzGroup> =
    chapters
        .groupBy { chapter -> JuzStarts.juzOf(AyahRef(chapter.number, 1)) }
        .toSortedMap()
        .map { (juz, surahsOfJuz) ->
            JuzGroup(
                juz = juz,
                startPage = juzPages.getValue(juz),
                surahs =
                    surahsOfJuz
                        .sortedBy { it.number }
                        .map { chapter ->
                            SurahEntry(chapter, surahPages.getValue(chapter.number))
                        },
            )
        }

/**
 * Where a quarter of a juz begins. Each juz has 8 quarters, numbered 1 to 8 from the start of the
 * juz. The page agent's table in core/data supplies these; until it is connected, [QuarterTable] is
 * implemented by [JuzStartQuarterTable].
 */
data class QuarterStart(val juz: Int, val quarterOfJuz: Int, val firstAyah: AyahRef)

/** The 240 quarters of the Quran (or fewer, when only part of the table is known). */
fun interface QuarterTable {
    suspend fun quarters(): List<QuarterStart>
}

/**
 * Stand-in until the quarter table in core/data is connected: it knows only the first quarter of
 * each juz, so the Juz tab lists 30 rows instead of 240.
 */
object JuzStartQuarterTable : QuarterTable {
    override suspend fun quarters(): List<QuarterStart> =
        JuzStarts.ayahs.mapIndexed { index, ayah -> QuarterStart(index + 1, 1, ayah) }
}

/** One row of the Juz tab. [fraction] is 0 at the start of a hizb and 1 to 3 for ¼, ½ and ¾. */
data class QuarterRow(
    val juz: Int,
    val hizb: Int,
    val fraction: Int,
    val firstAyah: AyahRef,
    val firstWords: String,
    val page: Int,
)

data class JuzSection(val juz: Int, val startPage: Int, val quarters: List<QuarterRow>)

private const val QUARTERS_PER_HIZB = 4
private const val HIZB_PER_JUZ = 2

/** The hizb (1 to 60) that quarter [quarterOfJuz] of [juz] lies in. */
fun hizbOfQuarter(juz: Int, quarterOfJuz: Int): Int =
    (juz - 1) * HIZB_PER_JUZ + (quarterOfJuz - 1) / QUARTERS_PER_HIZB + 1

/** 0 when the quarter begins a hizb, otherwise 1, 2 or 3 quarters into it. */
fun fractionOfQuarter(quarterOfJuz: Int): Int = (quarterOfJuz - 1) % QUARTERS_PER_HIZB

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
