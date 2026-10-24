package app.awn.quran

import android.app.Application
import android.content.ComponentName
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.ui.theme.HideTabBarOnInnerPage
import app.awn.quran.tabs.AwnTab
import app.awn.quran.tabs.TabsHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The bottom tabs show only on the root page of a tab. A tab that opens an inner page calls
 * [HideTabBarOnInnerPage]; the bar is gone while that page is open, returns when it closes, and is
 * back on the next tab when the user switches away from an inner page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TabsHostBarTest {

    @get:Rule val composeRule = createComposeRule()

    private var isLearnOnInnerPage by mutableStateOf(false)
    private var selectedTab by mutableStateOf(AwnTab.Learn)

    init {
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application.packageManager)
            .addActivityIfNotPresent(
                ComponentName(application.packageName, "androidx.activity.ComponentActivity"),
            )
    }

    private fun showTabs() {
        composeRule.setContent {
            TabsHost(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                quranTab = { Text("quran root") },
                learnTab = {
                    HideTabBarOnInnerPage(isLearnOnInnerPage)
                    Text("learn page")
                },
                hifzTab = { Text("hifz root") },
            )
        }
        composeRule.waitForIdle()
    }

    private fun tabLabel(tab: AwnTab) =
        ApplicationProvider.getApplicationContext<Application>().getString(tab.label)

    private fun assertBarShown() {
        composeRule.onNodeWithText(tabLabel(AwnTab.Hifz)).assertIsDisplayed()
    }

    private fun assertBarHidden() {
        composeRule.onNodeWithText(tabLabel(AwnTab.Hifz)).assertDoesNotExist()
    }

    @Test
    fun theBarShowsOnATabRoot() {
        showTabs()
        assertBarShown()
    }

    @Test
    fun theBarIsAbsentOnAnInnerPageAndReturnsWhenItCloses() {
        showTabs()
        composeRule.runOnIdle { isLearnOnInnerPage = true }
        composeRule.waitForIdle()
        assertBarHidden()
        composeRule.runOnIdle { isLearnOnInnerPage = false }
        composeRule.waitForIdle()
        assertBarShown()
    }

    @Test
    fun leavingAnInnerPageForAnotherTabShowsTheBar() {
        showTabs()
        composeRule.runOnIdle { isLearnOnInnerPage = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { selectedTab = AwnTab.Quran }
        composeRule.waitForIdle()
        assertBarShown()
    }
}
