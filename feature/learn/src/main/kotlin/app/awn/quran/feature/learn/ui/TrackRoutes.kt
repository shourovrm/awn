package app.awn.quran.feature.learn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.learn.course.CourseLesson
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.LearnTracks
import app.awn.quran.feature.learn.data.StepItemKind
import app.awn.quran.feature.learn.data.StepOutcome
import app.awn.quran.feature.learn.session.StepKind
import app.awn.quran.feature.learn.words.FinishedStep
import java.time.Instant
import kotlinx.coroutines.launch

/** The track pages and the units opened from them; Quran words reuses the hub's state. */
@Composable
internal fun TrackRoutes(
    route: LearnRoute,
    context: CourseScreenContext,
    hubActions: HubActions,
    dueAyahs: List<AyahRef>,
    modifier: Modifier,
) {
    when (route) {
        is LearnRoute.Track ->
            if (route.track == LearnTrack.Words) {
                HubRoute(context, dueAyahs) {
                    WordsTrackScreen(it, hubActions, context::goHub, modifier)
                }
            } else {
                TrackRoute(route.track, context, modifier)
            }
        is LearnRoute.CourseUnit -> CourseUnitRoute(route, context, modifier)
        else -> error("$route is not a track screen.")
    }
}

@Composable
private fun TrackRoute(track: LearnTrack, context: CourseScreenContext, modifier: Modifier) {
    val input = trackInput(context.runtime.lookups, context.snapshot, context.choices)
    TrackScreen(
        summary = LearnTracks.summary(track, input),
        units = LearnTracks.units(track, input),
        onOpen = { unit -> context.onRouteChange(LearnRoute.CourseUnit(unit.id, track)) },
        onBack = context::goHub,
        modifier = modifier,
    )
}

@Composable
private fun CourseUnitRoute(
    route: LearnRoute.CourseUnit,
    context: CourseScreenContext,
    modifier: Modifier,
) {
    val lesson = context.runtime.assets.content.lesson(route.id) ?: return
    val track = route.track
    val input = trackInput(context.runtime.lookups, context.snapshot, context.choices)
    val place = track?.let {
        val number = LearnTracks.units(it, input).firstOrNull { unit -> unit.id == lesson.id }
        UnitPlace(it, number?.number ?: 0, LearnTracks.unitAfter(it, lesson.id, input))
    }
    val openedAtMillis = remember(lesson.id) { Instant.now().toEpochMilli() }
    val backRoute = track?.let { LearnRoute.Track(it) } ?: LearnRoute.Hub
    // The unit is drawn again for the next one, so its pages and answers start fresh.
    key(lesson.id) {
        UnitScreen(
            lesson = lesson,
            languageCode = context.dependencies.languageCode,
            place = place,
            actions =
                UnitActions(
                    onBack = { context.onRouteChange(backRoute) },
                    onFinished = { rightCount ->
                        context.scope.launch {
                            finishUnit(context, lesson, rightCount, openedAtMillis)
                        }
                    },
                    onNextUnit = { next ->
                        context.onRouteChange(
                            LearnRoute.CourseUnit(next.id, track ?: LearnTrack.Reading),
                        )
                    },
                ),
            modifier = modifier,
        )
    }
}

/** Records the unit as done, with whether every check was answered right the first time. */
private suspend fun finishUnit(
    context: CourseScreenContext,
    lesson: CourseLesson,
    rightCount: Int,
    openedAtMillis: Long,
) {
    val checkCount = lesson.text(context.dependencies.languageCode).checks.size
    val outcome =
        when {
            checkCount == 0 -> StepOutcome.SEEN
            rightCount == checkCount -> StepOutcome.CORRECT
            else -> StepOutcome.WRONG
        }
    val step =
        FinishedStep(
            StepKind.Lesson,
            StepItemKind.LESSON,
            lesson.id,
            null,
            outcome,
            Instant.now().toEpochMilli() - openedAtMillis,
        )
    context.services.courseRepository.finishLesson(lesson.id, step)
}
