package app.awn.quran.feature.read.page

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.Quarter
import app.awn.quran.core.data.Quarters

/**
 * The surah the page header names: the first surah that starts on the page (its first word is
 * here), or, when none starts here, the surah that continues onto it. Page 77 gives An-Nisa (4),
 * page 604 gives Al-Ikhlas (112, the first of the three surahs that start there) and page 50 gives
 * Ali 'Imran (3). Returns 0 only for a layout with no words.
 */
fun headerSurah(layout: PageLayout): Int {
    val words =
        layout.lines.flatMap { it.items }.filterIsInstance<LineItem.WordItem>().map { it.word.key }
    val firstStart = words.firstOrNull { it.ayah == 1 && it.wordPosition == 1 }
    return (firstStart ?: words.firstOrNull())?.surah ?: 0
}

/** The surah of [headerSurah] as a chapter, or null while the chapters are loading. */
internal fun headerChapter(layout: PageLayout, chapters: List<Chapter>): Chapter? {
    val surah = headerSurah(layout)
    return chapters.firstOrNull { it.number == surah }
}

/** What the page frame writes: the juz of the page and the quarter that starts on it, if any. */
internal data class PageHeaderInfo(val juz: Int, val startingQuarter: Quarter?)

internal fun pageHeaderInfo(layout: PageLayout): PageHeaderInfo =
    if (layout.script in indoPakScripts) {
        indoPakHeaderInfo(layout)
    } else {
        PageHeaderInfo(
            juz = JuzTable.juzOfPage(layout.page),
            startingQuarter = Quarters.startingOnPage(layout.page, layout.script).firstOrNull(),
        )
    }

/**
 * The Madani page tables do not fit the IndoPak pages, so the juz and the quarter come from the
 * words on the page: the juz of its first ayah, and the quarter whose first word is printed here.
 */
private fun indoPakHeaderInfo(layout: PageLayout): PageHeaderInfo {
    val keys =
        layout.lines.flatMap { it.items }.filterIsInstance<LineItem.WordItem>().map { it.word.key }
    val firstAyah = keys.firstOrNull()?.let { AyahRef(it.surah, it.ayah) }
    val startingQuarter =
        Quarters.all.firstOrNull { quarter ->
            keys.any { key ->
                key.wordPosition == 1 &&
                    key.surah == quarter.firstAyah.surah &&
                    key.ayah == quarter.firstAyah.ayah
            }
        }
    val juz = firstAyah?.let { Quarters.containing(it).juz } ?: 1
    return PageHeaderInfo(juz = juz, startingQuarter = startingQuarter)
}
