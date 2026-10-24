package app.awn.quran.feature.duas.card

import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.ui.CardWord

/**
 * One tile of the word-by-word view: the Arabic word, its meaning and its sound in Latin letters.
 */
data class DuaWordTile(
    val key: WordKey,
    val arabic: String,
    val meaning: String,
    val transliteration: String,
)

/** Everything one dua card shows, read from the Quran text and the installed translations. */
data class DuaCardData(
    val dua: Dua,
    /** Every word of the dua's ayahs; words outside the dua are marked. */
    val words: List<CardWord>,
    /** The surah name and the ayah numbers, such as "Al-Baqarah 2:201". */
    val reference: String,
    /** The first ticked translation of the dua's ayahs, or null when none is ticked. */
    val translation: String?,
) {
    /** True when the dua is part of a longer ayah, so "Show dua only" has something to hide. */
    val hasWordsOutsideDua: Boolean
        get() = words.any { !it.isDuaWord }

    /** The dua's own words as one Arabic text, for search. */
    val duaArabic: String
        get() = words.filter { it.isDuaWord }.joinToString(separator = " ") { it.word.textUthmani }
}
