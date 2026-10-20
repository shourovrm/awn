package app.awn.quran.feature.learn

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
import app.awn.quran.core.ui.theme.AwnTheme

/** Every lesson as a poster line. */
@Composable
fun LessonsListScreen(
    lessons: List<LessonSummary>,
    onLessonClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AwnTheme(field = LearnField) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            LearnBar(
                title = stringResource(R.string.lessons_title),
                startLinkLabel = stringResource(R.string.back),
                onStartLinkClick = onBackClick,
            )
            Column(
                modifier = Modifier.padding(AwnSpacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            ) {
                lessons.forEach { lesson ->
                    LessonLine(lesson = lesson, onClick = { onLessonClick(lesson.id) })
                }
            }
        }
    }
}
