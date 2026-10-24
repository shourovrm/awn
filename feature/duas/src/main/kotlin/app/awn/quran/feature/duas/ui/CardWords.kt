package app.awn.quran.feature.duas.ui

import app.awn.quran.core.data.Word
import app.awn.quran.feature.duas.content.Dua

/** A word on a dua card; words of the ayah outside the dua are shown in a softer ink. */
data class CardWord(val word: Word, val isDuaWord: Boolean)

/**
 * Marks which of the card's [words] (all words of the dua's ayahs, in order) belong to [dua]; the
 * UI slice writes it.
 */
fun cardWords(words: List<Word>, dua: Dua): List<CardWord> = TODO("ui slice")
