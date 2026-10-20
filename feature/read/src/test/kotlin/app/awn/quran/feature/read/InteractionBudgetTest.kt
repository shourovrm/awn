package app.awn.quran.feature.read

import app.awn.quran.feature.read.page.LONG_PRESS_MILLIS
import app.awn.quran.feature.read.page.PLAYING_FADE_MS
import app.awn.quran.feature.read.quran.BARS_REVEAL_MS
import app.awn.quran.feature.read.quran.BAR_FADE_MS
import app.awn.quran.feature.read.quran.RESUME_DEBOUNCE_MS
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Numbers from sessions/baseline/quran-android-baseline.md that this screen must meet, checked
 * where the JVM can check them. The phone checks the rest (frame times, first change on screen).
 */
class InteractionBudgetTest {
    private val sources =
        File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun theBarFadeStartsInsideTheFirstChangeBudgetAndEndsInsideTheSettledBudget() {
        // The fade is a tween from the tap: the first pixel changes on the next frame and the last
        // change is BAR_FADE_MS later. Budgets: first change 150 ms, settled 400 ms.
        val oneFrameAt60Hz = 17
        assertTrue(oneFrameAt60Hz <= 150)
        assertTrue(BAR_FADE_MS + oneFrameAt60Hz <= 400)
        assertTrue(BAR_FADE_MS <= 120)
    }

    @Test
    fun theTimingsMatchTheSpecification() {
        assertEquals(1_500L, RESUME_DEBOUNCE_MS)
        assertEquals(2_000L, BARS_REVEAL_MS)
        assertEquals(500, PLAYING_FADE_MS)
        assertEquals(500L, LONG_PRESS_MILLIS)
    }

    @Test
    fun noGestureOnTheReadingScreenListensForADoubleTap() {
        assertTrue("sources not found", sources.isNotEmpty())
        // A double-tap handler makes Compose hold back the single tap, so bars would toggle late.
        val offenders = sources.filter { file ->
            val text = file.readText()
            "onDoubleTap" in text || "onDoubleClick" in text
        }
        assertEquals(emptyList<File>(), offenders)
    }

    @Test
    fun theBarsAreToggledByTheTapNotByATimer() {
        // The only delay in the bars' code is the opening reveal; a tap never waits.
        val viewModel = sources.single { it.name == "QuranViewModel.kt" }.readText()
        val toggle = viewModel.substringAfter("fun toggleBars()").substringBefore("}")
        assertTrue("delay(" !in toggle)
    }
}
