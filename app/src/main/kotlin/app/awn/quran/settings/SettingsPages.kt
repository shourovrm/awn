package app.awn.quran.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.core.ui.components.SettingsInfoRow
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.components.SettingsSwitchRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.core.ui.theme.DayColours
import app.awn.quran.core.ui.theme.NightColours
import kotlinx.coroutines.launch

private const val BACKUP_FILE_NAME = "awn-bookmarks.json"
private const val BACKUP_MIME_TYPE = "application/json"

/** Day colours, night colours, follow the phone theme, language and surah name meaning. */
@Composable
fun AppearancePage(
    colours: ColourSettings,
    showSurahNameMeaning: Boolean,
    onColoursChange: ((ColourSettings) -> ColourSettings) -> Unit,
    onShowSurahNameMeaningChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    targetRow: String? = null,
) {
    val context = LocalContext.current
    SettingsPage(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
        targetRow = targetRow,
    ) {
        AwnChoiceRow(
            label = stringResource(R.string.appearance_day_colours),
            options = DayColours.entries,
            selected = colours.day,
            optionLabel = { stringResource(it.label()) },
            onSelect = { chosen -> onColoursChange { it.copy(day = chosen) } },
            rowKey = SettingRowKeys.APPEARANCE_DAY,
        )
        AwnChoiceRow(
            label = stringResource(R.string.appearance_night_colours),
            options = NightColours.entries,
            selected = colours.night,
            optionLabel = { stringResource(it.label()) },
            onSelect = { chosen -> onColoursChange { it.copy(night = chosen) } },
            rowKey = SettingRowKeys.APPEARANCE_NIGHT,
        )
        SettingsSwitchRow(
            label = stringResource(R.string.appearance_follow_device),
            checked = colours.followDevice,
            onCheckedChange = { follows -> onColoursChange { it.copy(followDevice = follows) } },
            rowKey = SettingRowKeys.APPEARANCE_FOLLOW_DEVICE,
        )
        // The language is chosen in the system's per-app picker, which this row opens.
        SettingsLinkRow(
            label = stringResource(R.string.appearance_language),
            value = LocalConfiguration.current.locales[0].let { it.getDisplayLanguage(it) },
            onClick = { openLanguageSettings(context) },
            rowKey = SettingRowKeys.APPEARANCE_LANGUAGE,
        )
        SettingsSwitchRow(
            label = stringResource(R.string.appearance_surah_name_meaning),
            checked = showSurahNameMeaning,
            onCheckedChange = onShowSurahNameMeaningChange,
            rowKey = SettingRowKeys.APPEARANCE_SURAH_MEANING,
        )
    }
}

internal fun DayColours.label(): Int =
    when (this) {
        DayColours.Awn -> R.string.colours_awn
        DayColours.Light -> R.string.colours_light
    }

internal fun NightColours.label(): Int =
    when (this) {
        NightColours.Dark -> R.string.colours_dark
        NightColours.Black -> R.string.colours_black
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

/** Export and import of bookmarks, saved ayahs and recent pages as one file. */
@Composable
fun BackupPage(viewModel: SettingsViewModel, onBack: () -> Unit, targetRow: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<BackupResult?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
        ) { uri ->
            if (uri != null)
                scope.launch { result = viewModel.exportTo(context.contentResolver, uri) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch { result = viewModel.importFrom(context.contentResolver, uri) }
        }
    SettingsPage(
        title = stringResource(R.string.settings_backup),
        onBack = onBack,
        targetRow = targetRow,
    ) {
        SettingsLinkRow(
            icon = R.drawable.ic_app_file_upload,
            label = stringResource(R.string.backup_export),
            onClick = { exportLauncher.launch(BACKUP_FILE_NAME) },
            rowKey = SettingRowKeys.BACKUP_EXPORT,
        )
        SettingsLinkRow(
            icon = R.drawable.ic_app_file_download,
            label = stringResource(R.string.backup_import),
            onClick = { importLauncher.launch(arrayOf(BACKUP_MIME_TYPE)) },
            rowKey = SettingRowKeys.BACKUP_IMPORT,
        )
        result?.let { shown ->
            Text(
                text = stringResource(shown.messageResource()),
                style = AwnTypography.small,
                color = Awn.field.content,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
    }
}

private fun BackupResult.messageResource(): Int =
    when (this) {
        BackupResult.Exported -> R.string.backup_exported
        BackupResult.Imported -> R.string.backup_imported
        BackupResult.Failed -> R.string.backup_failed
    }

/** The notice, the credits and the version. */
@Composable
fun AboutPage(
    onOpenNotice: () -> Unit,
    onOpenCredits: () -> Unit,
    onBack: () -> Unit,
    targetRow: String? = null,
) {
    val context = LocalContext.current
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    SettingsPage(
        title = stringResource(R.string.settings_about),
        onBack = onBack,
        targetRow = targetRow,
    ) {
        SettingsLinkRow(
            label = stringResource(R.string.about_notice),
            onClick = onOpenNotice,
            rowKey = SettingRowKeys.ABOUT_NOTICE,
        )
        SettingsLinkRow(
            label = stringResource(R.string.about_credits),
            onClick = onOpenCredits,
            rowKey = SettingRowKeys.ABOUT_CREDITS,
        )
        SettingsInfoRow(
            label = stringResource(R.string.about_version),
            value = versionName,
            rowKey = SettingRowKeys.ABOUT_VERSION,
        )
    }
}

/**
 * The Recite and memorise page: the settings of the recite module in [moduleSettings], then the
 * "Memorised after" row, which belongs to Learn and is kept here in :app.
 */
@Composable
fun RecitePage(
    memorisedAfterDays: Int,
    onMemorisedAfterChange: (Int) -> Unit,
    onBack: () -> Unit,
    targetRow: String? = null,
    moduleSettings: @Composable () -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.settings_recite),
        onBack = onBack,
        targetRow = targetRow,
    ) {
        moduleSettings()
        AwnChoiceRow(
            label = stringResource(R.string.recite_memorised_after),
            options = MEMORISED_AFTER_DAYS.toList(),
            selected = memorisedAfterDays,
            optionLabel = { pluralStringResource(R.plurals.recite_days, it, it) },
            onSelect = onMemorisedAfterChange,
            rowKey = SettingRowKeys.RECITE_MEMORISED_AFTER,
        )
    }
}
