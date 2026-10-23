package app.awn.quran.feature.learn.ui

import androidx.compose.runtime.saveable.Saver
import app.awn.quran.feature.learn.course.LearnTrack

/** Where the user is inside the Learn tab. */
internal sealed interface LearnRoute {
    data object FirstOpen : LearnRoute

    data object Hub : LearnRoute

    /** The letters and daily time choices of the first screen, opened again from the hub. */
    data object CourseChoices : LearnRoute

    /** [isOwnQuiz] is true for a quiz the user started, which never changes the schedule. */
    data class Session(val isOwnQuiz: Boolean) : LearnRoute

    data object QuizStart : LearnRoute

    data object WordList : LearnRoute

    data class WordPage(val rank: Int) : LearnRoute

    data object Sources : LearnRoute

    /** The word course's progress: words known and the share of the Quran they cover. */
    data object Progress : LearnRoute

    /** The existing reading progress page: streak, pages and memorised ayahs. */
    data object ReadingProgress : LearnRoute

    data object Revision : LearnRoute

    /** The numbered units of one track. */
    data class Track(val track: LearnTrack) : LearnRoute

    /** A course unit, page by page; [track] is the track it was opened from, if any. */
    data class CourseUnit(val id: String, val track: LearnTrack?) : LearnRoute
}

/** The tab opens on the first-open choices until the course has started, then on the hub. */
internal fun learnStartRoute(courseStarted: Boolean): LearnRoute =
    if (courseStarted) LearnRoute.Hub else LearnRoute.FirstOpen

/** The screen Back returns to, or null when Back leaves the tab. */
internal fun learnBackFrom(route: LearnRoute): LearnRoute? =
    when (route) {
        LearnRoute.Hub,
        LearnRoute.FirstOpen -> null
        is LearnRoute.WordPage -> LearnRoute.WordList
        is LearnRoute.CourseUnit -> route.track?.let { LearnRoute.Track(it) } ?: LearnRoute.Hub
        else -> LearnRoute.Hub
    }

/**
 * Keeps the route across a turned screen or a recreated activity. A route is saved as one text: its
 * name, then the track and the unit id where it has them. A running session cannot be saved (its
 * questions and answers live in a controller), so it comes back as the hub.
 */
internal val LearnRouteSaver: Saver<LearnRoute?, String> =
    Saver(
        save = { route -> route?.let(::encodeLearnRoute) },
        restore = { saved -> decodeLearnRoute(saved) },
    )

private const val ROUTE_SEPARATOR = "|"
private const val NO_TRACK = "-"

/** The routes that carry no values, by the name they are saved under. */
private val SIMPLE_ROUTES: Map<String, LearnRoute> =
    mapOf(
        "first_open" to LearnRoute.FirstOpen,
        "hub" to LearnRoute.Hub,
        "course_choices" to LearnRoute.CourseChoices,
        "quiz_start" to LearnRoute.QuizStart,
        "word_list" to LearnRoute.WordList,
        "sources" to LearnRoute.Sources,
        "progress" to LearnRoute.Progress,
        "reading_progress" to LearnRoute.ReadingProgress,
        "revision" to LearnRoute.Revision,
    )

internal fun encodeLearnRoute(route: LearnRoute): String =
    when (route) {
        is LearnRoute.Session -> "hub"
        is LearnRoute.WordPage -> listOf("word_page", route.rank).joinToString(ROUTE_SEPARATOR)
        is LearnRoute.Track -> listOf("track", route.track.name).joinToString(ROUTE_SEPARATOR)
        is LearnRoute.CourseUnit ->
            listOf("unit", route.track?.name ?: NO_TRACK, route.id).joinToString(ROUTE_SEPARATOR)
        else -> SIMPLE_ROUTES.entries.first { it.value == route }.key
    }

internal fun decodeLearnRoute(saved: String): LearnRoute? {
    val parts = saved.split(ROUTE_SEPARATOR, limit = 3)
    return SIMPLE_ROUTES[parts[0]] ?: decodeRouteWithValues(parts)
}

private fun decodeRouteWithValues(parts: List<String>): LearnRoute? =
    when (parts[0]) {
        "word_page" -> parts.getOrNull(1)?.toIntOrNull()?.let { LearnRoute.WordPage(it) }
        "track" -> parts.getOrNull(1)?.let { LearnRoute.Track(LearnTrack.valueOf(it)) }
        "unit" -> decodeUnit(parts)
        else -> null
    }

private fun decodeUnit(parts: List<String>): LearnRoute? {
    val trackName = parts.getOrNull(1)
    val unitId = parts.getOrNull(2)
    val track = trackName?.takeUnless { it == NO_TRACK }?.let { LearnTrack.valueOf(it) }
    return if (trackName == null || unitId == null) null else LearnRoute.CourseUnit(unitId, track)
}
