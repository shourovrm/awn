package app.awn.quran.feature.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.MeasuredPage
import app.awn.quran.feature.read.page.MushafPage
import app.awn.quran.feature.read.page.PageFamilies
import app.awn.quran.feature.read.page.PageMeasureKey
import app.awn.quran.feature.read.page.PageMeasurementCache
import app.awn.quran.feature.read.page.PagePlacement
import app.awn.quran.feature.read.page.PageWork
import app.awn.quran.feature.read.page.TokenMeasurements
import app.awn.quran.feature.read.page.layoutPage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageMeasurementTest {
    @get:Rule val composeRule = createComposeRule()

    private val workExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, PAGE_WORK_THREAD)
    }
    private val workThreads = mutableListOf<String>()
    private val workCount = AtomicInteger()

    @After
    fun restoreDispatcher() {
        PageWork.dispatcher = Dispatchers.Default
        workExecutor.shutdownNow()
    }

    private fun measuredPage(): MeasuredPage =
        MeasuredPage(
            geometry =
                layoutPage(
                    rows = emptyList(),
                    isCentredBlock = false,
                    widthPx = 100f,
                    heightPx = 100f,
                    tokenWidthEm = { 1f },
                ),
            measurements = TokenMeasurements(emptyMap(), firstBaselineEm = 0.8f),
            basmalaWidthEm = null,
            rows = emptyList(),
            families = PageFamilies(null, null),
            pageHeightPx = 100f,
        )

    private fun keyFor(width: Float) =
        PageMeasureKey(
            layout = ReadFixture.pageOne(),
            hasGlyphFont = false,
            families = PageFamilies(null, null),
            size =
                PagePlacement(
                    widthPx = width,
                    bodyHeightPx = 100f,
                    sizeFactor = 1f,
                    isCentredBlock = false,
                ),
            density = Density(1f),
        )

    @Test
    fun theCacheKeepsOnlyTheMostRecentlyUsedPages() {
        val cache = PageMeasurementCache(capacity = 2)
        val keys = listOf(keyFor(1f), keyFor(2f), keyFor(3f))
        cache[keys[0]] = measuredPage()
        cache[keys[1]] = measuredPage()
        assertNotNull(cache[keys[0]]) // used again, so the second page is now the oldest
        cache[keys[2]] = measuredPage()

        assertEquals(2, cache.size)
        assertNull(cache[keys[1]])
        assertNotNull(cache[keys[0]])
    }

    @Test
    fun theDefaultCacheHoldsThePageOnScreenAndTwoOnEachSide() {
        val cache = PageMeasurementCache()

        (1..20).forEach { index -> cache[keyFor(index.toFloat())] = measuredPage() }

        assertEquals(5, cache.size)
    }

    @Test
    fun keysOfALayoutLoadedAgainAndOfTheSameSizeAreEqual() {
        val layout = ReadFixture.pageOne()
        fun key(l: PageLayout) =
            PageMeasureKey(
                l,
                false,
                PageFamilies(null, null),
                PagePlacement(10f, 20f, 1f, false),
                Density(2f),
            )

        assertEquals(key(layout), key(layout))
        // The same page loaded again is another object with the same content.
        assertEquals(key(layout), key(ReadFixture.pageOne()))
        assertTrue(key(layout) != key(ReadFixture.pageTwo()))
    }

    @Test
    fun aPageIsMeasuredOnTheWorkThreadAndAppearsWhenReady() {
        PageWork.dispatcher =
            object : CoroutineDispatcher() {
                private val delegate = workExecutor.asCoroutineDispatcher()

                override fun dispatch(
                    context: CoroutineContext,
                    block: Runnable,
                ) {
                    workCount.incrementAndGet()
                    delegate.dispatch(context) {
                        workThreads += Thread.currentThread().name
                        block.run()
                    }
                }
            }
        val layout = ReadFixture.pageOne()

        composeRule.setContent {
            AwnTheme {
                Box(Modifier.width(297.dp).height(453.dp)) {
                    MushafPage(
                        layout = layout,
                        chapters = ReadFixture().text.chapterList,
                        fontFamily = null,
                        plainFamily = null,
                        showPageInfo = false,
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule
                .onAllNodes(hasText(ReadFixture.FIRST_WORD_DRAWN))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText(ReadFixture.FIRST_WORD_DRAWN).assertIsDisplayed()
        assertTrue("work ran on $workThreads", workThreads.isNotEmpty())
        assertTrue(workThreads.all { it == PAGE_WORK_THREAD })
    }

    @Test
    fun aPageMeasuredBeforeIsDrawnWithoutMeasuringAgain() {
        PageWork.dispatcher =
            object : CoroutineDispatcher() {
                override fun dispatch(
                    context: CoroutineContext,
                    block: Runnable,
                ) {
                    workCount.incrementAndGet()
                    block.run()
                }
            }
        val layout = ReadFixture.pageOne()
        var showPage by mutableStateOf(true)
        composeRule.setContent {
            AwnTheme {
                Box(Modifier.width(300.dp).height(450.dp)) {
                    if (showPage) {
                        MushafPage(
                            layout = layout,
                            chapters = ReadFixture().text.chapterList,
                            fontFamily = null,
                            plainFamily = null,
                            showPageInfo = false,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val firstCount = workCount.get()
        showPage = false
        composeRule.waitForIdle()

        showPage = true
        composeRule.waitForIdle()

        assertEquals(1, firstCount)
        assertEquals(firstCount, workCount.get())
        composeRule.onNodeWithText(ReadFixture.FIRST_WORD_DRAWN).assertIsDisplayed()
    }

    private companion object {
        const val PAGE_WORK_THREAD = "page-work-test"
        const val WAIT_MILLIS = 5_000L
    }
}
