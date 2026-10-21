package app.awn.quran.feature.hifz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnFieldColors
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.hifz.R
import app.awn.quran.feature.hifz.method.JuzRow
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.ProgressMap

// Memorised sits between the page colour and the confirmed colour, so it reads as a lighter Solid.
private const val MEMORISED_MIX = 0.55f

// The longest juz of the 1421 print has 21 pages (juz 1), so squares are sized for 21.
private const val WIDEST_ROW_PAGES = 21

/** The four colours of the map in the active colour mode, taken from the mode's own palette. */
internal data class StatusColors(
    val notStarted: Color,
    val learning: Color,
    val memorised: Color,
    val solid: Color,
) {
    fun of(status: PageStatus): Color =
        when (status) {
            PageStatus.NotStarted -> notStarted
            PageStatus.Learning -> learning
            PageStatus.Memorised -> memorised
            PageStatus.Solid -> solid
        }

    companion object {
        fun of(field: AwnFieldColors) =
            StatusColors(
                notStarted = field.softFill,
                learning = field.tentativeWord,
                memorised = lerp(field.background, field.confirmedWord, MEMORISED_MIX),
                solid = field.confirmedWord,
            )
    }
}

/** One row per juz, one square per page, then the legend. */
@Composable
internal fun ProgressMapView(map: ProgressMap, modifier: Modifier = Modifier) {
    val colors = StatusColors.of(Awn.field)
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labelWidth = AwnSpacing.large
            val gap = 2.dp
            val squareSize: Dp =
                (maxWidth - labelWidth - gap * (WIDEST_ROW_PAGES - 1)) / WIDEST_ROW_PAGES
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                map.rows.forEach { row -> MapRow(row, colors, squareSize, gap, labelWidth) }
            }
        }
        Legend(colors, Modifier.padding(top = AwnSpacing.medium))
    }
}

@Composable
private fun MapRow(row: JuzRow, colors: StatusColors, square: Dp, gap: Dp, labelWidth: Dp) {
    val memorised = row.statuses.count { it.isKnown }
    val description =
        pluralStringResource(
            R.plurals.juz_row_description,
            row.statuses.size,
            row.juz,
            memorised,
            row.statuses.size,
        )
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.juz.toString(),
            style = AwnTypography.caption,
            color = Awn.field.content,
            modifier = Modifier.width(labelWidth),
        )
        Canvas(
            Modifier.height(square).width((square + gap) * row.statuses.size),
        ) {
            val step = (square + gap).toPx()
            val corner = CornerRadius(2.dp.toPx())
            row.statuses.forEachIndexed { index, status ->
                drawRoundRect(
                    color = colors.of(status),
                    topLeft = Offset(index * step, 0f),
                    size = Size(square.toPx(), square.toPx()),
                    cornerRadius = corner,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Legend(colors: StatusColors, modifier: Modifier = Modifier) {
    // Four labels do not fit one line at large text sizes, so they wrap.
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        PageStatus.entries.forEach { status ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(AwnSpacing.medium)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.of(status)),
                )
                Text(
                    text = statusName(status),
                    style = AwnTypography.caption,
                    color = Awn.field.content,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
