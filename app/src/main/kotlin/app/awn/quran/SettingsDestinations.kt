package app.awn.quran

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.feature.learn.reminder.ReviseReminderSettingsPage
import app.awn.quran.feature.learn.settings.LessonLanguageSettingsRow
import app.awn.quran.feature.listen.AudioSettingsPage
import app.awn.quran.feature.read.R as ReadR
import app.awn.quran.feature.read.page.PrintsPage
import app.awn.quran.feature.read.settings.PackListKind
import app.awn.quran.feature.read.settings.PackListPage
import app.awn.quran.feature.read.settings.QuranPageSettingsPage
import app.awn.quran.feature.read.settings.rememberCatalogueViewModel
import app.awn.quran.feature.recite.ReciteSettingsPage
import app.awn.quran.language.AppCompatLanguageApplier
import app.awn.quran.language.AppLanguage
import app.awn.quran.services.AwnServices
import app.awn.quran.settings.AboutPage
import app.awn.quran.settings.BackupPage
import app.awn.quran.settings.ColoursPage
import app.awn.quran.settings.CreditsScreen
import app.awn.quran.settings.LearnPage
import app.awn.quran.settings.NoticeAgainScreen
import app.awn.quran.settings.ReciteHifzPage
import app.awn.quran.settings.SettingsHubScreen
import app.awn.quran.settings.SettingsPage
import app.awn.quran.settings.SettingsSection
import app.awn.quran.settings.SettingsViewModel
import app.awn.quran.settings.StoragePage
import app.awn.quran.settings.hubSummaries
import app.awn.quran.settings.loadCredits
import kotlinx.coroutines.launch

/** The optional argument that names the row a search result opens. */
private const val ROW_ARGUMENT = "row"
private const val PACK_LIST_ARGUMENT = "kind"
private const val PACK_LIST_ROUTE = "settings/list"
private const val PRINTS_ROUTE = "settings/prints"

/** The optional argument that opens the audio downloads manager on arrival. */
private const val MANAGER_ARGUMENT = "manager"

/** The Audio page opened straight onto its downloaded audio manager, as Storage's Reciters does. */
internal fun audioManagerRoute(): String = "${SettingsSection.Audio.route}?$MANAGER_ARGUMENT=true"

private val rowArguments =
    listOf(
        navArgument(ROW_ARGUMENT) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )

private val managerArguments =
    rowArguments +
        listOf(
            navArgument(MANAGER_ARGUMENT) {
                type = NavType.BoolType
                defaultValue = false
            },
        )

/** The route pattern of [section], which accepts an optional row to scroll to. */
private fun routePattern(section: SettingsSection): String =
    "${section.route}?$ROW_ARGUMENT={$ROW_ARGUMENT}"

/** The Audio pattern also accepts whether to open the downloads manager on arrival. */
private fun audioRoutePattern(): String =
    "${routePattern(SettingsSection.Audio)}&$MANAGER_ARGUMENT={$MANAGER_ARGUMENT}"

private fun NavBackStackEntry.targetRow(): String? = arguments?.getString(ROW_ARGUMENT)

private fun packListRoute(kind: PackListKind): String = "$PACK_LIST_ROUTE/${kind.name}"

internal fun NavGraphBuilder.settingsDestinations(
    services: AwnServices,
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    colourSettings: ColourSettings,
) {
    val goBack: () -> Unit = { navController.popBackStack() }
    val openList: (PackListKind) -> Unit = { navController.navigate(packListRoute(it)) }
    settingsHubDestination(services, navController, viewModelFactory, colourSettings)
    coloursAndStorageDestinations(
        services,
        navController,
        viewModelFactory,
        colourSettings,
        openList,
    )
    composable(
        route = routePattern(SettingsSection.Backup),
        arguments = rowArguments,
    ) { entry ->
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        BackupPage(viewModel = viewModel, onBack = goBack, targetRow = entry.targetRow())
    }
    composable(
        route = routePattern(SettingsSection.About),
        arguments = rowArguments,
    ) { entry ->
        AboutPage(
            onOpenNotice = { navController.navigate(Routes.NOTICE) },
            onOpenCredits = { navController.navigate(Routes.CREDITS) },
            onBack = goBack,
            targetRow = entry.targetRow(),
        )
    }
    composable(Routes.NOTICE) { NoticeAgainScreen(onBack = goBack) }
    composable(Routes.CREDITS) {
        val credits by
            produceState(emptyList(), services) { value = loadCredits(services.coreData.packs) }
        CreditsScreen(credits, onBack = goBack)
    }
    composable("$PACK_LIST_ROUTE/{$PACK_LIST_ARGUMENT}") { entry ->
        val kind =
            PackListKind.entries.first { it.name == entry.arguments?.getString(PACK_LIST_ARGUMENT) }
        SettingsPage(title = stringResource(kind.title), onBack = goBack) {
            PackListPage(services.readDependencies, kind)
        }
    }
    moduleSettingsDestinations(services, navController, viewModelFactory, openList)
}

/** The hub: search field, grouped sections and the current value under each. */
private fun NavGraphBuilder.settingsHubDestination(
    services: AwnServices,
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    colourSettings: ColourSettings,
) {
    val goBack: () -> Unit = { navController.popBackStack() }
    composable(Routes.SETTINGS) {
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val catalogueViewModel = rememberCatalogueViewModel(services.readDependencies)
        val catalogue by catalogueViewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val versionName = remember {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }
        SettingsHubScreen(
            summaries =
                hubSummaries(
                    state = state,
                    colours = colourSettings,
                    totalBytes = catalogue.spaceUsedBytes + state.downloadedBytes,
                    versionName = versionName,
                ),
            onBack = goBack,
            language = AppLanguage.preselectedFor(LocalConfiguration.current.locales[0]),
            onLanguageChange = AppCompatLanguageApplier::apply,
            onOpenSection = { navController.navigate(it.route) },
            onOpenRow = { route, rowKey ->
                navController.navigate(
                    if (rowKey == null) route else "$route?$ROW_ARGUMENT=${Uri.encode(rowKey)}",
                )
            },
        )
    }
}

/** Colours and Storage, the two pages that read both the app's and the modules' state. */
private fun NavGraphBuilder.coloursAndStorageDestinations(
    services: AwnServices,
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    colourSettings: ColourSettings,
    openList: (PackListKind) -> Unit,
) {
    val goBack: () -> Unit = { navController.popBackStack() }
    composable(
        route = routePattern(SettingsSection.Colours),
        arguments = rowArguments,
    ) { entry ->
        val scope = rememberCoroutineScope()
        ColoursPage(
            colours = colourSettings,
            onColoursChange = { change ->
                scope.launch { services.preferences.updateColourSettings(change) }
            },
            onBack = goBack,
            targetRow = entry.targetRow(),
        )
    }
    composable(
        route = routePattern(SettingsSection.Storage),
        arguments = rowArguments,
    ) { entry ->
        val catalogueViewModel = rememberCatalogueViewModel(services.readDependencies)
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        StoragePage(
            viewModel = catalogueViewModel,
            audioBytes = state.downloadedBytes,
            onOpenMushaf = { navController.navigate(PRINTS_ROUTE) },
            onOpenList = openList,
            onOpenReciters = { navController.navigate(audioManagerRoute()) },
            speechModel = services.speechModel,
            onSpeechModelRemoved = services.speechPreparation::release,
            onBack = goBack,
            targetRow = entry.targetRow(),
        )
    }
}

/** The pages whose rows other modules own, drawn inside the app's settings page. */
private fun NavGraphBuilder.moduleSettingsDestinations(
    services: AwnServices,
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    openList: (PackListKind) -> Unit,
) {
    val goBack: () -> Unit = { navController.popBackStack() }
    quranPageDestinations(services, navController, openList)
    composable(
        route = audioRoutePattern(),
        arguments = managerArguments,
    ) { entry ->
        AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
            AudioSettingsPage(
                feature = services.audio,
                onBack = goBack,
                targetRow = entry.targetRow(),
                openManager = entry.arguments?.getBoolean(MANAGER_ARGUMENT) ?: false,
            )
        }
    }
    composable(
        route = routePattern(SettingsSection.ReciteHifz),
        arguments = rowArguments,
    ) { entry ->
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        ReciteHifzPage(
            memorisedAfterDays = state.memorisationThreshold,
            onMemorisedAfterChange = viewModel::setMemorisationThreshold,
            onBack = goBack,
            targetRow = entry.targetRow(),
            moduleSettings = { memorisedAfterRow ->
                ReciteSettingsPage(services.reciteSettings, memorisedAfterRow = memorisedAfterRow)
            },
        )
    }
    composable(
        route = routePattern(SettingsSection.Learn),
        arguments = rowArguments,
    ) { entry ->
        LearnPage(onBack = goBack, targetRow = entry.targetRow()) {
            ReviseReminderSettingsPage()
            LessonLanguageSettingsRow()
        }
    }
}

/** Settings, Quran page and the Mushaf chooser it opens. */
private fun NavGraphBuilder.quranPageDestinations(
    services: AwnServices,
    navController: NavController,
    openList: (PackListKind) -> Unit,
) {
    val goBack: () -> Unit = { navController.popBackStack() }
    composable(
        route = routePattern(SettingsSection.QuranPage),
        arguments = rowArguments,
    ) { entry ->
        val showMeaning by
            services.preferences.showSurahNameMeaning.collectAsStateWithLifecycle(true)
        val scope = rememberCoroutineScope()
        SettingsPage(
            title = stringResource(R.string.settings_quran_page),
            onBack = goBack,
            targetRow = entry.targetRow(),
        ) {
            QuranPageSettingsPage(
                services.readDependencies,
                onOpenMushaf = { navController.navigate(PRINTS_ROUTE) },
                onOpenPackList = openList,
                showSurahNameMeaning = showMeaning,
                onShowSurahNameMeaningChange = {
                    scope.launch { services.preferences.setShowSurahNameMeaning(it) }
                },
            )
        }
    }
    composable(PRINTS_ROUTE) {
        SettingsPage(
            title = stringResource(ReadR.string.mushaf_print),
            onBack = goBack,
            scrolls = false,
        ) {
            PrintsPage(services.readDependencies)
        }
    }
}
