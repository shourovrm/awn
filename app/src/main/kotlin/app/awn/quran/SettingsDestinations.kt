package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.feature.read.settings.ReadSettingsScreen
import app.awn.quran.feature.recite.ReciteSettingsScreen
import app.awn.quran.services.AwnServices
import app.awn.quran.settings.AboutPage
import app.awn.quran.settings.AppearancePage
import app.awn.quran.settings.AudioPlaceholderRows
import app.awn.quran.settings.BackupPage
import app.awn.quran.settings.CreditsScreen
import app.awn.quran.settings.MushafPlaceholderRows
import app.awn.quran.settings.NoticeAgainScreen
import app.awn.quran.settings.RecitePage
import app.awn.quran.settings.SettingsHubScreen
import app.awn.quran.settings.SettingsPage
import app.awn.quran.settings.SettingsPageSlots
import app.awn.quran.settings.SettingsSection
import app.awn.quran.settings.SettingsViewModel
import app.awn.quran.settings.loadCredits
import kotlinx.coroutines.launch

internal fun NavGraphBuilder.settingsDestinations(
    services: AwnServices,
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    colourSettings: ColourSettings,
) {
    val goBack: () -> Unit = { navController.popBackStack() }
    composable(Routes.SETTINGS) {
        SettingsHubScreen(
            onBack = goBack,
            onOpenSection = { navController.navigate(it.route) },
        )
    }
    composable(SettingsSection.Appearance.route) {
        val showMeaning by
            services.preferences.showSurahNameMeaning.collectAsStateWithLifecycle(false)
        val scope = rememberCoroutineScope()
        AppearancePage(
            colours = colourSettings,
            showSurahNameMeaning = showMeaning,
            onColoursChange = { change ->
                scope.launch { services.preferences.updateColourSettings(change) }
            },
            onShowSurahNameMeaningChange = {
                scope.launch { services.preferences.setShowSurahNameMeaning(it) }
            },
            onBack = goBack,
        )
    }
    composable(SettingsSection.Backup.route) {
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        BackupPage(viewModel = viewModel, onBack = goBack)
    }
    composable(SettingsSection.About.route) {
        AboutPage(
            onOpenNotice = { navController.navigate(Routes.NOTICE) },
            onOpenCredits = { navController.navigate(Routes.CREDITS) },
            onBack = goBack,
        )
    }
    composable(Routes.NOTICE) { NoticeAgainScreen(onBack = goBack) }
    composable(Routes.CREDITS) {
        val credits by
            produceState(emptyList(), services) { value = loadCredits(services.coreData.packs) }
        CreditsScreen(credits, onBack = goBack)
    }
    moduleSettingsDestinations(services, navController, viewModelFactory)
}

/**
 * The four pages whose rows other modules own. Until the maintainer connects their page
 * composables, [placeholderPageSlots] shows the settings screens that exist today.
 */
private fun NavGraphBuilder.moduleSettingsDestinations(
    services: AwnServices,
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
) {
    val goBack: () -> Unit = { navController.popBackStack() }
    composable(SettingsSection.Mushaf.route) {
        val slots = placeholderPageSlots(services, navController, viewModelFactory)
        SettingsPage(title = stringResource(R.string.settings_mushaf), onBack = goBack) {
            slots.mushaf()
        }
    }
    composable(SettingsSection.Reading.route) {
        val slots = placeholderPageSlots(services, navController, viewModelFactory)
        SettingsPage(title = stringResource(R.string.settings_reading), onBack = goBack) {
            slots.reading()
        }
    }
    composable(SettingsSection.Audio.route) {
        val slots = placeholderPageSlots(services, navController, viewModelFactory)
        SettingsPage(title = stringResource(R.string.settings_audio), onBack = goBack) {
            slots.audio()
        }
    }
    composable(SettingsSection.Recite.route) {
        val slots = placeholderPageSlots(services, navController, viewModelFactory)
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        RecitePage(
            memorisedAfterDays = state.memorisationThreshold,
            onMemorisedAfterChange = viewModel::setMemorisationThreshold,
            onBack = goBack,
            moduleSettings = slots.reciteModule,
        )
    }
}

@Composable
private fun placeholderPageSlots(
    services: AwnServices,
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
): SettingsPageSlots {
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    return SettingsPageSlots(
        mushaf = { MushafPlaceholderRows() },
        reading = { ReadSettingsScreen(services.readDependencies) },
        audio = {
            AudioPlaceholderRows(
                state = state,
                onWifiOnlyChange = viewModel::setWifiOnly,
                onReciterClick = { navController.navigate(Routes.RECITERS) },
                onDownloadsClick = { navController.navigate(Routes.DOWNLOADS) },
            )
        },
        reciteModule = { ReciteSettingsScreen(services.reciteSettings) },
    )
}
