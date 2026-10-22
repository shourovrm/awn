@file:Suppress("TooManyFunctions") // One small composable per part of the hub.

package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.PathRow
import app.awn.quran.feature.learn.course.PathState
import app.awn.quran.feature.learn.formatCount
import app.awn.quran.feature.learn.session.TodayPlan
import kotlin.math.roundToInt

/** Everything the hub shows, already worked out. */
internal data class HubState(
    val plan: TodayPlan,
    val sessionMinutes: Int,
    val path: List<PathRow>,
    val lessonTitles: Map<String, String>,
    /** For each lesson that follows a set: the word that brought it and its meaning. */
    val triggerMeanings: Map<String, Pair<WordKey, String>>,
    val wordsMet: Int,
    val wordsKnown: Int,
    val coverage: Double,
    val dueAyahs: Int,
    val meanings: MeaningsState,
)

/** Whether the word meanings the course needs are on the phone. */
internal enum class MeaningsState {
    Ready,
    Missing,
    Downloading,
}

// One callback per row of the hub.
@Suppress("LongParameterList")
internal class HubActions(
    val onStart: () -> Unit,
    val onProgress: () -> Unit,
    val onWordList: () -> Unit,
    val onQuiz: () -> Unit,
    val onTajweed: () -> Unit,
    val onReadingProgress: () -> Unit,
    val onReviseAyahs: () -> Unit,
    val onSessionMinutes: (Int) -> Unit,
    val onSources: () -> Unit,
    val onDownloadMeanings: () -> Unit,
    val onLesson: (String) -> Unit,
    val onChangeChoices: () -> Unit,
)

private val SESSION_MINUTES = listOf(5, 10, 15)
private const val PERCENT = 100

/** The Learn tab: today's work, the course path, the progress and the other things to do. */
@Composable
internal fun CourseHub(
    state: HubState,
    actions: HubActions,
    modifier: Modifier = Modifier,
    barActions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(title = stringResource(R.string.learn_title), actions = barActions)
        Column(
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            TodayCard(state, actions)
            Heading(stringResource(R.string.course_heading))
            state.path.forEach { PathRowView(it, state, actions) }
            Heading(stringResource(R.string.course_progress_heading))
            ProgressCard(state)
            SettingsLinkRow(
                label =
                    stringResource(
                        R.string.course_coverage_title,
                        (state.coverage * PERCENT).roundToInt(),
                    ),
                value = stringResource(R.string.course_coverage_detail),
                icon = R.drawable.ic_learn_trend,
                onClick = actions.onProgress,
            )
            Heading(stringResource(R.string.course_more_heading))
            MoreRows(state, actions)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = AwnTypography.bodyStrong,
        color = Awn.field.content,
        modifier = Modifier.padding(top = AwnSpacing.large),
    )
}

@Composable
private fun TodayCard(state: HubState, actions: HubActions) {
    val field = Awn.field
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AwnShapes.control)
                .background(field.barBackground)
                .padding(AwnSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        when (state.meanings) {
            MeaningsState.Ready -> TodayReady(state, actions)
            else -> MeaningsNeeded(state.meanings, actions.onDownloadMeanings)
        }
    }
}

@Composable
private fun TodayReady(state: HubState, actions: HubActions) {
    val field = Awn.field
    val plan = state.plan
    if (plan.isEmpty) {
        Text(
            text = stringResource(R.string.course_today_done_heading),
            style = AwnTypography.lead,
            color = field.onBar,
        )
        Text(
            text = stringResource(R.string.course_today_done),
            style = AwnTypography.body,
            color = field.onBar,
        )
        return
    }
    Text(
        text = stringResource(R.string.course_today_heading, state.sessionMinutes),
        style = AwnTypography.lead,
        color = field.onBar,
    )
    Text(text = planSummary(plan), style = AwnTypography.body, color = field.onBar)
    AwnButton(
        label = stringResource(R.string.course_start),
        onClick = actions.onStart,
        modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.small),
        containerColor = field.onBar,
        contentColor = field.barBackground,
    )
}

@Composable
private fun MeaningsNeeded(meanings: MeaningsState, onDownload: () -> Unit) {
    val field = Awn.field
    Text(
        text = stringResource(R.string.course_meanings_title),
        style = AwnTypography.lead,
        color = field.onBar,
    )
    if (meanings == MeaningsState.Downloading) {
        Text(
            text = stringResource(R.string.course_meanings_waiting),
            style = AwnTypography.body,
            color = field.onBar,
        )
    } else {
        AwnButton(
            label = stringResource(R.string.course_meanings_download),
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
            containerColor = field.onBar,
            contentColor = field.barBackground,
        )
    }
}

@Composable
private fun planSummary(plan: TodayPlan): String {
    val parts = mutableListOf<String>()
    if (plan.reviewCount > 0) {
        parts +=
            pluralStringResource(R.plurals.course_today_reviews, plan.reviewCount, plan.reviewCount)
    }
    if (plan.newWordCount > 0)
        parts +=
            pluralStringResource(R.plurals.course_today_new, plan.newWordCount, plan.newWordCount)
    if (plan.lessonIds.isNotEmpty()) parts += stringResource(R.string.course_today_lesson)
    return parts.joinToString(", ")
}

@Composable
private fun PathRowView(row: PathRow, state: HubState, actions: HubActions) {
    when (row) {
        is PathRow.Folded -> FoldedRow(row, state)
        is PathRow.SetStep -> SetRow(row)
        is PathRow.LessonStep -> LessonRow(row, state, actions)
        is PathRow.Later ->
            StepRow(
                PathState.Upcoming,
                stringResource(R.string.course_later),
                stringResource(R.string.course_later_from, row.fromSet),
                false,
                null,
            )
    }
}

@Composable
private fun FoldedRow(row: PathRow.Folded, state: HubState) {
    val sets =
        row.sets?.let {
            if (it.first == it.last) stringResource(R.string.course_folded_set, it.first)
            else stringResource(R.string.course_folded_sets, it.first, it.last)
        }
    val lessons = row.lessonIds.map { state.lessonTitles[it].orEmpty() }
    val title = (listOfNotNull(sets) + lessons).joinToString(", ")
    StepRow(
        PathState.Done,
        title,
        pluralStringResource(R.plurals.course_folded_detail, row.stepCount, row.stepCount),
        false,
        null,
    )
}

@Composable
private fun SetRow(row: PathRow.SetStep) {
    val detail =
        when {
            row.state == PathState.Done ->
                pluralStringResource(R.plurals.course_set_known, row.knownCount, row.knownCount)
            row.metCount > 0 ->
                pluralStringResource(
                    R.plurals.course_set_left,
                    LEARN_SET_SIZE - row.metCount,
                    LEARN_SET_SIZE - row.metCount,
                )
            else -> null
        }
    StepRow(
        row.state,
        stringResource(R.string.course_set_title, row.number),
        detail,
        row.isToday,
        null,
    )
}

@Composable
private fun LessonRow(row: PathRow.LessonStep, state: HubState, actions: HubActions) {
    val title =
        stringResource(R.string.course_lesson_title, state.lessonTitles[row.lessonId].orEmpty())
    val trigger = state.triggerMeanings[row.lessonId]
    val detail =
        if (trigger != null && row.triggerSet != null) {
            stringResource(
                R.string.course_lesson_reason,
                row.triggerSet,
                "{{${trigger.first}}}",
                trigger.second,
            )
        } else {
            stringResource(R.string.course_basics)
        }
    StepRow(row.state, title, detail, row.isToday, onClick = { actions.onLesson(row.lessonId) })
}

private const val LEARN_SET_SIZE = 10

/**
 * One step of the path: a marker, the title with a detail, and "Today" when it is in today's plan.
 */
@Composable
private fun StepRow(
    state: PathState,
    title: String,
    detail: String?,
    isToday: Boolean,
    onClick: (() -> Unit)?,
) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.settingsRowTall)
                .clip(AwnShapes.control)
                .then(
                    if (state == PathState.Next) Modifier.background(field.softFill) else Modifier,
                )
                .then(
                    if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                    else Modifier,
                )
                .padding(horizontal = AwnSpacing.small, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        StepMarker(state)
        Column(modifier = Modifier.weight(1f)) {
            RichText(title, style = AwnTypography.bodyStrong)
            detail?.let { RichText(it, style = AwnTypography.small, color = field.mutedContent) }
        }
        if (isToday) {
            Text(
                text = stringResource(R.string.course_today_tag),
                style = AwnTypography.bodyStrong,
                color = field.content,
            )
        }
    }
}

@Composable
private fun StepMarker(state: PathState) {
    val field = Awn.field
    val fill =
        when (state) {
            PathState.Done -> field.buttonFill
            PathState.Next -> field.content
            PathState.Upcoming -> field.softFill
        }
    Box(
        modifier = Modifier.size(AwnSpacing.iconSize).clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (state == PathState.Done) {
            Icon(
                painter = painterResource(UiR.drawable.ic_ui_check),
                contentDescription = null,
                tint = field.onButton,
                modifier = Modifier.size(AwnSpacing.smallIconSize),
            )
        }
    }
}

@Composable
private fun ProgressCard(state: HubState) {
    val colors = paperColors()
    val knownFraction = if (state.wordsMet == 0) 0f else state.wordsKnown.toFloat() / state.wordsMet
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AwnShapes.control)
                .background(colors.background)
                .padding(AwnSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(
            text = formatCount(state.wordsKnown),
            style = AwnTypography.title,
            color = colors.content,
        )
        Text(
            text =
                pluralStringResource(R.plurals.course_words_known, state.wordsMet, state.wordsMet),
            style = AwnTypography.body,
            color = colors.content,
        )
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = AwnSpacing.small)
                    .clip(AwnShapes.wordBlock)
                    .background(colors.softFill),
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth(knownFraction)
                        .heightIn(min = AwnSpacing.small)
                        .background(colors.buttonFill),
            )
        }
        Text(
            text = stringResource(R.string.course_legend_known, state.wordsKnown),
            style = AwnTypography.small,
            color = colors.content,
        )
        Text(
            text = stringResource(R.string.course_legend_met, state.wordsMet - state.wordsKnown),
            style = AwnTypography.small,
            color = colors.content,
        )
    }
}

@Composable
private fun MoreRows(state: HubState, actions: HubActions) {
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
    SettingsLinkRow(
        label = stringResource(R.string.course_tajweed),
        icon = R.drawable.ic_learn_lessons,
        onClick = actions.onTajweed,
    )
    SettingsLinkRow(
        label = stringResource(R.string.course_revise_ayahs),
        value = pluralStringResource(R.plurals.course_ayahs_due, state.dueAyahs, state.dueAyahs),
        icon = R.drawable.ic_learn_quiz,
        onClick = actions.onReviseAyahs,
    )
    SettingsLinkRow(
        label = stringResource(R.string.course_reading_progress),
        icon = R.drawable.ic_learn_trend,
        onClick = actions.onReadingProgress,
    )
    AwnChoiceRow(
        label = stringResource(R.string.course_session_length),
        options = SESSION_MINUTES,
        selected = state.sessionMinutes,
        optionLabel = { stringResource(R.string.course_minutes, it) },
        onSelect = actions.onSessionMinutes,
    )
    SettingsLinkRow(
        label = stringResource(R.string.course_change_choices),
        onClick = actions.onChangeChoices,
    )
    SettingsLinkRow(label = stringResource(R.string.course_sources), onClick = actions.onSources)
}
