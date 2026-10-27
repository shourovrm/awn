package app.awn.quran.feature.duas.ui

import app.awn.quran.feature.duas.card.DuaCardData
import app.awn.quran.feature.duas.card.DuaWordTile

/**
 * The translation a card shows. With [showDuaOnly] set it is the translation of the dua's part, so
 * the whole translation stands in only when no word of the dua has a meaning to build it from.
 */
internal fun translationShownOf(
    card: DuaCardData,
    wordTiles: List<DuaWordTile>,
    showDuaOnly: Boolean,
): String? {
    if (!showDuaOnly) return card.translation
    return duaPartTranslationOf(wordTiles) ?: card.translation
}

/**
 * The translation of the dua's part, joined from the meanings of its words in order. A translation
 * is stored per ayah, so no split point exists inside an ayah; the word meanings are the only text
 * that follows the dua's words. Null when no word has a meaning.
 */
internal fun duaPartTranslationOf(wordTiles: List<DuaWordTile>): String? =
    wordTiles
        .map { tile -> tile.meaning.trim() }
        .filter { meaning -> meaning.isNotEmpty() }
        .joinToString(separator = " ")
        .takeIf { it.isNotEmpty() }
