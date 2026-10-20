package app.awn.quran.feature.read.page

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density

internal const val MEASURE_FONT_PX = 200f
/** Used when the text engine reports no baseline, as the test fonts of Robolectric do. */
internal const val FALLBACK_BASELINE_EM = 0.8f
internal const val RING_WIDTH_EM = 1.1f

/** The text style of page text at [fontSizePx]; no extra line height, so baselines are exact. */
@Suppress("DEPRECATION") // includeFontPadding is the only way to get exact font metrics here.
internal fun pageTextStyle(family: FontFamily?, fontSizePx: Float, density: Density): TextStyle =
    TextStyle(
        fontFamily = family,
        fontSize = with(density) { fontSizePx.toSp() },
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle =
            LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.None,
            ),
    )

/** Widths of the tokens of a page, in ems of its font, and where its first baseline falls. */
internal class TokenMeasurements(
    private val widthsEm: Map<String, Float>,
    val firstBaselineEm: Float,
) {
    fun widthEm(token: LineToken): Float =
        when (token) {
            is LineToken.WordToken -> widthsEm.getValue(token.text)
            is LineToken.RosetteToken ->
                token.glyphCode?.let { widthsEm.getValue(it) } ?: RING_WIDTH_EM
        }
}

internal fun measureTokens(
    rows: List<PageRow>,
    style: TextStyle,
    fontSizePx: Float,
    measurer: TextMeasurer,
): TokenMeasurements {
    val texts =
        rows
            .filterIsInstance<PageRow.TextRow>()
            .flatMap { it.tokens }
            .mapNotNull { token ->
                when (token) {
                    is LineToken.WordToken -> token.text
                    is LineToken.RosetteToken -> token.glyphCode
                }
            }
            .distinct()
    var firstBaseline = 0f
    val widths = HashMap<String, Float>()
    for (text in texts) {
        val result = measurer.measure(text, style, softWrap = false, maxLines = 1)
        widths[text] = result.size.width / fontSizePx
        if (firstBaseline == 0f) firstBaseline = result.firstBaseline / fontSizePx
    }
    val baselineEm = if (firstBaseline > 0f) firstBaseline else FALLBACK_BASELINE_EM
    return TokenMeasurements(widths, baselineEm)
}

internal fun toArabicIndicDigits(number: Int): String =
    number.toString().map { digit -> '٠' + (digit - '0') }.joinToString("")
