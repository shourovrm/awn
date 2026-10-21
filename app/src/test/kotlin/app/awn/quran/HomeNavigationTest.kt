package app.awn.quran

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back on the Quran screen goes to the index (home) and Back on the index leaves the app. The
 * graph below has the routes of the real one; its Quran screen handles Back the way QuranScreen
 * does: first what is open, then [goToHomeFromQuran].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeNavigationTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private var isSomethingOpen by mutableStateOf(false)

    private fun launchApp() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = Routes.QURAN) {
                composable(Routes.QURAN) {
                    BackHandler(enabled = true) {
                        if (isSomethingOpen) {
                            isSomethingOpen = false
                        } else {
                            navController.goToHomeFromQuran()
                        }
                    }
                    Text("quran")
                }
                composable(Routes.INDEX) { Text("index") }
                composable(Routes.SETTINGS) { Text("settings") }
            }
        }
        composeRule.waitForIdle()
    }

    private fun navigateOnMainThread(block: () -> Unit) {
        composeRule.runOnUiThread(block)
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        navigateOnMainThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun currentRoute() = navController.currentBackStackEntry?.destination?.route

    @Test
    fun backOnLaunchedQuranScreenGoesToIndexAndSecondBackLeavesApp() {
        launchApp()
        assertEquals(Routes.QURAN, currentRoute())

        pressBack()
        assertEquals(Routes.INDEX, currentRoute())
        assertFalse(composeRule.activity.isFinishing)

        pressBack()
        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun backAfterOpeningASurahFromTheIndexReturnsToTheIndex() {
        launchApp()
        navigateOnMainThread { navController.navigate(Routes.INDEX) }
        // Same call as AwnApp's openQuran, which the index's rows use.
        navigateOnMainThread {
            navController.navigate(Routes.QURAN) {
                popUpTo(Routes.QURAN) { inclusive = true }
                launchSingleTop = true
            }
        }
        assertEquals(Routes.QURAN, currentRoute())

        pressBack()
        assertEquals(Routes.INDEX, currentRoute())
        pressBack()
        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun backPopsToTheIndexWhenTheQuranScreenWasOpenedFromIt() {
        launchApp()
        pressBack()
        navigateOnMainThread { navController.navigate(Routes.QURAN) }
        assertEquals(Routes.QURAN, currentRoute())

        pressBack()
        assertEquals(Routes.INDEX, currentRoute())
        assertEquals(1, navController.currentBackStack.value.count { it.destination.route == Routes.INDEX })
        pressBack()
        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun backClosesOpenThingsBeforeLeavingTheQuranScreen() {
        launchApp()
        isSomethingOpen = true

        pressBack()
        assertEquals(Routes.QURAN, currentRoute())
        pressBack()
        assertEquals(Routes.INDEX, currentRoute())
    }

    @Test
    fun backFromSettingsReturnsToTheQuranScreen() {
        launchApp()
        navigateOnMainThread { navController.navigate(Routes.SETTINGS) }

        pressBack()
        assertEquals(Routes.QURAN, currentRoute())
    }
}
