package app.awn.quran.feature.learn.ui

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

    /** The existing list of tajweed lessons. */
    data object Lessons : LearnRoute

    data object Revision : LearnRoute

    /** An older tajweed lesson; [track] is set when it was opened from a track page. */
    data class OneLesson(val id: String, val track: LearnTrack? = null) : LearnRoute

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
        is LearnRoute.OneLesson -> route.track?.let { LearnRoute.Track(it) } ?: LearnRoute.Hub
        else -> LearnRoute.Hub
    }
