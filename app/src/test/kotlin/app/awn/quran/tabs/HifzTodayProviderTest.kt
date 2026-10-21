package app.awn.quran.tabs

import android.app.Application
import android.content.ComponentName
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.hifz.HifzTodayLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HifzTodayProviderTest {

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
            openLearnTab = { events += "learn" },
            openHifzTab = { events += "hifz tab" },
            reviseAt = { _: AyahRef -> events += "revise" },
        )

    private fun itemFor(line: HifzTodayLine?): TodayItem? {
        var item: TodayItem? = null
        val provider = HifzTodayProvider(line = { line }, startNextPart = { events += "start" })
        composeRule.setContent { item = provider.item(navigation) }
        composeRule.waitForIdle()
        return item
    }

    @Test
    fun noPlanMeansNoHifzRow() {
        assertNull(itemFor(null))
    }

    @Test
    fun aPlanGivesTheHifzRowWithItsLine() {
        val item = itemFor(HifzTodayLine("Hifz", "Sabaq page 24, revise 4 pages"))

        assertNotNull(item)
        assertEquals("Hifz", item?.title)
        assertEquals("Sabaq page 24, revise 4 pages", item?.detail)
    }

    @Test
    fun tappingShowsTheHifzTabFirstThenStartsThePart() {
        itemFor(HifzTodayLine("Hifz", "Done"))?.onOpen?.invoke()

        assertEquals(listOf("hifz tab", "start"), events)
    }

    @Test
    fun hifzComesBeforeLearnInTheSectionOrder() {
        val shown =
            visibleTodayItems(
                listOf(
                    TodayItem("Hifz", "d", 0) {},
                    TodayItem("Learn", "d", 0) {},
                ),
            )

        assertEquals(listOf("Hifz", "Learn"), shown.map { it.title })
    }
}
