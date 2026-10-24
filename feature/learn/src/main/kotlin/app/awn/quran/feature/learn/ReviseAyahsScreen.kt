package app.awn.quran.feature.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill

/** What the buttons of the Revise screen do. [memorise] is null when Hifz has no page to open. */
internal class ReviseActions(
    val onStartClick: () -> Unit,
    val onReviseEarlyClick: () -> Unit,
    val memorise: OpenPageAction?,
)

/**
 * "Revise ayahs" from the Learn hub. With ayahs due it shows the count, the surahs they come from
 * and the next seven days; with none due it says when the next revision is and what to do
 * meanwhile.
 */
@Composable
internal fun ReviseAyahsScreen(
    state: ReviseUiState,
    actions: ReviseActions,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LearnPage(
        title = stringResource(R.string.revision_title),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        if (state.isSomethingDue) {
            DueToday(state, actions.onStartClick)
        } else {
            NothingDue(state, actions)
        }
        if (state.hasUpcoming) {
            UpcomingStrip(state.upcomingDays)
        }
        if (!state.isSomethingDue) {
            Meanwhile(state, actions)
        }
    }
}

@Composable
private fun DueToday(state: ReviseUiState, onStartClick: () -> Unit) {
    LearnHeroCard(
        figure = state.dueCount,
        caption = pluralStringResource(R.plurals.revise_due_caption, state.dueCount),
    )
    AwnButton(
        label = stringResource(R.string.revise_start),
        onClick = onStartClick,
        modifier = Modifier.fillMaxWidth(),
    )
    LearnSectionHeading(stringResource(R.string.revise_due_heading))
    LearnCard { state.dueSurahs.forEach { DueSurahRow(it) } }
}

@Composable
private fun DueSurahRow(surah: DueSurah) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowTall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = surah.name, style = AwnTypography.bodyStrong, color = Awn.field.content)
            Text(
                text = ayahSpan(surah),
                style = AwnTypography.small,
                color = Awn.field.mutedContent,
            )
        }
        Text(
            text = formatCount(surah.dueCount),
            style = AwnTypography.bodyStrong,
            color = Awn.field.content,
        )
    }
}

@Composable
private fun ayahSpan(surah: DueSurah): String =
    if (surah.firstAyah == surah.lastAyah) {
        stringResource(R.string.revise_ayah_single, formatCount(surah.firstAyah))
    } else {
        stringResource(
            R.string.revise_ayah_range,
            formatCount(surah.firstAyah),
            formatCount(surah.lastAyah),
        )
    }

@Composable
private fun NothingDue(state: ReviseUiState, actions: ReviseActions) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(
            text = stringResource(R.string.revise_empty_title),
            style = AwnTypography.lead,
            color = Awn.field.content,
        )
        Text(
            text = nextRevisionSentence(state),
            style = AwnTypography.body,
            color = Awn.field.mutedContent,
        )
    }
}

@Composable
private fun nextRevisionSentence(state: ReviseUiState): String {
    val day = state.nextRevisionDay ?: return stringResource(R.string.revise_empty_none)
    return pluralStringResource(
        R.plurals.revise_empty_next,
        state.nextRevisionCount,
        day,
        formatCount(state.nextRevisionCount),
    )
}

@Composable
private fun UpcomingStrip(days: List<UpcomingDay>) {
    LearnSectionHeading(stringResource(R.string.revise_upcoming_heading))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        days.forEach { UpcomingDayCell(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun UpcomingDayCell(day: UpcomingDay, modifier: Modifier) {
    val hasAyahs = day.dueCount > 0
    val fill = if (hasAyahs) Awn.field.buttonFill else Awn.field.softFill
    val textColour = if (hasAyahs) Awn.field.onButton else Awn.field.mutedContent
    Column(
        modifier =
            modifier
                .clip(AwnShapes.wordBlock)
                .background(fill)
                .padding(vertical = AwnSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.label,
            style = AwnTypography.caption,
            color = textColour,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = formatCount(day.dueCount),
            style = AwnTypography.bodyStrong,
            color = textColour,
        )
    }
}

/** Both rows are left out when their destination is missing, so nothing leads nowhere. */
@Composable
private fun Meanwhile(state: ReviseUiState, actions: ReviseActions) {
    val memorise = actions.memorise
    val canReviseEarly = state.nextRevisionCount > 0
    if (memorise == null && !canReviseEarly) return
    LearnSectionHeading(stringResource(R.string.revise_meanwhile_heading))
    LearnCard {
        if (memorise != null) {
            SettingsLinkRow(
                label = stringResource(R.string.revise_memorise),
                value = stringResource(R.string.words_hifz_today, memorise.page),
                onClick = memorise.onClick,
            )
        }
        if (canReviseEarly) {
            SettingsLinkRow(
                label = stringResource(R.string.revise_early),
                value =
                    pluralStringResource(
                        R.plurals.revise_early_detail,
                        state.nextRevisionCount,
                        formatCount(state.nextRevisionCount),
                    ),
                onClick = actions.onReviseEarlyClick,
            )
        }
    }
}
