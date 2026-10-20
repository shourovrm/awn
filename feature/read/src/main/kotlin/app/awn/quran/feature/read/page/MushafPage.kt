package app.awn.quran.feature.read.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.tracker.WordState

internal const val SLOTS_PER_PAGE = 15
private const val FULL_LINE_SHARE = 0.8f
private const val HIDDEN_BLOCK_ALPHA = 0.3f

/** The text in the bismillah line; it is not part of the word tables. */
internal const val BASMALA_TEXT = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"

/**
 * One mushaf page: [layout] drawn right to left in numbered line slots. [fontFamily] is the font
 * made for [layout]'s script and page, or null when it is not installed; glyph scripts then draw
 * the Unicode text with [plainFamily] instead.
 *
 * [wordStates] gives the colour state of each word, so reciting screens can reuse the page.
 */
@Composable
fun MushafPage(
    layout: PageLayout,
    chapters: List<Chapter>,
    fontFamily: FontFamily?,
    plainFamily: FontFamily?,
    zoom: Float,
    selectedWord: WordKey?,
    wordStates: (WordKey) -> WordState,
    onWordClick: (WordKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasGlyphFont = fontFamily != null
    val drawsGlyphs = layout.script in glyphScripts && hasGlyphFont
    val textFamily = if (layout.script in glyphScripts && !hasGlyphFont) plainFamily else fontFamily
    val rows = remember(layout, drawsGlyphs) { buildPageRows(layout, drawsGlyphs) }
    val baseStyle =
        AwnTypography.quran.copy(
            fontSize = AwnTypography.quran.fontSize * zoom,
            fontFamily = textFamily ?: AwnTypography.quran.fontFamily,
        )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val slotHeight = maxHeight / SLOTS_PER_PAGE
            val availableWidthPx = with(density) { maxWidth.toPx() }
            val metrics =
                remember(rows, baseStyle, availableWidthPx) {
                    measurePage(rows, baseStyle, availableWidthPx, textMeasurer, density)
                }
            val pageStyle = baseStyle.copy(fontSize = baseStyle.fontSize * metrics.fontScale)

            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (slot in 1..slotCountOf(layout)) {
                    Box(Modifier.fillMaxWidth().height(slotHeight), Alignment.Center) {
                        when (val row = rows.firstOrNull { it.slot == slot }) {
                            is PageRow.TextRow ->
                                TextLine(
                                    row = row,
                                    style = pageStyle,
                                    isJustified = metrics.isJustified(row),
                                    selectedWord = selectedWord,
                                    wordStates = wordStates,
                                    onWordClick = onWordClick,
                                )
                            is PageRow.HeaderRow ->
                                SurahBand(
                                    name =
                                        chapters.firstOrNull { it.number == row.surah }?.nameArabic,
                                    style = pageStyle.copy(fontFamily = plainFamily),
                                )
                            is PageRow.BasmalaRow ->
                                Text(
                                    text = BASMALA_TEXT,
                                    style = pageStyle.copy(fontFamily = plainFamily),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            null -> Spacer(Modifier)
                        }
                    }
                }
            }
        }
    }
}

/** How big the text of a page may be so its widest line fits, and which lines fill the width. */
internal class PageMetrics(
    val fontScale: Float,
    private val fullLines: Set<Int>,
) {
    fun isJustified(row: PageRow.TextRow): Boolean = row.slot in fullLines
}

private val tokenGap = 4.dp

private fun measurePage(
    rows: List<PageRow>,
    style: TextStyle,
    availableWidthPx: Float,
    textMeasurer: TextMeasurer,
    density: Density,
): PageMetrics {
    val fixedGapPx = with(density) { tokenGap.toPx() }
    val rosettePx = with(density) { AwnSpacing.large.toPx() }
    val textRows = rows.filterIsInstance<PageRow.TextRow>()
    val wordWidths = textRows.associate { row ->
        val words = row.tokens.filterIsInstance<LineToken.WordToken>()
        row.slot to words.sumOf { textMeasurer.measure(it.text, style).size.width }.toFloat()
    }
    val fixedWidths = textRows.associate { row ->
        val rosettes = row.tokens.count { it is LineToken.RosetteToken }
        row.slot to rosettes * rosettePx + (row.tokens.size - 1).coerceAtLeast(0) * fixedGapPx
    }

    val scaleToFit =
        textRows.minOfOrNull { row ->
            val words = wordWidths.getValue(row.slot)
            if (words <= 0f) 1f else (availableWidthPx - fixedWidths.getValue(row.slot)) / words
        } ?: 1f
    val fontScale = scaleToFit.coerceIn(0f, 1f)
    val fullLines =
        textRows
            .filter { row ->
                val width =
                    wordWidths.getValue(row.slot) * fontScale + fixedWidths.getValue(row.slot)
                width >= availableWidthPx * FULL_LINE_SHARE
            }
            .map { it.slot }
            .toSet()
    return PageMetrics(fontScale, fullLines)
}

@Composable
private fun TextLine(
    row: PageRow.TextRow,
    style: TextStyle,
    isJustified: Boolean,
    selectedWord: WordKey?,
    wordStates: (WordKey) -> WordState,
    onWordClick: (WordKey) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (isJustified) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.spacedBy(tokenGap, Alignment.CenterHorizontally)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        row.tokens.forEach { token ->
            when (token) {
                is LineToken.WordToken ->
                    WordText(
                        token = token,
                        style = style,
                        state = wordStates(token.key),
                        isSelected = token.key == selectedWord,
                        onClick = { onWordClick(token.key) },
                    )
                is LineToken.RosetteToken -> Rosette(number = token.ayah.ayah)
            }
        }
    }
}

@Composable
private fun WordText(
    token: LineToken.WordToken,
    style: TextStyle,
    state: WordState,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val field = Awn.field
    val textColor: Color
    val blockColor: Color
    when {
        isSelected -> {
            textColor = field.onButton
            blockColor = field.buttonFill
        }
        state == WordState.Confirmed -> {
            textColor = field.confirmedWord
            blockColor = Color.Transparent
        }
        state == WordState.Tentative -> {
            textColor = field.tentativeWord
            blockColor = Color.Transparent
        }
        state == WordState.Wrong -> {
            textColor = field.wrongWord
            blockColor = Color.Transparent
        }
        state == WordState.Hidden -> {
            textColor = Color.Transparent
            blockColor = field.content.copy(alpha = HIDDEN_BLOCK_ALPHA)
        }
        else -> {
            textColor = field.content
            blockColor = Color.Transparent
        }
    }
    Text(
        text = token.text,
        style = style,
        color = textColor,
        maxLines = 1,
        softWrap = false,
        modifier =
            Modifier.clip(AwnShapes.wordBlock).background(blockColor).clickable(onClick = onClick),
    )
}

@Composable
private fun Rosette(number: Int) {
    Box(
        modifier = Modifier.size(AwnSpacing.large).clip(CircleShape).background(AwnColors.ochre),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = AwnTypography.small,
            color = AwnColors.ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun SurahBand(name: String?, style: TextStyle) {
    val field = Awn.field
    Box(
        modifier = Modifier.fillMaxWidth().clip(AwnShapes.wordBlock).background(field.buttonFill),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name.orEmpty(), style = style, color = field.onButton, maxLines = 1)
    }
}

/** True when [script] is drawn with one font per page. */
internal fun isGlyphScript(script: ScriptId): Boolean = script in glyphScripts
