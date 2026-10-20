package app.awn.quran

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.feature.learn.LearnScreen
import app.awn.quran.feature.listen.AudioBar
import app.awn.quran.feature.listen.PlayRangePanel
import app.awn.quran.feature.read.ReadField
import app.awn.quran.feature.read.quran.QuranScreen
import app.awn.quran.feature.read.quran.QuranScreenSlots
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.recite.ReciteBar
import app.awn.quran.feature.recite.rememberReciteWordStates
import app.awn.quran.index.IndexActions
import app.awn.quran.index.IndexScreen
import app.awn.quran.index.IndexViewModel
import app.awn.quran.notice.NoticeScreen
import app.awn.quran.player.PlaybackState
import app.awn.quran.search.SearchScreen
import app.awn.quran.search.SearchViewModel
import app.awn.quran.services.AwnServices
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AwnApp(services: AwnServices) {
    val preferences = services.preferences
    val colourSettings by
        preferences.colourSettings.collectAsStateWithLifecycle(initialValue = ColourSettings())
    val colourMode = colourSettings.resolve(deviceIsDark = isSystemInDarkTheme())
    // null until DataStore has answered, so the notice never flashes before the stored answer.
    val noticeAccepted by
        preferences.noticeAccepted.collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()

    CompositionLocalProvider(LocalColourMode provides colourMode) {
        when (noticeAccepted) {
            null -> AwnTheme {}
            false ->
                NoticeScreen(onAccept = { coroutineScope.launch { preferences.acceptNotice() } })
            true -> {
                AskForNotificationsWhenAudioStarts(services)
                AwnNavigation(services = services, colourSettings = colourSettings)
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

/**
 * Every screen switches instantly. Navigation Compose's default is a 700 ms cross-fade on every
 * navigate and pop, which made Back feel slow; the four transitions below must stay `None`
 * (NavTransitionsTest checks this file). Predictive back still works: with no transition there is
 * nothing to seek, and at the start destination the system plays its back-to-home animation.
 */
@Composable
private fun AwnNavigation(services: AwnServices, colourSettings: ColourSettings) {
    val navController = rememberNavController()
    val viewModelFactory = remember(services) { AwnViewModelFactory(services) }
    NavHost(
        navController = navController,
        startDestination = Routes.QURAN,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(
            route = Routes.QURAN,
            arguments =
                listOf(
                    optionalIntArgument(Routes.PAGE_ARGUMENT),
                    optionalIntArgument(Routes.SURAH_ARGUMENT),
                    optionalIntArgument(Routes.AYAH_ARGUMENT),
                ),
        ) { entry ->
            QuranRoute(services, entry, navController)
        }
        indexDestinations(navController, viewModelFactory)
        settingsDestinations(services, navController, viewModelFactory, colourSettings)
        composable(Routes.LEARN) { LearnScreen(quranText = services.coreData.quranText) }
    }
}

private fun optionalIntArgument(name: String) =
    navArgument(name) {
        type = NavType.IntType
        defaultValue = Routes.NOT_GIVEN
    }

/** Opens the Quran screen and drops the screens above the old one, so Back never retraces them. */
private fun NavController.openQuran(page: Int?, ayah: AyahRef?) {
    navigate(Routes.quran(page, ayah)) {
        popUpTo(Routes.QURAN) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavGraphBuilder.indexDestinations(
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
) {
    composable(Routes.INDEX) {
        val viewModel: IndexViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        IndexScreen(
            state = state,
            onSortChange = viewModel::setSort,
            actions =
                IndexActions(
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onLearn = { navController.navigate(Routes.LEARN) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpen = navController::openQuran,
                ),
        )
    }
    composable(Routes.SEARCH) {
        val viewModel: SearchViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val typedQuery by viewModel.typedQuery.collectAsStateWithLifecycle()
        SearchScreen(
            typedQuery = typedQuery,
            state = state,
            onQueryChange = viewModel::setQuery,
            onBack = { navController.popBackStack() },
            onOpen = navController::openQuran,
        )
    }
}

/**
 * The Quran screen with every feature plugged into its slots: the audio bar and play panel from
 * :feature:listen, the recite bar from :feature:recite, day and night from the app preferences.
 * [Routes.SURAH_ARGUMENT] and [Routes.AYAH_ARGUMENT] select an ayah, else [Routes.PAGE_ARGUMENT]
 * opens a page, else the page read last opens.
 */
@Composable
private fun QuranRoute(
    services: AwnServices,
    entry: NavBackStackEntry,
    navController: NavController,
) {
    val arguments = entry.arguments
    val page = arguments?.getInt(Routes.PAGE_ARGUMENT)?.takeIf { it != Routes.NOT_GIVEN }
    val surah = arguments?.getInt(Routes.SURAH_ARGUMENT)?.takeIf { it != Routes.NOT_GIVEN }
    val ayah = arguments?.getInt(Routes.AYAH_ARGUMENT)?.takeIf { it != Routes.NOT_GIVEN }
    val target =
        when {
            surah != null && ayah != null -> QuranTarget.Ayah(AyahRef(surah, ayah))
            page != null -> QuranTarget.Page(page)
            else -> null
        }
    val nightToggle = rememberNightToggle(services.preferences)
    val slots =
        QuranScreenSlots(
            audioBar = { currentPage -> AudioBar(services.audio, currentPage) },
            playPanel = { selected, currentPage ->
                PlayRangePanel(services.audio, selected, currentPage)
            },
            onPlayFrom = services.audio.controller::playFrom,
            reciteBar = { onExit -> ReciteBar(services.recite, onExit) },
            onReciteFrom = services.recite::reciteFrom,
            reciteWordStates = { rememberReciteWordStates(services.recite) },
            isNight = nightToggle.isNight,
            onToggleNight = nightToggle.toggle,
            onOpenIndex = { navController.navigate(Routes.INDEX) },
            onOpenSearch = { navController.navigate(Routes.SEARCH) },
            onOpenLearn = { navController.navigate(Routes.LEARN) },
            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
        )
    AwnTheme(field = ReadField) {
        QuranScreen(dependencies = services.readDependencies, slots = slots, target = target)
    }
}
