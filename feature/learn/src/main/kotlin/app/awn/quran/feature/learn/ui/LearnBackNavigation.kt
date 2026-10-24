package app.awn.quran.feature.learn.ui

import app.awn.quran.feature.learn.course.LearnTrack

/** The tab opens on the first-open choices until the course has started, then on the hub. */
internal fun learnStartRoute(courseStarted: Boolean): LearnRoute =
    if (courseStarted) LearnRoute.Hub else LearnRoute.FirstOpen

/** The Learn tab's main page is the only one that shows the bottom tabs. */
internal fun isLearnTabRoot(route: LearnRoute?): Boolean =
    route == null || route == LearnRoute.Hub || route == LearnRoute.FirstOpen

/** The screen Back returns to, or null when Back leaves the tab. */
internal fun learnBackFrom(route: LearnRoute): LearnRoute? =
    when (route) {
        LearnRoute.Hub,
        LearnRoute.FirstOpen -> null
        is LearnRoute.WordPage -> route.from ?: LearnRoute.WordList
        is LearnRoute.WordAyahs -> route.from
        is LearnRoute.SetPage,
        is LearnRoute.PageWords -> LearnRoute.Track(LearnTrack.Words)
        is LearnRoute.CourseUnit -> learnBackFromUnit(route)
        else -> LearnRoute.Hub
    }

private fun learnBackFromUnit(route: LearnRoute.CourseUnit): LearnRoute {
    if (route.isOpenedFromWords) return LearnRoute.Track(LearnTrack.Words)
    return route.track?.let { LearnRoute.Track(it) } ?: LearnRoute.Hub
}
