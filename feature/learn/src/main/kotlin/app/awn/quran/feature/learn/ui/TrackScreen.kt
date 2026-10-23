package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.TrackSummary
import app.awn.quran.feature.learn.course.TrackUnit

/**
 * The numbered units of Reading, Tajweed or Grammar. Any unit can be opened, done or not, so a user
 * can go back over one. A track that is best started later says so in the line under the bar, but
 * its units still open: rows that look like the others and do nothing read as a broken page.
 */
@Composable
internal fun TrackScreen(
    summary: TrackSummary,
    units: List<TrackUnit>,
    onOpen: (TrackUnit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = trackName(summary.track),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        Column(
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            MutedLine(trackDetail(summary))
            units.forEach { unit ->
                StepRow(
                    state = unit.state,
                    title = stringResource(R.string.unit_numbered_title, unit.number, unit.title),
                    detail = null,
                    isToday = false,
                    onClick = { onOpen(unit) },
                )
            }
        }
    }
}
