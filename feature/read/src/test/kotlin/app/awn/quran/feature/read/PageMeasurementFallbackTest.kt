package app.awn.quran.feature.read

import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.read.page.MeasuredPage
import app.awn.quran.feature.read.page.PageFamilies
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.PageWork
import app.awn.quran.feature.read.page.TokenMeasurements
import app.awn.quran.feature.read.page.layoutPage
import app.awn.quran.feature.read.page.measureWithFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageMeasurementFallbackTest {
    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val layout = PageLayout(page = 92, script = ScriptId.V4Tajweed, lines = emptyList())

    private fun measuredPage(rows: List<PageRow>): MeasuredPage =
        MeasuredPage(
            geometry = layoutPage(emptyList(), false, 100f, 100f, { 1f }),
            measurements = TokenMeasurements(emptyMap(), firstBaselineEm = 0.8f),
            basmalaWidthEm = null,
            rows = rows,
            families = PageFamilies(null, null),
        )

    @Test
    fun aPageThatMeasuresIsDrawnWithItsOwnPrint() {
        val own = measuredPage(listOf(PageRow.BasmalaRow(1)))

        val result = runBlocking {
            measureWithFallback(layout, { own }, { error("the fallback must not run") })
        }

        assertSame(own, result)
    }

    @Test
    fun aPageThatThrowsIsDrawnInTheFallbackFont() {
        val fallback = measuredPage(listOf(PageRow.BasmalaRow(2)))

        val result = runBlocking {
            measureWithFallback(layout, { error("cannot place page 92") }, { fallback })
        }

        assertSame(fallback, result)
    }

    @Test
    fun aPageThatNeverFinishesIsDrawnInTheFallbackFont() {
        // A real thread pool: inline work would block the caller and the timeout could not fire.
        PageWork.dispatcher = Dispatchers.Default
        val fallback = measuredPage(emptyList())

        val result = runBlocking {
            measureWithFallback(
                layout,
                measureOwnPrint = {
                    Thread.sleep(SLOW_MEASURE_MS)
                    measuredPage(emptyList())
                },
                measureFallback = { fallback },
                timeoutMs = TIMEOUT_MS,
            )
        }

        assertSame(fallback, result)
    }

    private companion object {
        const val TIMEOUT_MS = 100L
        const val SLOW_MEASURE_MS = 1_500L
    }
}
