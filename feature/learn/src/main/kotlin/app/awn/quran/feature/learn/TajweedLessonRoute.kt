package app.awn.quran.feature.learn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.Lesson
import app.awn.quran.feature.learn.course.LearnTracks
import app.awn.quran.feature.learn.data.StepItemKind
import app.awn.quran.feature.learn.data.StepOutcome
import app.awn.quran.feature.learn.session.StepKind
import app.awn.quran.feature.learn.ui.LearnRoute
import app.awn.quran.feature.learn.words.FinishedStep
import kotlinx.coroutines.launch

/**
 * An older tajweed lesson. Opened from the Tajweed track, leaving it marks it read and returns to
 * the track; a course lesson id is sent on to the paged unit.
 */
@Composable
internal fun OneLessonRoute(
    route: LearnRoute.OneLesson,
    content: LearnRouteContent,
    course: CourseRouteContext,
    onRouteChange: (LearnRoute) -> Unit,
    modifier: Modifier,
) {
    val courseContent = course.runtime?.assets?.content
    if (courseContent?.lesson(route.id) != null) {
        LaunchedEffect(route.id) {
            onRouteChange(
                LearnRoute.CourseUnit(route.id, LearnTracks.trackOf(route.id, courseContent)),
            )
        }
        return
    }
    val scope = rememberCoroutineScope()
    val repository = course.dependencies.services.courseRepository
    val track = route.track
    val onBack = {
        if (track != null) {
            scope.launch { repository.finishLesson(route.id, lessonReadStep(route.id)) }
            onRouteChange(LearnRoute.Track(track))
        } else {
            onRouteChange(LearnRoute.Hub)
        }
    }
    LessonRoute(content.lessons.firstOrNull { it.id == route.id }, content, onBack, modifier)
}

// An older lesson has no check, so reading it to the end is its whole outcome.
private fun lessonReadStep(lessonId: String) =
    FinishedStep(StepKind.Lesson, StepItemKind.LESSON, lessonId, null, StepOutcome.SEEN, 0L)

@Composable
private fun LessonRoute(
    lesson: Lesson?,
    content: LearnRouteContent,
    onBackClick: () -> Unit,
    modifier: Modifier,
) {
    if (lesson == null) return
    val examples by exampleLines(lesson, content.language, content.quranText, content.surahName)
    LessonScreen(
        lesson = lesson,
        language = content.language,
        examples = examples,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
private fun exampleLines(
    lesson: Lesson,
    language: LearnLanguage,
    quranText: QuranTextRepository?,
    surahName: (Int) -> String,
): State<List<ExampleLine>> =
    produceState(emptyList(), lesson, language, quranText) {
        value =
            lesson.examples.map { example ->
                val words = quranText?.wordsOfAyah(AyahRef(example.surah, example.ayah)).orEmpty()
                LearnUiMapper.exampleLine(example, language, surahName(example.surah), words)
            }
    }
