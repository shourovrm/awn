package app.awn.quran.feature.read

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.QuranScreen
import app.awn.quran.feature.read.quran.QuranScreenSlots
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.settings.PageView
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Looking up a page font opens the font pack, and for a tajweed page at night writes a recoloured
 * copy of the font. That work stalls a swipe when it runs on the main thread, so the screen must
 * only ask for fonts from the IO dispatcher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainThreadFontReadTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val packs = RecordingPackRepository()
    private val fixture = ReadFixture()

    private fun openScreen(view: PageView, script: ScriptId) {
        packs.install(script, File("pages.ttf"))
        packs.install(ScriptId.QpcHafs, File("hafs.ttf"))
        runBlocking { fixture.store.update { it.copy(pageView = view, script = script) } }
        val dependencies = fixture.dependencies.copy(packs = packs)
        composeRule.setContent {
            AwnTheme {
                QuranScreen(dependencies, QuranScreenSlots.Empty, target = QuranTarget.Page(1))
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        // Loads run on the IO dispatcher, which the compose rule does not wait for.
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            packs.fontFileRequests.isNotEmpty()
        }
    }

    private fun threadsThatReadFonts() = packs.fontFileRequests.map { it.thread }.toSet()

    private fun assertNoFontLookupOnTheMainThread() {
        val mainThread = Looper.getMainLooper().thread
        val onMain = threadsThatReadFonts().filter { it == mainThread }
        assertEquals("font lookups on the main thread", emptyList<Thread>(), onMain)
        assertTrue("the screen asked for no font at all", packs.fontFileRequests.isNotEmpty())
    }

    @Test
    fun theMushafPagerLooksUpFontsOffTheMainThread() {
        openScreen(PageView.Mushaf, ScriptId.V4Tajweed)

        assertNoFontLookupOnTheMainThread()
    }

    @Test
    fun theLineByLinePagerLooksUpFontsOffTheMainThread() {
        openScreen(PageView.LineByLine, ScriptId.V4Tajweed)

        assertNoFontLookupOnTheMainThread()
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
