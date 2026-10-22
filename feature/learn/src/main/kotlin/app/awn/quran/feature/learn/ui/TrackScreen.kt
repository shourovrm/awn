package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.TrackSummary
import app.awn.quran.feature.learn.course.TrackUnit

/**
 * The numbered units of Reading, Tajweed or Grammar. Any unit can be opened, done or not, so a user
 * can go back over one; a closed track lists its units but opens none.
 */
@Composable
internal fun TrackScreen(
    summary: TrackSummary,
    units: List<TrackUnit>,
    onOpen: (TrackUnit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOpen = summary.opensAfterReadingUnit == null
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
                    onClick = if (isOpen) ({ onOpen(unit) }) else null,
                )
            }
        }
    }
}

/**
 * The Quran words track: the ten-word sets in frequency order with each grammar lesson after the
 * set that brings it, then the word list and the quiz.
 */
@Composable
internal fun WordsTrackScreen(
    state: HubState,
    actions: HubActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = trackName(LearnTrack.Words),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        Column(
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            state.path.forEach { PathRowView(it, state, actions) }
            SettingsLinkRow(
                label = stringResource(R.string.course_word_list),
                value =
                    pluralStringResource(
                        R.plurals.course_word_list_detail,
                        state.wordsMet,
                        state.wordsMet,
                        state.wordsKnown,
                    ),
                icon = R.drawable.ic_learn_list,
                onClick = actions.onWordList,
            )
            SettingsLinkRow(
                label = stringResource(R.string.course_quiz),
                icon = R.drawable.ic_learn_quiz,
                onClick = actions.onQuiz,
            )
        }
    }
}
