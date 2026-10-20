package app.awn.quran.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.R
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.ThemePreference
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.settings.ReadSettingsScreen
import app.awn.quran.feature.recite.ReciteSettings
import app.awn.quran.feature.recite.ReciteSettingsScreen
import kotlinx.coroutines.launch

private const val BACKUP_FILE_NAME = "awn-bookmarks.json"
private const val BACKUP_MIME_TYPE = "application/json"

/** Where the rows of the App group lead. */
class SettingsActions(
    val onReciterClick: () -> Unit,
    val onDownloadsClick: () -> Unit,
    val onNoticeClick: () -> Unit,
    val onCreditsClick: () -> Unit,
)

/**
 * One scrolling list on the paper field: Reading, Listening, Reciting, Learning and App groups,
 * each a green title followed by rows (docs/design-system.md, "Layout of the Settings screen").
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    readDependencies: ReadDependencies,
    reciteSettings: ReciteSettings,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    actions: SettingsActions,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AwnTheme(field = AwnField.Paper) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.large),
        ) {
            Text(text = stringResource(R.string.settings_title), style = AwnTypography.title)
            ReadSettingsScreen(readDependencies)
            ListeningGroup(state, viewModel, actions)
            ReciteSettingsScreen(reciteSettings)
            LearningGroup(state, viewModel)
            AppGroup(viewModel, themePreference, onThemePreferenceChange, actions)
        }
    }
}

@Composable
private fun ListeningGroup(
    state: SettingsState,
    viewModel: SettingsViewModel,
    actions: SettingsActions,
) {
    Column(Modifier.fillMaxWidth()) {
        GroupTitle(stringResource(R.string.settings_group_listening))
        ValueRow(
            stringResource(R.string.settings_reciter),
            state.reciterName,
            actions.onReciterClick,
        )
        ValueRow(
            label = stringResource(R.string.settings_downloads),
            value = "",
            onClick = actions.onDownloadsClick,
        )
        ValueRow(
            label = stringResource(R.string.settings_audio_size),
            value = Formatter.formatShortFileSize(LocalContext.current, state.downloadedBytes),
            onClick = null,
        )
        SwitchRow(
            label = stringResource(R.string.settings_wifi_only),
            checked = state.wifiOnly,
            onCheckedChange = viewModel::setWifiOnly,
        )
    }
}

@Composable
private fun LearningGroup(state: SettingsState, viewModel: SettingsViewModel) {
    Column(Modifier.fillMaxWidth()) {
        GroupTitle(stringResource(R.string.settings_group_learning))
        ValueRow(
            label = stringResource(R.string.settings_memorised_after),
            value = state.memorisationThreshold.toString(),
            onClick = viewModel::cycleMemorisationThreshold,
        )
    }
}

@Composable
private fun AppGroup(
    viewModel: SettingsViewModel,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    actions: SettingsActions,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupMessage by remember { mutableStateOf<Int?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    backupMessage =
                        viewModel.exportTo(context.contentResolver, uri).messageResource()
                }
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    backupMessage =
                        viewModel.importFrom(context.contentResolver, uri).messageResource()
                }
            }
        }
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }

    Column(Modifier.fillMaxWidth()) {
        GroupTitle(stringResource(R.string.settings_group_app))
        ValueRow(
            label = stringResource(R.string.settings_theme),
            value = stringResource(themePreference.labelResource()),
            onClick = { onThemePreferenceChange(themePreference.next()) },
        )
        ValueRow(
            label = stringResource(R.string.settings_language),
            value = LocalConfiguration.current.locales[0].let { it.getDisplayLanguage(it) },
            onClick = { openLanguageSettings(context) },
        )
        ValueRow(
            label = stringResource(R.string.settings_backup_export),
            value = backupMessage?.let { stringResource(it) }.orEmpty(),
            onClick = { exportLauncher.launch(BACKUP_FILE_NAME) },
        )
        ValueRow(
            label = stringResource(R.string.settings_backup_import),
            value = "",
            onClick = { importLauncher.launch(arrayOf(BACKUP_MIME_TYPE)) },
        )
        ValueRow(stringResource(R.string.settings_notice), "", actions.onNoticeClick)
        ValueRow(stringResource(R.string.settings_credits), "", actions.onCreditsClick)
        ValueRow(stringResource(R.string.settings_version), versionName.orEmpty(), onClick = null)
    }
}

private fun BackupResult.messageResource(): Int =
    when (this) {
        BackupResult.Exported -> R.string.settings_backup_exported
        BackupResult.Imported -> R.string.settings_backup_imported
        BackupResult.Failed -> R.string.settings_backup_failed
    }

/** Opens the system's per-app language picker, or the device language list before Android 13. */
private fun openLanguageSettings(context: Context) {
    val deviceList = Intent(Settings.ACTION_LOCALE_SETTINGS)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        context.startActivity(deviceList)
        return
    }
    val perAppPicker =
        Intent(
            Settings.ACTION_APP_LOCALE_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
    try {
        context.startActivity(perAppPicker)
    } catch (_: ActivityNotFoundException) {
        // A device without the per-app picker still has the language list.
        context.startActivity(deviceList)
    }
}

/** Tapping the Theme row steps through Light, Dark and System. */
private fun ThemePreference.next(): ThemePreference =
    ThemePreference.entries[(ordinal + 1) % ThemePreference.entries.size]

private fun ThemePreference.labelResource(): Int =
    when (this) {
        ThemePreference.Light -> R.string.theme_light
        ThemePreference.Dark -> R.string.theme_dark
        ThemePreference.System -> R.string.theme_system
    }
