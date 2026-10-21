package app.awn.quran.index

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import app.awn.quran.R
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import java.text.NumberFormat

private const val ELLIPSIS = "…"

/**
 * The longest start of [words] that fits in [maxWidth], cut only between words, with an ellipsis
 * after it when words were left out. [measure] gives the width of a string in the same unit as
 * [maxWidth]. A first word wider than [maxWidth] is returned whole: a word is never cut.
 */
fun fitWholeWords(
    words: List<String>,
    maxWidth: Float,
    measure: (String) -> Float,
): String {
    val whole = words.joinToString(" ")
    if (words.isEmpty() || measure(whole) <= maxWidth) return whole
    val fittingCount =
        (words.size - 1 downTo 1).firstOrNull { count ->
            measure(words.take(count).joinToString(" ") + " " + ELLIPSIS) <= maxWidth
        } ?: 1
    return words.take(fittingCount).joinToString(" ") + " " + ELLIPSIS
}

/**
 * The one primary action of the Quran tab: opens the Quran screen at the page read last (page 1
 * before anything was read). It shows the first line of that page in Arabic, cut between words.
 */
@Composable
internal fun ContinueCard(
    reading: ContinueReading,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    val title =
        stringResource(if (reading.isStart) R.string.continue_start else R.string.continue_reading)
    val numbers = NumberFormat.getIntegerInstance()
    val detail =
        stringResource(
            R.string.continue_detail,
            reading.chapter.nameSimple,
            numbers.format(reading.page),
            numbers.format(reading.juz),
        )
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AwnShapes.control)
                .background(field.buttonFill)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(AwnSpacing.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = AwnTypography.bodyStrong, color = field.onButton)
                Text(text = detail, style = AwnTypography.small, color = field.onButton)
            }
            Icon(
                painter = painterResource(UiR.drawable.ic_ui_chevron),
                contentDescription = null,
                tint = field.onButton,
                modifier = Modifier.size(AwnSpacing.iconSize),
            )
        }
        if (reading.firstLineWords.isNotEmpty()) {
            FirstLine(
                words = reading.firstLineWords,
                color = field.onButton,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
    }
}

@Composable
private fun FirstLine(words: List<String>, color: Color, modifier: Modifier = Modifier) {
    val style = AwnTypography.quranLead.copy(textDirection = TextDirection.Rtl)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxWidth = constraints.maxWidth.toFloat()
        val text =
            remember(words, maxWidth, style) {
                fitWholeWords(words, maxWidth) { measurer.widthOf(it, style) }
            }
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun TextMeasurer.widthOf(text: String, style: TextStyle): Float =
    measure(text = text, style = style, softWrap = false, maxLines = 1).size.width.toFloat()
