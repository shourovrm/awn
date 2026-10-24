package app.awn.quran.search

import java.text.BreakIterator

private const val LEADING_ELLIPSIS = "…"
private const val DEFAULT_WORDS_BEFORE_MATCH = 3

/** The part of a result text that is shown, with the match ranges moved to fit it. */
internal data class ResultWindow(val text: String, val matches: List<IntRange>)

/**
 * The tail of [text] that starts [wordsBefore] words before the first of [matches], behind a
 * leading "…". A result is cut to a few lines, so a match late in a long ayah or translation would
 * otherwise be hidden and the user could not see why it matched. The window starts at a word break,
 * which is always a grapheme cluster boundary, so no letter is parted from its marks, and every
 * range moves by the same distance, so ranges that were snapped to whole clusters stay snapped.
 * [text] comes back unchanged when the match is already within the first words.
 */
internal fun windowBeforeFirstMatch(
    text: String,
    matches: List<IntRange>,
    wordsBefore: Int = DEFAULT_WORDS_BEFORE_MATCH,
): ResultWindow {
    val firstMatchStart = matches.firstOrNull()?.first ?: return ResultWindow(text, matches)
    val windowStart =
        clusterBoundaryAtOrBefore(text, startOfWordsBefore(text, firstMatchStart, wordsBefore))
    if (windowStart == 0) return ResultWindow(text, matches)
    val shift = LEADING_ELLIPSIS.length - windowStart
    return ResultWindow(
        text = LEADING_ELLIPSIS + text.substring(windowStart),
        matches = matches.map { match -> (match.first + shift)..(match.last + shift) },
    )
}

/** The index where the [wordsBefore]th word before the word that holds [position] begins. */
private fun startOfWordsBefore(text: String, position: Int, wordsBefore: Int): Int {
    var index = position
    while (index > 0 && !text[index - 1].isWhitespace()) index -= 1
    repeat(wordsBefore) {
        while (index > 0 && text[index - 1].isWhitespace()) index -= 1
        while (index > 0 && !text[index - 1].isWhitespace()) index -= 1
    }
    return index
}

private fun clusterBoundaryAtOrBefore(text: String, index: Int): Int {
    val clusters = BreakIterator.getCharacterInstance()
    clusters.setText(text)
    return if (clusters.isBoundary(index)) index else clusters.preceding(index)
}
