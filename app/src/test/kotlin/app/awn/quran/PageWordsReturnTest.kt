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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Words on this page showed the Learn tab, so Back left the Mushaf page behind. Back from those
 * words must reopen the Quran page and select the tab that was selected before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PageWordsReturnTest {

    @get:Rule val composeRule = createComposeRule()

    init {
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application.packageManager)
            .addActivityIfNotPresent(
                ComponentName(application.packageName, "androidx.activity.ComponentActivity"),
            )
    }

    private var selectedTab = AwnTab.Hifz
    private var remembered: PageWordsReturn? = null
    private var requestedPage: Int? = null
    private lateinit var navController: NavHostController

    private fun navigation(): TabNavigation {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Routes.TABS) {
                composable(Routes.TABS) { Text("tabs") }
                composable(
                    Routes.QURAN,
                    arguments =
                        listOf(Routes.PAGE_ARGUMENT, Routes.SURAH_ARGUMENT, Routes.AYAH_ARGUMENT)
                            .map { name ->
                                navArgument(name) {
                                    type = NavType.IntType
                                    defaultValue = Routes.NOT_GIVEN
                                }
                            },
                ) {
                    Text("quran")
                }
            }
        }
        composeRule.waitForIdle()
        return TabNavigation(
            navController = navController,
            selectTab = { selectedTab = it },
            requestPageWords = { requestedPage = it },
            currentTab = selectedTab,
            pageWordsReturn = remembered,
            rememberPageWordsReturn = { remembered = it },
        )
    }

    @Test
    fun showPageWordsRemembersThePageAndTheTabItCameFrom() {
        val navigation = navigation()

        composeRule.runOnIdle { navigation.showPageWords(page = 25) }

        assertEquals(PageWordsReturn(quranPage = 25, tab = AwnTab.Hifz), remembered)
        assertEquals(25, requestedPage)
        assertEquals(AwnTab.Learn, selectedTab)
    }

    @Test
    fun leavingThePageWordsReopensThePageOverTheEarlierTab() {
        val navigation = navigation()
        composeRule.runOnIdle { navigation.showPageWords(page = 25) }
        selectedTab = AwnTab.Learn

        val afterShowing =
            TabNavigation(
                navController = navController,
                selectTab = { selectedTab = it },
                currentTab = selectedTab,
                pageWordsReturn = remembered,
                rememberPageWordsReturn = { remembered = it },
            )
        composeRule.runOnIdle { afterShowing.leavePageWords() }
        composeRule.waitForIdle()

        assertEquals(AwnTab.Hifz, selectedTab)
        assertNull(remembered)
        val shown = navController.currentBackStackEntry?.arguments
        assertEquals(25, shown?.getInt(Routes.PAGE_ARGUMENT))
    }

    @Test
    fun leavingWithNothingRememberedDoesNothing() {
        val navigation = navigation()

        composeRule.runOnIdle { navigation.leavePageWords() }

        assertEquals(Routes.TABS, navController.currentDestination?.route)
        assertEquals(AwnTab.Hifz, selectedTab)
    }
}
