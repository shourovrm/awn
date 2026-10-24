package app.awn.quran.feature.duas.ui

import app.awn.quran.core.data.Word
import app.awn.quran.feature.duas.content.Dua

/** A word on a dua card; words of the ayah outside the dua are shown in a softer ink. */
data class CardWord(val word: Word, val isDuaWord: Boolean)

/**
 * Marks which of the card's [words] (all words of the dua's ayahs, in order) belong to [dua]. The
 * dua can start or end inside an ayah, so a word counts when its key lies between the dua's first
 * and last word.
 */
fun cardWords(words: List<Word>, dua: Dua): List<CardWord> =
    words.map { word -> CardWord(word, isDuaWord = word.key >= dua.start && word.key <= dua.end) }
