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
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.CourseLesson
import app.awn.quran.feature.learn.course.ScreenKind

/** A course lesson read on its own from the path; it asks no question and moves no date. */
@Composable
internal fun CourseLessonScreen(
    lesson: CourseLesson,
    languageCode: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = lesson.text(languageCode)
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = text.title,
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        Column(
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        ) {
            text.screens
                .filter { it.kind == ScreenKind.Reading }
                .forEach { screen ->
                    RichText(screen.title, style = AwnTypography.title)
                    LessonBlocks(screen.blocks)
                }
        }
    }
}
