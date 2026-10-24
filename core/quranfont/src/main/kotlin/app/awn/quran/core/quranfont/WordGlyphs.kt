package app.awn.quran.core.quranfont

import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey

/**
 * What draws a word in its page font: the glyph [word] and the Mushaf [page] whose font its glyph
 * code belongs to. A code is valid only with the font of its own page, and an ayah that runs over a
 * page break holds words of two pages.
 */
data class WordGlyphs(val word: Word, val page: Int)

/** One Arabic word with what the word by word view and its popup show. */
data class AyahWord(
    val key: WordKey,
    val arabic: String,
    /** Null when the word-by-word pack is not installed. */
    val meaning: String?,
    val transliteration: String?,
    /** Set for the glyph prints (V1, V2, tajweed); null when [arabic] is drawn as plain text. */
    val glyphs: WordGlyphs? = null,
)

/**
 * The glyphs of [word] when it has a glyph code in [script] and its page is known in [pageOfWord];
 * null otherwise, which draws the Unicode text.
 */
fun glyphsOf(word: Word, script: ScriptId?, pageOfWord: Map<WordKey, Int>): WordGlyphs? {
    val page = pageOfWord[word.key] ?: return null
    val hasCode =
        when (script) {
            ScriptId.V1 -> word.codeV1 != null
            ScriptId.V2,
            ScriptId.V4Tajweed -> word.codeV2 != null
            else -> false
        }
    return if (hasCode) WordGlyphs(word, page) else null
}
