package app.awn.quran.feature.learn

import androidx.compose.runtime.mutableStateOf
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.ui.LearnRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The word list that the Quran page opens inside the Learn tab must not stay as the Learn tab's
 * screen once the user goes back to the Quran: the next tap on Learn shows Learn's home.
 */
class RequestedPageWordsTest {

    private val routeState = mutableStateOf<LearnRoute?>(LearnRoute.PageWords(42))
    private val requestedPage = mutableStateOf<Int?>(42)
    private var leftToQuran = false
    private val words = RequestedPageWords(routeState, requestedPage) { leftToQuran = true }

    @Test
    fun `leaving the requested page words returns to the Quran and resets Learn to its home`() {
        words.leave()

        assertTrue(leftToQuran)
        assertEquals(LearnRoute.Hub, routeState.value)
        assertNull(requestedPage.value)
    }

    @Test
    fun `back to the words track from the requested page words leaves the same way`() {
        words.changeRoute(LearnRoute.Track(LearnTrack.Words))

        assertTrue(leftToQuran)
        assertEquals(LearnRoute.Hub, routeState.value)
    }

    @Test
    fun `opening a word from the page words stays in Learn`() {
        val wordPage = LearnRoute.WordPage(rank = 7, from = LearnRoute.PageWords(42))

        words.changeRoute(wordPage)

        assertEquals(wordPage, routeState.value)
        assertEquals(false, leftToQuran)
    }
}
