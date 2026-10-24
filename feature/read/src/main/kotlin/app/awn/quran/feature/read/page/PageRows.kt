package app.awn.quran.feature.read.page

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.text.normalizeFallbackText

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

/** The scripts whose words are glyph codes of a font made for each page. */
val pageGlyphScripts = setOf(ScriptId.V1, ScriptId.V2, ScriptId.V4Tajweed)

/** The IndoPak print: Nastaleeq text in one Unicode font, laid out in its own 15-line pages. */
internal val indoPakScripts = setOf(ScriptId.IndoPak15)

/** The scripts the page draws with their own font; the others draw the Hafs text. */
internal val glyphScripts = pageGlyphScripts + indoPakScripts

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
            ScriptId.IndoPak15 -> word.textNastaleeq
            ScriptId.QpcHafs -> null
        }
    return glyphCode ?: word.textUthmani
}

/** Turns the layout lines into rows, keeping the slot numbers so short pages stay short. */
internal fun buildPageRows(layout: PageLayout, hasGlyphFont: Boolean): List<PageRow> {
    val shift = slotShiftOf(layout)
    return layout.lines.map { line ->
        val slot = line.line + shift
        val header = line.items.filterIsInstance<LineItem.SurahHeader>().firstOrNull()
        when {
            header != null -> PageRow.HeaderRow(slot, header.surah)
            line.items.any { it is LineItem.Basmala } -> PageRow.BasmalaRow(slot)
            else ->
                PageRow.TextRow(
                    slot,
                    line.items.flatMap { item -> tokensOf(item, layout.script, hasGlyphFont) },
                )
        }
    }
}

/**
 * How far the slot numbers move so the first one is 1. An IndoPak page whose surah header has no
 * free slot above its first word has lines numbered 0 and below (see [PageLine]); they are drawn
 * above slot 1 by giving every slot of that page the same height as the extra ones.
 */
internal fun slotShiftOf(layout: PageLayout): Int {
    val firstLine = layout.lines.minOfOrNull { it.line } ?: 1
    return if (firstLine < 1) 1 - firstLine else 0
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

/**
 * Pages 1 and 2 of the Madani prints are drawn as a centred block, not in the 15-line frame. The
 * IndoPak prints print them at the bottom of the full frame, which their line numbers already say.
 */
internal fun isCentredBlockPage(layout: PageLayout): Boolean =
    layout.page <= 2 && layout.script !in indoPakScripts

/** Lines in the layout, counting banner and Basmalah lines. */
internal fun slotCountOf(layout: PageLayout): Int = layout.lines.maxOfOrNull { it.line } ?: 0

/**
 * The slots the page frame is divided into: the 15 lines of the print, or more when extra header
 * lines were added above slot 1.
 */
internal fun frameSlotCountOf(layout: PageLayout): Int =
    maxOf(PageGeometry.SLOTS, slotCountOf(layout) + slotShiftOf(layout))

/** True when [script] is drawn with one font per page. */
fun isGlyphScript(script: ScriptId): Boolean = script in glyphScripts
