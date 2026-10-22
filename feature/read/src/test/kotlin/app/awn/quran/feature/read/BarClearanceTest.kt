package app.awn.quran.feature.read

import app.awn.quran.feature.read.page.PageBarInsets
import app.awn.quran.feature.read.quran.BarClearance
import app.awn.quran.feature.read.quran.LOADED_PAGE_RADIUS
import app.awn.quran.feature.read.quran.keepPagesNear
import org.junit.Assert.assertEquals
import org.junit.Test

class BarClearanceTest {
    private fun clearance() = BarClearance(topFloorPx = 150, bottomFloorPx = 200)

    @Test
    fun beforeAnyBarIsMeasuredTheClearanceIsTheFloor() {
        assertEquals(PageBarInsets(150, 200), clearance().insets(isReciting = false))
    }

    @Test
    fun aTallerTopBarRaisesTheClearanceAndAShorterOneDoesNotLowerIt() {
        val clearance = clearance()

        clearance.onTopBarMeasured(260)
        clearance.onTopBarMeasured(150)

        assertEquals(260, clearance.topPx)
    }

    @Test
    fun theAudioBarSetsTheBottomClearanceOutsideReciting() {
        val clearance = clearance()

        clearance.onBottomBarMeasured(230, isReciting = false)

        assertEquals(230, clearance.bottomPx(isReciting = false))
    }

    @Test
    fun recitingUsesTheTallerOfTheReciteBarAndTheAudioBar() {
        val clearance = clearance()
        clearance.onBottomBarMeasured(420, isReciting = true)

        assertEquals(420, clearance.bottomPx(isReciting = true))
        assertEquals(200, clearance.bottomPx(isReciting = false))

        clearance.onBottomBarMeasured(120, isReciting = true)
        assertEquals(420, clearance.bottomPx(isReciting = true))
    }

    @Test
    fun afterRecitingTheGapOfTheReciteBarIsGone() {
        val clearance = clearance()
        clearance.onBottomBarMeasured(420, isReciting = true)

        clearance.onReciteEnded()

        assertEquals(0, clearance.reciteBarPx)
        assertEquals(200, clearance.bottomPx(isReciting = true))
    }

    @Test
    fun pagesFarFromTheOneOnScreenAreNotKeptLoaded() {
        val loaded = (1..40).associateWith { page -> "page $page" }

        val kept = keepPagesNear(loaded, centerPage = 20)

        assertEquals(((20 - LOADED_PAGE_RADIUS)..(20 + LOADED_PAGE_RADIUS)).toSet(), kept.keys)
        assertEquals("page 20", kept[20])
    }

    @Test
    fun keepingPagesNearTheFirstPageDoesNotGoBelowOne() {
        val kept = keepPagesNear((1..40).associateWith { it }, centerPage = 1)

        assertEquals((1..(1 + LOADED_PAGE_RADIUS)).toSet(), kept.keys)
    }
}
