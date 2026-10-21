package app.awn.quran.feature.read

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.MushafPage
import app.awn.quran.feature.read.page.PageWork
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coming back from Settings builds the Quran screen again and loads the layout of the page again,
 * as a new object with the same content. The page must then draw in its first frame from the
 * measurements kept before, without waiting for the measuring worker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CachedPageFirstFrameTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val fixture = ReadFixture()

    /** Takes work and never runs it: a page that draws anyway did not need the worker. */
    private object StalledDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
    }

    @Test
    fun aPageLoadedAgainDrawsFromTheMeasurementsOfTheFirstLoad() {
        val firstLoad = runBlocking { fixture.text.pageLayout(1, ScriptId.QpcHafs) }
        var layout by mutableStateOf(firstLoad)
        composeRule.setContent {
            AwnTheme {
                MushafPage(
                    layout = layout,
                    chapters = fixture.text.chapterList,
                    fontFamily = null,
                    plainFamily = null,
                    showPageInfo = false,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(ReadFixture.FIRST_WORD_DRAWN).assertIsDisplayed()

        // The layout is loaded again, as a new object, and the worker never answers.
        PageWork.dispatcher = StalledDispatcher
        val secondLoad = runBlocking { fixture.text.pageLayout(1, ScriptId.QpcHafs) }
        check(secondLoad !== firstLoad) { "The test needs a second layout object." }
        layout = secondLoad
        composeRule.waitForIdle()

        composeRule.onNodeWithText(ReadFixture.FIRST_WORD_DRAWN).assertIsDisplayed()
    }
}
