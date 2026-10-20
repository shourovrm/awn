package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.LocalThemePreference
import app.awn.quran.core.ui.theme.ThemePreference
import app.awn.quran.feature.learn.LearnScreen
import app.awn.quran.feature.listen.ListenScreen
import app.awn.quran.feature.read.ReadScreen
import app.awn.quran.feature.recite.ReciteScreen
import app.awn.quran.home.HomeScreen
import app.awn.quran.notice.NoticeScreen
import app.awn.quran.settings.AboutScreen
import app.awn.quran.settings.SettingsScreen
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val READ = "read"
    const val LISTEN = "listen"
    const val RECITE = "recite"
    const val LEARN = "learn"
}

@Composable
fun AwnApp(preferences: AppPreferences) {
    val themePreference by
        preferences.themePreference.collectAsStateWithLifecycle(
            initialValue = ThemePreference.System,
        )
    // null until DataStore has answered, so the notice never flashes before the stored answer.
    val noticeAccepted by
        preferences.noticeAccepted.collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()

    CompositionLocalProvider(LocalThemePreference provides themePreference) {
        when (noticeAccepted) {
            null -> AwnTheme {}
            false ->
                NoticeScreen(onAccept = { coroutineScope.launch { preferences.acceptNotice() } })
            true -> {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                            onReadClick = { navController.navigate(Routes.READ) },
                            onListenClick = { navController.navigate(Routes.LISTEN) },
                            onReciteClick = { navController.navigate(Routes.RECITE) },
                            onLearnClick = { navController.navigate(Routes.LEARN) },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            themePreference = themePreference,
                            onThemePreferenceChange = {
                                coroutineScope.launch { preferences.setThemePreference(it) }
                            },
                            onAboutClick = { navController.navigate(Routes.ABOUT) },
                        )
                    }
                    composable(Routes.ABOUT) { AboutScreen() }
                    composable(Routes.READ) { ReadScreen() }
                    composable(Routes.LISTEN) { ListenScreen() }
                    composable(Routes.RECITE) { ReciteScreen() }
                    composable(Routes.LEARN) { LearnScreen() }
                }
            }
        }
    }
}
