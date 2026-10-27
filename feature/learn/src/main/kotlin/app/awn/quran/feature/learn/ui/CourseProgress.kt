package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.formatCount
import app.awn.quran.feature.learn.words.CoverageBars
import app.awn.quran.feature.learn.words.CoverageMilestone
import app.awn.quran.feature.learn.words.WordFrequency
import app.awn.quran.feature.learn.words.WordProgress
import kotlin.math.roundToInt

/**
 * What the course progress page draws. [milestones] hold the share of the Quran's words covered by
 * the 100, 200 ... 5,000 most frequent words, the ends of the seven word levels.
 */
internal data class CourseProgress(
    val wordsMet: Int,
    val wordsKnown: Int,
    val coverage: Double,
    val milestones: List<CoverageMilestone>,
)

private const val PERCENT = 100
// A share times 1,000 is tenths of a percent; dividing by 10 gives the percent with one decimal.
private const val PERCENT_DECIMALS_FACTOR = 1_000
private const val TENTHS_PER_PERCENT = 10.0
private val CHART_MAXIMUM_HEIGHT = AwnSpacing.coverageChartHeight
// A landscape phone is about 360 dp high; this share of the screen keeps bars above the fold.
private const val CHART_SCREEN_HEIGHT_SHARE = 0.4f
private val CHART_MINIMUM_HEIGHT = 120.dp
private val BAR_CORNER = 4.dp
// Keeps a short-number column (100) wide enough that its bar does not look like a sliver.
private val BAR_MINIMUM_WIDTH = 28.dp

internal fun buildCourseProgress(
    wordsMet: Int,
    knownRanks: Collection<Int>,
    frequency: WordFrequency,
): CourseProgress =
    CourseProgress(
        wordsMet = wordsMet,
        wordsKnown = knownRanks.size,
        coverage = WordProgress.coverage(knownRanks, frequency),
        milestones = CoverageBars.milestones(frequency),
    )

/** Words known, the share of the Quran they cover, and the share each word count covers. */
@Composable
internal fun CourseProgressScreen(
    progress: CourseProgress,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = stringResource(R.string.progress_words_title),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        Column(
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            val coveragePercent = (progress.coverage * PERCENT).roundToInt()
            Text(
                text = formatCount(progress.wordsKnown),
                style = AwnTypography.poster,
                color = Awn.field.content,
            )
            Text(
                text = stringResource(R.string.progress_known_caption),
                style = AwnTypography.lead,
                color = Awn.field.content,
            )
            Text(
                text = stringResource(R.string.progress_known_share, coveragePercent),
                style = AwnTypography.body,
                color = Awn.field.content,
            )
            Text(
                text = stringResource(R.string.progress_chart_title),
                style = AwnTypography.bodyStrong,
                color = Awn.field.content,
                modifier = Modifier.padding(top = AwnSpacing.large),
            )
            CoverageBarChart(progress)
        }
    }
}

/**
 * One column per word count, then the user's own column in ochre, all on one scale. The columns
 * share the row width equally, and each grows to its widest text when that text is wider than its
 * share. The row scrolls sideways when large text needs more room than the screen, so a label such
 * as 5,000 is never clipped or drawn over its neighbour.
 */
@Composable
private fun CoverageBarChart(progress: CourseProgress) {
    val bars =
        progress.milestones.map {
            ChartBar(formatCount(it.wordCount), it.share, Awn.field.buttonFill)
        } +
            ChartBar(
                stringResource(R.string.progress_chart_you_label),
                progress.coverage,
                AwnColors.ochre,
            )
    val chartHeight = responsiveChartHeight()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minimumColumnWidth = columnMinimumWidth(availableWidth = maxWidth, barCount = bars.size)
        Row(
            modifier =
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            bars.forEach { bar -> BarColumn(bar, minimumColumnWidth) }
        }
    }
}

/**
 * The chart is capped at [CHART_MAXIMUM_HEIGHT] so portrait keeps its full height, and shrinks on a
 * short screen so the bars stay visible without scrolling the page.
 */
@Composable
private fun responsiveChartHeight(): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    return (screenHeight * CHART_SCREEN_HEIGHT_SHARE).coerceIn(
        CHART_MINIMUM_HEIGHT,
        CHART_MAXIMUM_HEIGHT,
    )
}

/**
 * An equal slice of the row for each column, after the gaps between columns, but never below
 * [BAR_MINIMUM_WIDTH] so a short label still leaves its bar a readable width.
 */
private fun columnMinimumWidth(availableWidth: Dp, barCount: Int): Dp {
    val totalGapWidth = AwnSpacing.small * (barCount - 1)
    val equalShareWidth = (availableWidth - totalGapWidth) / barCount
    return maxOf(equalShareWidth, BAR_MINIMUM_WIDTH)
}

private class ChartBar(val label: String, val share: Double, val colour: Color)

@Composable
private fun BarColumn(bar: ChartBar, minimumWidth: Dp) {
    val percentText = stringResource(R.string.progress_chart_percent, bar.percentOneDecimal())
    Column(
        modifier = Modifier.widthIn(min = minimumWidth).width(IntrinsicSize.Max).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = percentText,
            style = AwnTypography.small,
            color = Awn.field.content,
            maxLines = 1,
            softWrap = false,
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(bar.share.toFloat().coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(topStart = BAR_CORNER, topEnd = BAR_CORNER))
                        .background(bar.colour),
            )
        }
        Text(
            text = bar.label,
            style = AwnTypography.small,
            color = Awn.field.mutedContent,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun ChartBar.percentOneDecimal(): Double =
    (share * PERCENT_DECIMALS_FACTOR).roundToInt() / TENTHS_PER_PERCENT
