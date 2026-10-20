package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout

/**
 * The surah the title names: the first surah that starts on the page, or else the surah that
 * continues there. Page 604 therefore names surah 112 although 113 and 114 follow it.
 *
 * Switch this to the page agent's shared header-surah function when both branches are merged.
 */
internal fun titleSurahNumber(startedSurahs: List<Int>, firstWordSurah: Int?): Int? =
    startedSurahs.firstOrNull() ?: firstWordSurah

/** The title surah of a Mushaf page layout, in line order. */
internal fun titleSurahNumber(layout: PageLayout): Int? {
    val items = layout.lines.sortedBy { it.line }.flatMap { it.items }
    val started = items.filterIsInstance<LineItem.SurahHeader>().map { it.surah }
    val firstWordSurah = items.firstNotNullOfOrNull { item ->
        when (item) {
            is LineItem.WordItem -> item.word.key.surah
            is LineItem.AyahEnd -> item.ayah.surah
            is LineItem.SurahHeader,
            is LineItem.Basmala -> null
        }
    }
    return titleSurahNumber(started, firstWordSurah)
}

/** The title surah of a list-view page: its first surah band, or else the first ayah's surah. */
internal fun titleSurahNumber(content: PageContent): Int? {
    val started = content.items.filterIsInstance<PageItem.SurahBand>().map { it.chapter.number }
    return titleSurahNumber(started, content.ayahs.firstOrNull()?.surah)
}

internal fun List<Chapter>.chapterNumbered(number: Int?): Chapter? = firstOrNull {
    it.number == number
}
