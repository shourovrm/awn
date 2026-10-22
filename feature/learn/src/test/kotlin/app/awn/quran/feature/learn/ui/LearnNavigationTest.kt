package app.awn.quran.feature.learn.ui

import app.awn.quran.feature.learn.course.LearnTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the Learn tab opens and where Back goes inside it. The course screens were written but no
 * route reached them; the tab now opens on the first-open screen until the course has started, then
 * on the hub (the approved "path + today card" layout), and Back walks towards the hub.
 */
class LearnNavigationTest {

    @Test
    fun aCourseThatHasNotStartedOpensOnTheFirstOpenScreen() {
        assertEquals(LearnRoute.FirstOpen, learnStartRoute(courseStarted = false))
    }

    @Test
    fun aStartedCourseOpensOnTheHub() {
        assertEquals(LearnRoute.Hub, learnStartRoute(courseStarted = true))
    }

    @Test
    fun backOnTheHubOrTheFirstOpenScreenLeavesTheTab() {
        assertNull(learnBackFrom(LearnRoute.Hub))
        assertNull(learnBackFrom(LearnRoute.FirstOpen))
    }

    @Test
    fun backFromAWordPageReturnsToTheWordList() {
        assertEquals(LearnRoute.WordList, learnBackFrom(LearnRoute.WordPage(rank = 12)))
    }

    @Test
    fun backFromEveryOtherScreenReturnsToTheHub() {
        val others =
            listOf(
                LearnRoute.Session(isOwnQuiz = false),
                LearnRoute.Session(isOwnQuiz = true),
                LearnRoute.QuizStart,
                LearnRoute.WordList,
                LearnRoute.Sources,
                LearnRoute.Progress,
                LearnRoute.Revision,
                LearnRoute.OneLesson("basics-01"),
                LearnRoute.Track(LearnTrack.Reading),
                LearnRoute.CourseUnit("letters-01", track = null),
            )

        others.forEach { route ->
            assertEquals(route.toString(), LearnRoute.Hub, learnBackFrom(route))
        }
    }

    @Test
    fun backFromAUnitOpenedFromATrackReturnsToThatTrack() {
        assertEquals(
            LearnRoute.Track(LearnTrack.Reading),
            learnBackFrom(LearnRoute.CourseUnit("letters-02", LearnTrack.Reading)),
        )
        assertEquals(
            LearnRoute.Track(LearnTrack.Tajweed),
            learnBackFrom(LearnRoute.OneLesson("makharij", LearnTrack.Tajweed)),
        )
    }
}
