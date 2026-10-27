package app.awn.quran

import android.app.Application
import android.content.ComponentName
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
                composable(Routes.DUAS, arguments = duasArguments()) { Text("duas") }
                composable(Routes.QURAN, arguments = quranArguments()) { Text("quran") }
            }
        }
        composeRule.waitForIdle()
        val navigation = TabNavigation(navController, selectTab = { selectedTab = it })
        composeRule.runOnIdle { navController.navigate(Routes.SETTINGS) }
        composeRule.waitForIdle()
        return navigation
    }

    /** The Duas route's optional group, declared as AwnApp declares it. */
    private fun duasArguments() =
        listOf(
            navArgument(Routes.DUAS_GROUP_ARGUMENT) {
                type = NavType.StringType
                nullable = true
            },
        )

    /** The Quran route's optional page, surah and ayah, declared as AwnApp declares them. */
    private fun quranArguments() =
        listOf(Routes.PAGE_ARGUMENT, Routes.SURAH_ARGUMENT, Routes.AYAH_ARGUMENT).map { name ->
            navArgument(name) {
                type = NavType.IntType
                defaultValue = Routes.NOT_GIVEN
            }
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

    @Test
    fun backFromQuranOpenedFromADuaReturnsToThatDuasEntry() {
        navigationAbove(AwnTab.Quran)
        composeRule.runOnIdle {
            navController.popBackStack(Routes.TABS, inclusive = false)
            navController.navigate(Routes.duas())
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { navController.openQuranOverDuas(page = 5, ayah = null) }
        composeRule.waitForIdle()
        assertEquals(Routes.QURAN, navController.currentDestination?.route)

        composeRule.runOnIdle { navController.leaveQuran() }
        composeRule.waitForIdle()

        assertEquals(Routes.DUAS, navController.currentDestination?.route)
    }

    @Test
    fun backFromQuranOpenedFromTheTabsReturnsToTheTabs() {
        navigationAbove(AwnTab.Quran)
        composeRule.runOnIdle {
            navController.popBackStack(Routes.TABS, inclusive = false)
            navController.openQuran(page = 5, ayah = null)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { navController.leaveQuran() }
        composeRule.waitForIdle()

        assertEquals(Routes.TABS, navController.currentDestination?.route)
    }
}

/** The Quran screen's Back must use [leaveQuran], not a forced tab switch. */
class QuranBackWiringTest {

    private val source = File("src/main/kotlin/app/awn/quran/AwnApp.kt").readText()

    @Test
    fun backWhenIdleReturnsToTheTabTheUserCameFrom() {
        val wiring = Regex("""onBackWhenIdle\s*=\s*([^\n]+)""").find(source)?.groupValues?.get(1)

        assertTrue(
            "onBackWhenIdle is wired to: $wiring",
            wiring != null && wiring.contains("leaveQuran") && !wiring.contains("showTab"),
        )
    }
}
