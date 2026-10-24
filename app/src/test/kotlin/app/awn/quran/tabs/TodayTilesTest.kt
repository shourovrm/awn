package app.awn.quran.tabs

import android.app.Application
import android.content.ComponentName
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TodayTilesTest {

    @get:Rule val composeRule = createComposeRule()

    init {
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application.packageManager)
            .addActivityIfNotPresent(
                ComponentName(application.packageName, "androidx.activity.ComponentActivity"),
            )
    }

    private val events = mutableListOf<String>()
    private val navigation =
        TodayNavigation(
            openLearnTab = { events += "learn tab" },
            openHifzTab = { events += "hifz tab" },
            reviseAt = { _: AyahRef, _: Int? -> events += "revise" },
        )

    private fun tilesFor(hifz: TodayItem?, learn: TodayItem?): TodayTiles {
        var tiles: TodayTiles? = null
        composeRule.setContent {
            tiles = rememberTodayTiles({ hifz }, { learn }, navigation)
        }
        composeRule.waitForIdle()
        return checkNotNull(tiles)
    }

    @Test
    fun aProviderWithNothingToDoStillGivesATileThatOpensItsTab() {
        val tiles = tilesFor(hifz = null, learn = null)

        assertEquals("Hifz", tiles.hifz.title)
        assertEquals("Learn", tiles.learn.title)
        assertEquals("Nothing today", tiles.hifz.detail)
        tiles.hifz.onOpen()
        tiles.learn.onOpen()
        assertEquals(listOf("hifz tab", "learn tab"), events)
    }

    @Test
    fun aProvidersOfferReplacesTheIdleTile() {
        val offer = TodayItem("Words", "10 min", icon = 0, onOpen = {})

        val tiles = tilesFor(hifz = null, learn = offer)

        assertEquals("Words", tiles.learn.title)
        assertEquals("10 min", tiles.learn.detail)
        assertEquals("Hifz", tiles.hifz.title)
    }
}
