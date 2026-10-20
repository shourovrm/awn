package app.awn.quran

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.LocalThemePreference
import app.awn.quran.core.ui.theme.ThemePreference
import app.awn.quran.feature.learn.LearnScreen
import app.awn.quran.feature.listen.DownloadsScreen
import app.awn.quran.feature.listen.DownloadsViewModel
import app.awn.quran.feature.listen.ListenViewModel
import app.awn.quran.feature.listen.RecitersScreen
import app.awn.quran.feature.listen.RecitersViewModel
import app.awn.quran.feature.read.ReadHost
import app.awn.quran.feature.recite.ReciteScreen
import app.awn.quran.home.HomeActions
import app.awn.quran.home.HomeScreen
import app.awn.quran.home.HomeViewModel
import app.awn.quran.listen.ListenRoute
import app.awn.quran.notice.NoticeScreen
import app.awn.quran.player.PlaybackState
import app.awn.quran.services.AwnServices
import app.awn.quran.settings.CreditsScreen
import app.awn.quran.settings.NoticeAgainScreen
import app.awn.quran.settings.SettingsActions
import app.awn.quran.settings.SettingsScreen
import app.awn.quran.settings.SettingsViewModel
import app.awn.quran.settings.loadCredits
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val NOTICE = "notice"
    const val CREDITS = "credits"
    const val PAGE_ARGUMENT = "page"
    const val NO_PAGE = -1
    const val READ = "read?$PAGE_ARGUMENT={$PAGE_ARGUMENT}"
    const val LISTEN = "listen"
    const val RECITERS = "reciters"
    const val DOWNLOADS = "downloads"
    const val RECITE = "recite"
    const val LEARN = "learn"

    fun read(page: Int? = null): String = "read?$PAGE_ARGUMENT=${page ?: NO_PAGE}"
}

@Composable
fun AwnApp(services: AwnServices) {
    val preferences = services.preferences
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
                AskForNotificationsWhenAudioStarts(services)
                AwnNavigation(
                    services = services,
                    themePreference = themePreference,
                    onThemePreferenceChange = {
                        coroutineScope.launch { preferences.setThemePreference(it) }
                    },
                )
            }
        }
    }
}

/**
 * Asks for the notification permission the first time audio plays, because the player's media
 * notification needs it on Android 13 and later. Playing works without it.
 */
@Composable
private fun AskForNotificationsWhenAudioStarts(services: AwnServices) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        // Before Android 13 the permission does not exist and notifications are always allowed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        services.audioPlayer.state.first { it is PlaybackState.Playing }
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val isGranted =
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        if (!isGranted) launcher.launch(permission)
    }
}

@Composable
private fun AwnNavigation(
    services: AwnServices,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
) {
    val navController = rememberNavController()
    val viewModelFactory = remember(services) { AwnViewModelFactory(services) }
    NavHost(navController = navController, startDestination = Routes.HOME) {
        homeDestination(navController, viewModelFactory)
        settingsDestinations(
            services = services,
            navController = navController,
            viewModelFactory = viewModelFactory,
            themePreference = themePreference,
            onThemePreferenceChange = onThemePreferenceChange,
        )
        composable(
            route = Routes.READ,
            arguments =
                listOf(
                    navArgument(Routes.PAGE_ARGUMENT) {
                        type = NavType.IntType
                        defaultValue = Routes.NO_PAGE
                    },
                ),
        ) { entry ->
            ReadRoute(services, entry, navController)
        }
        listenDestinations(services, navController, viewModelFactory)
        composable(Routes.RECITE) {
            val viewModel: ReciteViewModel = viewModel(factory = viewModelFactory)
            ReciteScreen(
                range = viewModel.range,
                session = viewModel.session,
                settings = services.reciteSettings,
                audioPlayer = services.audioPlayer,
                onRangeChange = { viewModel.range = it },
            )
        }
        composable(Routes.LEARN) { LearnScreen(quranText = services.coreData.quranText) }
    }
}

private fun NavGraphBuilder.homeDestination(
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
) {
    composable(Routes.HOME) {
        val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        HomeScreen(
            state = state,
            actions =
                HomeActions(
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onRead = { navController.navigate(Routes.read()) },
                    onListen = { navController.navigate(Routes.LISTEN) },
                    onRecite = { navController.navigate(Routes.RECITE) },
                    onLearn = { navController.navigate(Routes.LEARN) },
                    onContinueReading = { navController.navigate(Routes.read(state.lastPage)) },
                ),
        )
    }
}

private fun NavGraphBuilder.settingsDestinations(
    services: AwnServices,
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
) {
    composable(Routes.SETTINGS) {
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        SettingsScreen(
            viewModel = viewModel,
            readDependencies = services.readDependencies,
            reciteSettings = services.reciteSettings,
            themePreference = themePreference,
            onThemePreferenceChange = onThemePreferenceChange,
            actions =
                SettingsActions(
                    onReciterClick = { navController.navigate(Routes.RECITERS) },
                    onDownloadsClick = { navController.navigate(Routes.DOWNLOADS) },
                    onNoticeClick = { navController.navigate(Routes.NOTICE) },
                    onCreditsClick = { navController.navigate(Routes.CREDITS) },
                ),
        )
    }
    composable(Routes.NOTICE) { NoticeAgainScreen() }
    composable(Routes.CREDITS) {
        val credits by
            produceState(emptyList(), services) { value = loadCredits(services.coreData.packs) }
        CreditsScreen(credits)
    }
}

private fun NavGraphBuilder.listenDestinations(
    services: AwnServices,
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
) {
    composable(Routes.LISTEN) {
        val viewModel: ListenViewModel = viewModel(factory = viewModelFactory)
        ListenRoute(
            viewModel = viewModel,
            quranText = services.coreData.quranText,
            onOpenReciters = { navController.navigate(Routes.RECITERS) },
            onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
        )
    }
    composable(Routes.RECITERS) {
        val viewModel: RecitersViewModel = viewModel(factory = viewModelFactory)
        RecitersScreen(
            viewModel = viewModel,
            onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
        )
    }
    composable(Routes.DOWNLOADS) {
        val viewModel: DownloadsViewModel = viewModel(factory = viewModelFactory)
        DownloadsScreen(viewModel)
    }
}

/** Read mode: opens the page given by the route, else the page read last. */
@Composable
private fun ReadRoute(
    services: AwnServices,
    entry: NavBackStackEntry,
    navController: NavController,
) {
    val requestedPage =
        entry.arguments?.getInt(Routes.PAGE_ARGUMENT)?.takeIf { it != Routes.NO_PAGE }
    val reciterNames by
        produceState(emptyMap<String, String>(), services) {
            value = services.coreData.reciters.reciters().associate { it.id to it.name }
        }
    ReadHost(
        dependencies = services.readDependencies,
        onExpandPlayer = { navController.navigate(Routes.LISTEN) },
        startPage = requestedPage,
        reciterName = { reciterId -> reciterNames[reciterId] ?: reciterId },
    )
}
