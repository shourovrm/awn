package app.awn.quran.search

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.search.SearchHit
import app.awn.quran.core.data.search.SearchSource
import app.awn.quran.core.ui.text.normalizeFallbackText
import app.awn.quran.core.ui.text.normalizeFallbackTextWithMap

/**
 * The Arabic text of a typed search hit ready for the Hafs fallback font, with the marked ranges
 * moved to follow it. The stored text has a space before each pause mark, and the mark would be
 * drawn on a dotted circle. Hits of other sources are returned as they are.
 */
internal fun SearchHit.withFallbackText(): SearchHit {
    if (source != SearchSource.Arabic) return this
    val normalised = normalizeFallbackTextWithMap(text)
    return copy(text = normalised.text, matches = matches.map(normalised::mapRange))
}

/**
 * The hit of a recitation match: the words of [ayah] joined by spaces, each made ready for the Hafs
 * fallback font on its own (so a pause mark joins its word), with one marked range over the words
 * from [firstWord] to [lastWord] (counted from 0). A word number the text does not have is left
 * unmarked, because the reference and the text split four ayahs into different word counts.
 */
internal fun recitedHit(
    ayah: AyahRef,
    wordTexts: List<String>,
    firstWord: Int,
    lastWord: Int,
): SearchHit {
    val words = wordTexts.map(::normalizeFallbackText)
    val text = words.joinToString(separator = " ")
    val lastShownWord = lastWord.coerceAtMost(words.lastIndex)
    val matches =
        if (firstWord > lastShownWord) {
            emptyList()
        } else {
            val start = words.take(firstWord).sumOf { it.length + 1 }
            val end = start + words.subList(firstWord, lastShownWord + 1).joinToString(" ").length
            listOf(start until end)
        }
    return SearchHit(ayah, SearchSource.Arabic, text, matches)
}
