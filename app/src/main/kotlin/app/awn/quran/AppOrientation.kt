package app.awn.quran

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.awn.quran.feature.read.settings.ReadSettingsStore
import app.awn.quran.feature.read.settings.requestedOrientation

/**
 * Applies the saved app orientation on every screen except the Quran pages, which apply their own
 * and restore this one when they close. Without this, a change made in Settings would only take
 * effect the next time the reader leaves the Quran screen.
 */
@Composable
internal fun ApplyAppOrientation(settingsStore: ReadSettingsStore, navController: NavController) {
    val activity = LocalActivity.current
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val appOrientation = settings?.appOrientation
    LaunchedEffect(activity, route, appOrientation) {
        val isOtherScreen = route != null && route != Routes.QURAN
        if (activity != null && appOrientation != null && isOtherScreen) {
            activity.requestedOrientation = appOrientation.requestedOrientation()
        }
    }
}
