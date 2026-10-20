package app.awn.quran.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography

/** The field colour that tells the user they are in Learn mode. */
val LearnField = AwnField.Ochre

/**
 * The Learn screen: two figure tiles (days in a row, pages this week), the first lessons, the day's
 * snippet and one primary action, which starts today's revision.
 */
@Composable
fun LearnScreen(
    state: LearnUiState,
    onLessonClick: (String) -> Unit,
    onAllLessonsClick: () -> Unit,
    onProgressClick: () -> Unit,
    onReviseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AwnTheme(field = LearnField) {
        Column(modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                LearnBar(
                    title = stringResource(R.string.learn_title),
                    endLinkLabel = stringResource(R.string.learn_progress_link),
                    onEndLinkClick = onProgressClick,
                )
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    FigureTile(
                        field = AwnField.Green,
                        figure = state.streakDays,
                        caption = stringResource(R.string.learn_streak_caption),
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
                    state.lessons.forEach { lesson ->
                        LessonLine(lesson = lesson, onClick = { onLessonClick(lesson.id) })
                    }
                    TextLink(
                        label = stringResource(R.string.learn_all_lessons),
                        onClick = onAllLessonsClick,
                    )
                }
                state.snippet?.let { SnippetBlock(it) }
            }
            ReviseButton(dueCount = state.ayahsDueToday, onClick = onReviseClick)
        }
    }
}

@Composable
private fun SnippetBlock(snippet: SnippetLine) {
    Column(Modifier.padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.medium)) {
        ArabicText(text = snippet.arabic)
        Text(
            text = snippet.reference,
            style = AwnTypography.small,
            modifier = Modifier.padding(top = AwnSpacing.small),
        )
    }
}

@Composable
private fun ReviseButton(dueCount: Int, onClick: () -> Unit) {
    val label =
        if (dueCount > 0) {
            pluralStringResource(R.plurals.learn_revise, dueCount, dueCount)
        } else {
            stringResource(R.string.learn_revise_none)
        }
    AwnButton(
        label = label,
        onClick = onClick,
        enabled = dueCount > 0,
        modifier = Modifier.fillMaxWidth().padding(AwnSpacing.screenMargin),
    )
}
