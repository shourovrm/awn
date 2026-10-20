package app.awn.quran.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography

/** Streak, pages this week, memorised ayahs by surah and the next revision date. */
@Composable
fun ProgressScreen(state: ProgressUiState, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    AwnTheme(field = LearnField) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            LearnBar(
                title = stringResource(R.string.progress_title),
                startLinkLabel = stringResource(R.string.back),
                onStartLinkClick = onBackClick,
            )
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                FigureTile(
                    field = AwnField.Green,
                    figure = state.streakDays,
                    caption = stringResource(R.string.progress_streak_caption),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                FigureTile(
                    field = AwnField.Night,
                    figure = state.pagesThisWeek,
                    caption = stringResource(R.string.learn_pages_caption),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Column(
                modifier = Modifier.padding(AwnSpacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            ) {
                MemorisedSummary(state)
                state.bySurah.forEach { SurahRow(it) }
                Text(
                    text =
                        state.nextRevisionDate?.let {
                            stringResource(R.string.progress_next_due, it)
                        } ?: stringResource(R.string.progress_next_none),
                    style = AwnTypography.body,
                    modifier = Modifier.padding(top = AwnSpacing.medium),
                )
            }
        }
    }
}

@Composable
private fun MemorisedSummary(state: ProgressUiState) {
    Text(
        text =
            pluralStringResource(
                R.plurals.progress_memorised,
                state.memorisedCount,
                formatCount(state.memorisedCount),
            ),
        style = AwnTypography.title,
    )
    Text(
        text =
            pluralStringResource(
                R.plurals.progress_memorised_rule,
                state.memorisationThreshold,
                formatCount(state.memorisationThreshold),
            ),
        style = AwnTypography.small,
    )
}

@Composable
private fun SurahRow(progress: SurahProgress) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = progress.name, style = AwnTypography.body)
        Text(text = formatCount(progress.memorisedCount), style = AwnTypography.bodyStrong)
    }
}
