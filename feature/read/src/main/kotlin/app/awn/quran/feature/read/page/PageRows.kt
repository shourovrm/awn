package app.awn.quran.feature.read.page

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey

/** One thing drawn in a line of text: a tappable word or the rosette that ends an ayah. */
internal sealed interface LineToken {
    data class WordToken(val key: WordKey, val text: String) : LineToken

    /** The ayah-end sign. [glyphCode] is the printed sign of the page font, null to draw a ring. */
    data class RosetteToken(val ayah: AyahRef, val glyphCode: String?) : LineToken
}

/** One numbered slot of the page as the screen draws it. */
internal sealed interface PageRow {
    val slot: Int

    data class TextRow(override val slot: Int, val tokens: List<LineToken>) : PageRow

    /** The filled band that names [surah]. */
    data class HeaderRow(override val slot: Int, val surah: Int) : PageRow

    data class BasmalaRow(override val slot: Int) : PageRow
}

/** The scripts whose words are drawn as glyph codes from a font made for each page. */
internal val glyphScripts = setOf(ScriptId.V1, ScriptId.V2, ScriptId.V4Tajweed)

/**
 * Returns what to draw for [word]. Glyph scripts draw their private-use code with the page font; if
 * that font is missing, or the pack has no code, the Unicode text is drawn instead.
 */
internal fun textToDraw(word: Word, script: ScriptId, hasGlyphFont: Boolean): String {
    if (!hasGlyphFont) return normalizeFallbackText(word.textUthmani)
    val glyphCode =
        when (script) {
            ScriptId.V1 -> word.codeV1
            ScriptId.V2,
            ScriptId.V4Tajweed -> word.codeV2
            ScriptId.QpcHafs,
            ScriptId.IndoPak -> null
        }
    return glyphCode ?: word.textUthmani
}

/** Turns the layout lines into rows, keeping the slot numbers so short pages stay short. */
internal fun buildPageRows(layout: PageLayout, hasGlyphFont: Boolean): List<PageRow> =
    layout.lines.map { line ->
        val header = line.items.filterIsInstance<LineItem.SurahHeader>().firstOrNull()
        when {
            header != null -> PageRow.HeaderRow(line.line, header.surah)
            line.items.any { it is LineItem.Basmala } -> PageRow.BasmalaRow(line.line)
            else ->
                PageRow.TextRow(
                    line.line,
                    line.items.flatMap { item -> tokensOf(item, layout.script, hasGlyphFont) },
                )
        }
    }

private fun tokensOf(item: LineItem, script: ScriptId, hasGlyphFont: Boolean): List<LineToken> =
    when (item) {
        is LineItem.WordItem ->
            listOf(LineToken.WordToken(item.word.key, textToDraw(item.word, script, hasGlyphFont)))
        is LineItem.AyahEnd ->
            listOf(LineToken.RosetteToken(item.ayah, item.glyphCode.takeIf { hasGlyphFont }))
        is LineItem.SurahHeader,
        is LineItem.Basmala -> emptyList()
    }

/** Pages 1 and 2 are drawn as a centred block, not in the 15-line frame. */
internal fun isCentredBlockPage(layout: PageLayout): Boolean = layout.page <= 2

/** Lines in the layout, counting banner and Basmalah lines. */
internal fun slotCountOf(layout: PageLayout): Int = layout.lines.maxOfOrNull { it.line } ?: 0

/** True when [script] is drawn with one font per page. */
internal fun isGlyphScript(script: ScriptId): Boolean = script in glyphScripts
