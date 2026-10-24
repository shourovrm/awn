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

    /** A word's page; [from] is the screen Back returns to, the word list when null. */
    data class WordPage(val rank: Int, val from: LearnRoute? = null) : LearnRoute

    /** The ten words of one set of the Quran words track. */
    data class SetPage(val number: Int) : LearnRoute

    /** Every ayah the word at [rank] appears in; Back returns to [from]. */
    data class WordAyahs(val rank: Int, val from: WordPage) : LearnRoute

    /** Every distinct word on one Quran page, with the user's progress on each. */
    data class PageWords(val page: Int) : LearnRoute

    data object Sources : LearnRoute

    /** The word course's progress: words known and the share of the Quran they cover. */
    data object Progress : LearnRoute

    /** The existing reading progress page: streak, pages and memorised ayahs. */
    data object ReadingProgress : LearnRoute

    data object Revision : LearnRoute

    /** The numbered units of one track. */
    data class Track(val track: LearnTrack) : LearnRoute

    /**
     * A course unit, page by page; [track] is the track it was opened from, if any. A grammar
     * lesson linked from the Quran words track has [isOpenedFromWords] set, so Back returns there
     * and not to the grammar list.
     */
    data class CourseUnit(
        val id: String,
        val track: LearnTrack?,
        val isOpenedFromWords: Boolean = false,
    ) : LearnRoute
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
        is LearnRoute.WordPage -> encodeWordPage(route)
        is LearnRoute.WordAyahs ->
            listOf("word_ayahs", route.rank, encodeLearnRoute(route.from))
                .joinToString(ROUTE_SEPARATOR)
        is LearnRoute.SetPage -> listOf("set_page", route.number).joinToString(ROUTE_SEPARATOR)
        is LearnRoute.PageWords -> listOf("page_words", route.page).joinToString(ROUTE_SEPARATOR)
        is LearnRoute.Track -> listOf("track", route.track.name).joinToString(ROUTE_SEPARATOR)
        is LearnRoute.CourseUnit -> encodeUnit(route)
        else -> SIMPLE_ROUTES.entries.first { it.value == route }.key
    }

internal fun decodeLearnRoute(saved: String): LearnRoute? {
    val parts = saved.split(ROUTE_SEPARATOR, limit = 3)
    return SIMPLE_ROUTES[parts[0]] ?: decodeRouteWithValues(parts)
}

private fun decodeRouteWithValues(parts: List<String>): LearnRoute? =
    when (parts[0]) {
        "word_page" -> decodeWordPage(parts)
        "word_ayahs" -> decodeWordAyahs(parts)
        "set_page" -> parts.getOrNull(1)?.toIntOrNull()?.let { LearnRoute.SetPage(it) }
        "page_words" -> parts.getOrNull(1)?.toIntOrNull()?.let { LearnRoute.PageWords(it) }
        "track" -> parts.getOrNull(1)?.let { LearnRoute.Track(LearnTrack.valueOf(it)) }
        "unit" -> decodeUnit(parts, isOpenedFromWords = false)
        "unit_from_words" -> decodeUnit(parts, isOpenedFromWords = true)
        else -> null
    }

private fun encodeUnit(route: LearnRoute.CourseUnit): String {
    val name = if (route.isOpenedFromWords) "unit_from_words" else "unit"
    return listOf(name, route.track?.name ?: NO_TRACK, route.id).joinToString(ROUTE_SEPARATOR)
}

private fun decodeUnit(parts: List<String>, isOpenedFromWords: Boolean): LearnRoute? {
    val trackName = parts.getOrNull(1)
    val unitId = parts.getOrNull(2)
    val track = trackName?.takeUnless { it == NO_TRACK }?.let { LearnTrack.valueOf(it) }
    return if (trackName == null || unitId == null) null
    else LearnRoute.CourseUnit(unitId, track, isOpenedFromWords)
}

// The screen Back returns to is saved as the rest of the text after the rank, which the split with
// a limit of three keeps whole.
private fun encodeWordPage(route: LearnRoute.WordPage): String {
    val parts = mutableListOf("word_page", route.rank.toString())
    route.from?.let { parts += encodeLearnRoute(it) }
    return parts.joinToString(ROUTE_SEPARATOR)
}

private fun decodeWordPage(parts: List<String>): LearnRoute? {
    val rank = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return LearnRoute.WordPage(rank, parts.getOrNull(2)?.let(::decodeLearnRoute))
}

private fun decodeWordAyahs(parts: List<String>): LearnRoute? {
    val rank = parts.getOrNull(1)?.toIntOrNull() ?: return null
    val from = parts.getOrNull(2)?.let(::decodeLearnRoute) as? LearnRoute.WordPage
    return LearnRoute.WordAyahs(rank, from ?: LearnRoute.WordPage(rank))
}
