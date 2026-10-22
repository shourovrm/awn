package app.awn.quran

import android.app.Application
import android.content.ComponentName
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.tabs.AwnTab
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Back on the Quran screen always showed the Quran tab, so a page opened from Hifz or Learn
 * returned to the surah index instead of the tab the user came from. [TabNavigation.returnToTabs]
 * closes the screens above the tabs and leaves the chosen tab as it was; [TabNavigation.showTab]
 * still switches tab for the index button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TabNavigationTest {

    @get:Rule val composeRule = createComposeRule()

    init {
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application.packageManager)
            .addActivityIfNotPresent(
                ComponentName(application.packageName, "androidx.activity.ComponentActivity"),
            )
    }

    private var selectedTab = AwnTab.Quran
    private lateinit var navController: NavHostController

    private fun navigationAbove(tab: AwnTab): TabNavigation {
        selectedTab = tab
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Routes.TABS) {
                composable(Routes.TABS) { Text("tabs") }
                composable(Routes.SETTINGS) { Text("settings") }
            }
        }
        composeRule.waitForIdle()
        val navigation = TabNavigation(navController, selectTab = { selectedTab = it })
        composeRule.runOnIdle { navController.navigate(Routes.SETTINGS) }
        composeRule.waitForIdle()
        return navigation
    }

    @Test
    fun returnToTabsKeepsTheHifzTab() {
        val navigation = navigationAbove(AwnTab.Hifz)

        composeRule.runOnIdle { navigation.returnToTabs() }
        composeRule.waitForIdle()

        assertEquals(Routes.TABS, navController.currentDestination?.route)
        assertEquals(AwnTab.Hifz, selectedTab)
    }

    @Test
    fun returnToTabsKeepsTheLearnTab() {
        val navigation = navigationAbove(AwnTab.Learn)

        composeRule.runOnIdle { navigation.returnToTabs() }
        composeRule.waitForIdle()

        assertEquals(Routes.TABS, navController.currentDestination?.route)
        assertEquals(AwnTab.Learn, selectedTab)
    }

    @Test
    fun showTabStillSwitchesToTheChosenTab() {
        val navigation = navigationAbove(AwnTab.Hifz)

        composeRule.runOnIdle { navigation.showTab(AwnTab.Quran) }
        composeRule.waitForIdle()

        assertEquals(Routes.TABS, navController.currentDestination?.route)
        assertEquals(AwnTab.Quran, selectedTab)
    }
}

/** The Quran screen's Back must use [TabNavigation.returnToTabs], not a forced tab switch. */
class QuranBackWiringTest {

    private val source = File("src/main/kotlin/app/awn/quran/AwnApp.kt").readText()

    @Test
    fun backWhenIdleReturnsToTheTabTheUserCameFrom() {
        val wiring = Regex("""onBackWhenIdle\s*=\s*([^\n]+)""").find(source)?.groupValues?.get(1)

        assertTrue(
            "onBackWhenIdle is wired to: $wiring",
            wiring != null && wiring.contains("returnToTabs") && !wiring.contains("showTab"),
        )
    }
}
