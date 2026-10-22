package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.formatCount
import app.awn.quran.feature.learn.words.WordFrequency
import app.awn.quran.feature.learn.words.WordProgress
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * What the course progress page draws. [curve] holds the share of the Quran's words covered by the
 * first 0, 1, 2 ... [curve].lastIndex most frequent forms, so the line shows how fast the share
 * grows with each word learned.
 */
internal data class CourseProgress(
    val wordsMet: Int,
    val wordsKnown: Int,
    val coverage: Double,
    val curve: List<Double>,
)

private const val PERCENT = 100
private const val CHART_STEP_FORMS = 50
private const val CHART_MINIMUM_FORMS = 100
private const val CHART_ASPECT_RATIO = 2f

/** The chart reaches twice as far as the user has come, in steps of 50 words, at least 100. */
internal fun buildCourseProgress(
    wordsMet: Int,
    knownRanks: Collection<Int>,
    frequency: WordFrequency,
): CourseProgress {
    val reach =
        (ceil(knownRanks.size * 2.0 / CHART_STEP_FORMS).toInt() * CHART_STEP_FORMS)
            .coerceAtLeast(CHART_MINIMUM_FORMS)
            .coerceAtMost(frequency.formCount)
    return CourseProgress(
        wordsMet = wordsMet,
        wordsKnown = knownRanks.size,
        coverage = WordProgress.coverage(knownRanks, frequency),
        curve = (0..reach).map { WordProgress.coverageOfTopForms(it, frequency) },
    )
}

/** Words known, the share of the Quran they cover, and the curve of that share. */
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
            CoverageChart(progress)
            Text(
                text = stringResource(R.string.progress_chart_axis),
                style = AwnTypography.small,
                color = Awn.field.mutedContent,
            )
        }
    }
}

@Composable
private fun CoverageChart(progress: CourseProgress) {
    val lineColour = Awn.field.content
    val gridColour = Awn.field.mutedContent
    val reach = progress.curve.lastIndex
    val chartTop = progress.curve.last().coerceAtLeast(progress.coverage)
    Canvas(Modifier.fillMaxWidth().aspectRatio(CHART_ASPECT_RATIO)) {
        val curvePath = Path()
        progress.curve.forEachIndexed { forms, share ->
            val point =
                Offset(
                    size.width * forms / reach,
                    size.height * (1f - (share / chartTop).toFloat()),
                )
            if (forms == 0) curvePath.moveTo(point.x, point.y)
            else curvePath.lineTo(point.x, point.y)
        }
        drawLine(
            gridColour,
            Offset(0f, size.height),
            Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        drawPath(curvePath, lineColour, style = Stroke(width = 2.dp.toPx()))
        val youX = size.width * progress.wordsKnown.coerceAtMost(reach) / reach
        val youY = size.height * (1f - (progress.coverage / chartTop).toFloat())
        drawCircle(lineColour, radius = 4.dp.toPx(), center = Offset(youX, youY))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = formatCount(0), style = AwnTypography.small, color = Awn.field.mutedContent)
        Text(
            text =
                pluralStringResource(
                    R.plurals.progress_chart_you,
                    progress.wordsKnown,
                    progress.wordsKnown,
                    (progress.coverage * PERCENT).roundToInt(),
                ),
            style = AwnTypography.small,
            color = Awn.field.content,
        )
        Text(text = formatCount(reach), style = AwnTypography.small, color = Awn.field.mutedContent)
    }
}
