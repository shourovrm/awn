package app.awn.quran.feature.learn.course

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.text.normalizeFallbackText
import java.util.concurrent.ConcurrentHashMap

/** An ayah as the learning screens draw it: its words in reading order. */
data class LearningAyah(val ayah: AyahRef, val words: List<LearningWord>)

/** One word ready to draw: [text] already carries the font's own mark codes. */
data class LearningWord(val key: WordKey, val text: String)

/**
 * The Arabic of the learning screens. Every word comes from the Quran text by its word key and is
 * drawn with the bundled KFGQPC Uthmanic Hafs font, which draws any form of a word, so a Madani
 * print of the user's choice does not matter here. The text goes through the same normaliser as
 * the Quran screen's Hafs text ([normalizeFallbackText]), so marks that the font codes differently
 * draw correctly.
 *
 * IndoPak readers would get the IndoPak text and font here; the app does not draw IndoPak yet
 * (see docs/decisions/learn.md), so the Hafs text is used for every print.
 */
class LearningArabic(
    private val quranText: QuranTextRepository,
    private val packs: PackRepository,
) {
    private val ayahs = ConcurrentHashMap<AyahRef, LearningAyah>()

    /** The font that draws the learning Arabic, or null while the font file is missing. */
    val fontFamily: FontFamily? by lazy {
        packs.fontFile(ScriptId.QpcHafs, 1)?.let { FontFamily(Font(it)) }
    }

    suspend fun ayah(ayah: AyahRef): LearningAyah =
        ayahs[ayah]
            ?: LearningAyah(ayah, quranText.wordsOfAyah(ayah).map(::learningWord)).also {
                ayahs[ayah] = it
            }

    suspend fun word(key: WordKey): LearningWord? =
        ayah(AyahRef(key.surah, key.ayah)).words.firstOrNull { it.key == key }

    private fun learningWord(word: Word) = LearningWord(word.key, normalizeFallbackText(word.textUthmani))
}
