package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.formatCount
import app.awn.quran.feature.learn.session.LastAyah
import app.awn.quran.feature.learn.session.SessionSummary
import kotlin.math.roundToInt

/** The last screen of a day or a quiz. */
@Composable
internal fun EndFrame(summary: SessionSummary, onDone: () -> Unit) {
    BodyWithButton(stringResource(R.string.end_done), onDone) {
        if (summary.isOwnQuiz) QuizEnd(summary) else DayEnd(summary)
    }
}

@Composable
private fun QuizEnd(summary: SessionSummary) {
    Text(
        text = stringResource(R.string.end_quiz_title),
        style = AwnTypography.title,
        color = Awn.field.content,
    )
    Text(
        text =
            stringResource(
                R.string.end_quiz_summary,
                summary.answeredCorrectly,
                summary.wordsRevised,
            ),
        style = AwnTypography.body,
        color = Awn.field.content,
    )
}

@Composable
private fun DayEnd(summary: SessionSummary) {
    Text(
        text = stringResource(R.string.end_title),
        style = AwnTypography.title,
        color = Awn.field.content,
    )
    Text(
        text =
            stringResource(
                R.string.end_summary_join,
                pluralStringResource(
                    R.plurals.end_summary_revised,
                    summary.wordsRevised,
                    summary.wordsRevised,
                ),
                pluralStringResource(R.plurals.end_summary_new, summary.newWords, summary.newWords),
            ),
        style = AwnTypography.body,
        color = Awn.field.content,
    )
    KnownCard(summary)
    summary.lastAyah?.let { LastAyahCard(it) }
    CountRow(stringResource(R.string.end_words_met), summary.wordsMetBefore, summary.wordsMetAfter)
    CountRow(
        stringResource(R.string.end_words_known),
        summary.wordsKnownBefore,
        summary.wordsKnownAfter,
    )
    Text(
        text = stringResource(R.string.end_tomorrow),
        style = AwnTypography.bodyStrong,
        color = Awn.field.content,
    )
    Text(
        text =
            stringResource(
                R.string.end_tomorrow_detail_join,
                pluralStringResource(
                    R.plurals.end_tomorrow_detail_revise,
                    summary.tomorrowReviews,
                    summary.tomorrowReviews,
                ),
                pluralStringResource(
                    R.plurals.end_tomorrow_detail_new,
                    summary.tomorrowNewWords,
                    summary.tomorrowNewWords,
                ),
            ),
        style = AwnTypography.body,
        color = Awn.field.content,
    )
}

/** The words known as a poster figure on the green card, with their share of the Quran. */
@Composable
private fun KnownCard(summary: SessionSummary) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AwnShapes.control)
                .background(Awn.field.buttonFill)
                .padding(AwnSpacing.medium),
    ) {
        Text(
            text = formatCount(summary.wordsKnownAfter),
            style = AwnTypography.poster,
            color = Awn.field.onButton,
        )
        Text(
            text = stringResource(R.string.end_known, (summary.coverage * PERCENT).roundToInt()),
            style = AwnTypography.body,
            color = Awn.field.onButton,
        )
    }
}

/** The last ayah with each known word on a green block and each other word on a soft block. */
@Composable
private fun LastAyahCard(lastAyah: LastAyah) {
    val colors = paperColors()
    val environment = LocalLearnEnvironment.current
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AwnShapes.control)
                .background(colors.background)
                .padding(AwnSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(
            text = stringResource(R.string.end_last_ayah, lastAyah.ayah.toString()),
            style = AwnTypography.bodyStrong,
            color = colors.content,
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            ) {
                lastAyah.words.forEach { word ->
                    Text(
                        text = word.text,
                        style =
                            AwnTypography.lead.copy(
                                fontFamily =
                                    environment?.arabic?.fontFamily
                                        ?: AwnTypography.lead.fontFamily,
                            ),
                        color = if (word.isKnown) colors.onButton else colors.content,
                        modifier =
                            Modifier.clip(AwnShapes.wordBlock)
                                .background(
                                    if (word.isKnown) colors.buttonFill else colors.hiddenWordBlock,
                                )
                                .padding(
                                    horizontal = AwnSpacing.small,
                                    vertical = AwnSpacing.small,
                                ),
                    )
                }
            }
        }
        Text(
            text =
                pluralStringResource(
                    R.plurals.end_last_ayah_known,
                    lastAyah.words.size,
                    lastAyah.knownCount,
                    lastAyah.words.size,
                ),
            style = AwnTypography.small,
            color = colors.content,
        )
    }
}

@Composable
private fun CountRow(label: String, before: Int, after: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = AwnTypography.body, color = Awn.field.content)
        Text(
            text = stringResource(R.string.end_range, before, after),
            style = AwnTypography.lead,
            color = Awn.field.content,
        )
    }
}

private const val PERCENT = 100
