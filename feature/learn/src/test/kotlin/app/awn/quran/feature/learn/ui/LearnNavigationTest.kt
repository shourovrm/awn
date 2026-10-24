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
    fun backFromAWordsAyahsReturnsToItsWordPage() {
        val wordPage = LearnRoute.WordPage(rank = 12, from = LearnRoute.SetPage(2))
        assertEquals(wordPage, learnBackFrom(LearnRoute.WordAyahs(rank = 12, from = wordPage)))
    }

    @Test
    fun aWordsAyahsRouteIsSavedAndRestoredWithItsWordPage() {
        val route =
            LearnRoute.WordAyahs(rank = 12, from = LearnRoute.WordPage(12, LearnRoute.SetPage(2)))

        assertEquals(route, decodeLearnRoute(encodeLearnRoute(route)))
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
            learnBackFrom(
                LearnRoute.CourseUnit("tajweed-11-where-letters-are-made", LearnTrack.Tajweed),
            ),
        )
    }

    @Test
    fun backFromTheNewWordsScreensReturnsToWhereTheUserCameFrom() {
        assertEquals(
            LearnRoute.PageWords(25),
            learnBackFrom(LearnRoute.WordPage(rank = 12, from = LearnRoute.PageWords(25))),
        )
        assertEquals(
            LearnRoute.SetPage(3),
            learnBackFrom(LearnRoute.WordPage(rank = 12, from = LearnRoute.SetPage(3))),
        )
        assertEquals(LearnRoute.Track(LearnTrack.Words), learnBackFrom(LearnRoute.SetPage(3)))
        assertEquals(LearnRoute.Track(LearnTrack.Words), learnBackFrom(LearnRoute.PageWords(25)))
    }

    @Test
    fun routesSurviveSavingForATurnedScreen() {
        val routes =
            listOf(
                LearnRoute.FirstOpen,
                LearnRoute.Hub,
                LearnRoute.WordPage(rank = 12),
                LearnRoute.WordPage(rank = 12, from = LearnRoute.SetPage(3)),
                LearnRoute.WordPage(rank = 12, from = LearnRoute.PageWords(25)),
                LearnRoute.SetPage(3),
                LearnRoute.PageWords(25),
                LearnRoute.Track(LearnTrack.Tajweed),
                LearnRoute.CourseUnit("letters-01", LearnTrack.Reading),
                LearnRoute.CourseUnit("letters-01", track = null),
                LearnRoute.CourseUnit("grammar-01", LearnTrack.Grammar, isOpenedFromWords = true),
            )
        for (route in routes) {
            assertEquals(route, decodeLearnRoute(encodeLearnRoute(route)))
        }
    }

    @Test
    fun aRunningSessionComesBackAsTheHub() {
        val saved = encodeLearnRoute(LearnRoute.Session(isOwnQuiz = false))
        assertEquals(LearnRoute.Hub, decodeLearnRoute(saved))
    }

    @Test
    fun backFromAGrammarLessonOpenedFromTheWordsTrackReturnsToTheWordsTrack() {
        val lesson =
            LearnRoute.CourseUnit("grammar-01", LearnTrack.Grammar, isOpenedFromWords = true)
        assertEquals(LearnRoute.Track(LearnTrack.Words), learnBackFrom(lesson))
    }

    @Test
    fun backFromAGrammarLessonOpenedFromTheGrammarListReturnsToTheList() {
        val lesson = LearnRoute.CourseUnit("grammar-01", LearnTrack.Grammar)
        assertEquals(LearnRoute.Track(LearnTrack.Grammar), learnBackFrom(lesson))
    }

    @Test
    fun onlyTheHubAndTheFirstOpenScreenAreTabRoots() {
        assertEquals(true, isLearnTabRoot(null))
        assertEquals(true, isLearnTabRoot(LearnRoute.Hub))
        assertEquals(true, isLearnTabRoot(LearnRoute.FirstOpen))
        assertEquals(false, isLearnTabRoot(LearnRoute.Track(LearnTrack.Grammar)))
        assertEquals(false, isLearnTabRoot(LearnRoute.SetPage(1)))
        assertEquals(false, isLearnTabRoot(LearnRoute.WordList))
    }
}
