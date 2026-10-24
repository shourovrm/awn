package app.awn.quran.core.quranfont

import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.ui.text.normalizeFallbackText

/** The scripts whose words are glyph codes of a font made for each page. */
val pageGlyphScripts = setOf(ScriptId.V1, ScriptId.V2, ScriptId.V4Tajweed)

/** The IndoPak print: Nastaleeq text in one Unicode font, laid out in its own 15-line pages. */
val indoPakScripts = setOf(ScriptId.IndoPak15)

/** The scripts the page draws with their own font; the others draw the Hafs text. */
val glyphScripts = pageGlyphScripts + indoPakScripts

/** True when [script] is drawn with one font per page. */
fun isGlyphScript(script: ScriptId): Boolean = script in glyphScripts

/**
 * Returns what to draw for [word]. Glyph scripts draw their private-use code with the page font; if
 * that font is missing, or the pack has no code, the Unicode text is drawn instead.
 */
fun textToDraw(word: Word, script: ScriptId, hasGlyphFont: Boolean): String {
    if (!hasGlyphFont) return normalizeFallbackText(word.textUthmani)
    val glyphCode =
        when (script) {
            ScriptId.V1 -> word.codeV1
            ScriptId.V2,
            ScriptId.V4Tajweed -> word.codeV2
            ScriptId.IndoPak15 -> word.textNastaleeq
            ScriptId.QpcHafs -> null
        }
    return glyphCode ?: word.textUthmani
}
