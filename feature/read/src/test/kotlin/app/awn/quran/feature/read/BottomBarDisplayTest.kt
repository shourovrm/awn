package app.awn.quran.feature.read

import app.awn.quran.feature.read.quran.BottomBarDisplay
import app.awn.quran.feature.read.quran.bottomBarDisplay
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The user's rule (2026-10-22): a tap on the page hides the bars as before, but a bar that is busy
 * (audio playing or paused, or reciting) does not vanish and does not stay over the text: it
 * shrinks to a small corner button that shows what is going on, and a tap on that button brings the
 * full bar back. Ending a recitation stays on the bar's Stop, so a page tap never ends it.
 */
class BottomBarDisplayTest {

    @Test
    fun anIdleBarFollowsTheOtherBars() {
        assertEquals(BottomBarDisplay.Full, bottomBarDisplay(isBusy = false, areBarsVisible = true))
        assertEquals(
            BottomBarDisplay.None,
            bottomBarDisplay(isBusy = false, areBarsVisible = false),
        )
    }

    @Test
    fun aBusyBarIsFullWhileTheBarsShow() {
        assertEquals(BottomBarDisplay.Full, bottomBarDisplay(isBusy = true, areBarsVisible = true))
    }

    @Test
    fun aBusyBarShrinksToTheCornerButtonWhenTheBarsHide() {
        assertEquals(BottomBarDisplay.Mini, bottomBarDisplay(isBusy = true, areBarsVisible = false))
    }
}

/** The Quran screen decides what the bottom shows with [bottomBarDisplay]. */
class BottomBarDisplayWiringTest {

    private val source =
        File("src/main/kotlin/app/awn/quran/feature/read/quran/QuranScreen.kt").readText()

    @Test
    fun theQuranScreenUsesTheRule() {
        assertTrue(source.contains("bottomBarDisplay("))
        assertTrue(source.contains("BottomBarDisplay.Mini"))
    }
}
