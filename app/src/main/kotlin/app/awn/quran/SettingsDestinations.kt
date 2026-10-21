package app.awn.quran

import android.net.Uri
import androidx.compose.runtime.Composable
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
import app.awn.quran.feature.listen.AudioSettingsPage
import app.awn.quran.feature.read.page.MushafSettingsPage
import app.awn.quran.feature.read.settings.PackListKind
import app.awn.quran.feature.read.settings.PackListPage
import app.awn.quran.feature.read.settings.ReadingSettingsPage
import app.awn.quran.feature.read.settings.rememberCatalogueViewModel
import app.awn.quran.feature.recite.ReciteSettingsPage
import app.awn.quran.language.AppCompatLanguageApplier
import app.awn.quran.language.AppLanguage
import app.awn.quran.services.AwnServices
import app.awn.quran.settings.AboutPage
import app.awn.quran.settings.AppearancePage
import app.awn.quran.settings.BackupPage
import app.awn.quran.settings.CreditsScreen
import app.awn.quran.settings.DownloadsPage
import app.awn.quran.settings.NoticeAgainScreen
import app.awn.quran.settings.RecitePage
import app.awn.quran.settings.SettingsHubScreen
import app.awn.quran.settings.SettingsPage
import app.awn.quran.settings.SettingsSection
import app.awn.quran.settings.SettingsViewModel
import app.awn.quran.settings.hubSummaries
import app.awn.quran.settings.loadCredits
import kotlinx.coroutines.launch

/** The optional argument that names the row a search result opens. */
private const val ROW_ARGUMENT = "row"
private const val PACK_LIST_ARGUMENT = "kind"
private const val PACK_LIST_ROUTE = "settings/list"

private val rowArguments =
    listOf(
        navArgument(ROW_ARGUMENT) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )

/** The route pattern of [section], which accepts an optional row to scroll to. */
private fun routePattern(section: SettingsSection): String =
    "${section.route}?$ROW_ARGUMENT={$ROW_ARGUMENT}"

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
    appearanceAndDownloadsDestinations(
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

/** Appearance and Downloads, the two pages that read both the app's and the modules' state. */
private fun NavGraphBuilder.appearanceAndDownloadsDestinations(
    services: AwnServices,
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    colourSettings: ColourSettings,
    openList: (PackListKind) -> Unit,
) {
    val goBack: () -> Unit = { navController.popBackStack() }
    composable(
        route = routePattern(SettingsSection.Appearance),
        arguments = rowArguments,
    ) { entry ->
        val showMeaning by
            services.preferences.showSurahNameMeaning.collectAsStateWithLifecycle(true)
        val scope = rememberCoroutineScope()
        AppearancePage(
            colours = colourSettings,
            showSurahNameMeaning = showMeaning,
            language = AppLanguage.preselectedFor(LocalConfiguration.current.locales[0]),
            onColoursChange = { change ->
                scope.launch { services.preferences.updateColourSettings(change) }
            },
            onShowSurahNameMeaningChange = {
                scope.launch { services.preferences.setShowSurahNameMeaning(it) }
            },
            onLanguageChange = AppCompatLanguageApplier::apply,
            onBack = goBack,
            targetRow = entry.targetRow(),
        )
    }
    composable(
        route = routePattern(SettingsSection.Downloads),
        arguments = rowArguments,
    ) { entry ->
        val catalogueViewModel = rememberCatalogueViewModel(services.readDependencies)
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        DownloadsPage(
            viewModel = catalogueViewModel,
            audioBytes = state.downloadedBytes,
            onOpenList = openList,
            onOpenReciters = { navController.navigate(SettingsSection.Audio.route) },
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
    composable(
        route = routePattern(SettingsSection.Mushaf),
        arguments = rowArguments,
    ) { entry ->
        SettingsPage(
            title = stringResource(R.string.settings_mushaf),
            onBack = goBack,
            targetRow = entry.targetRow(),
        ) {
            MushafSettingsPage(services.readDependencies, onOpenPackList = openList)
        }
    }
    composable(
        route = routePattern(SettingsSection.Reading),
        arguments = rowArguments,
    ) { entry ->
        SettingsPage(
            title = stringResource(R.string.settings_reading),
            onBack = goBack,
            targetRow = entry.targetRow(),
        ) {
            ReadingSettingsPage(services.readDependencies, onOpenPackList = openList)
        }
    }
    composable(
        route = routePattern(SettingsSection.Audio),
        arguments = rowArguments,
    ) { entry ->
        AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
            AudioSettingsPage(
                feature = services.audio,
                onBack = goBack,
                targetRow = entry.targetRow(),
            )
        }
    }
    composable(
        route = routePattern(SettingsSection.Recite),
        arguments = rowArguments,
    ) { entry ->
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        RecitePage(
            memorisedAfterDays = state.memorisationThreshold,
            onMemorisedAfterChange = viewModel::setMemorisationThreshold,
            onBack = goBack,
            targetRow = entry.targetRow(),
            moduleSettings = { ReciteModuleRows(services) },
        )
    }
}

@Composable
private fun ReciteModuleRows(services: AwnServices) {
    ReciteSettingsPage(services.reciteSettings)
    ReviseReminderSettingsPage()
}
