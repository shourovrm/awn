package app.awn.quran.settings

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
import app.awn.quran.core.ui.theme.mutedContent
import kotlinx.coroutines.launch

private const val BACKUP_FILE_NAME = "awn-bookmarks.json"
private const val BACKUP_MIME_TYPE = "application/json"

/** Day colours, night colours and following the phone's dark mode. */
@Composable
fun ColoursPage(
    colours: ColourSettings,
    onColoursChange: ((ColourSettings) -> ColourSettings) -> Unit,
    onBack: () -> Unit,
    targetRow: String? = null,
) {
    SettingsPage(
        title = stringResource(R.string.settings_colours),
        onBack = onBack,
        targetRow = targetRow,
    ) {
        AwnChoiceRow(
            label = stringResource(R.string.colours_day),
            options = DayColours.entries,
            selected = colours.day,
            optionLabel = { stringResource(it.label()) },
            onSelect = { chosen -> onColoursChange { it.copy(day = chosen) } },
            rowKey = SettingRowKeys.COLOURS_DAY,
        )
        AwnChoiceRow(
            label = stringResource(R.string.colours_night),
            options = NightColours.entries,
            selected = colours.night,
            optionLabel = { stringResource(it.label()) },
            onSelect = { chosen -> onColoursChange { it.copy(night = chosen) } },
            rowKey = SettingRowKeys.COLOURS_NIGHT,
        )
        SettingsSwitchRow(
            label = stringResource(R.string.colours_follow_phone),
            checked = colours.followDevice,
            onCheckedChange = { follows -> onColoursChange { it.copy(followDevice = follows) } },
            rowKey = SettingRowKeys.COLOURS_FOLLOW_PHONE,
        )
    }
}

internal fun DayColours.label(): Int =
    when (this) {
        DayColours.Awn -> R.string.colours_cream
        DayColours.Light -> R.string.colours_white
    }

internal fun NightColours.label(): Int =
    when (this) {
        NightColours.Dark -> R.string.colours_dark
        NightColours.Black -> R.string.colours_black
    }

/** Saving bookmarks, saved ayahs and recent pages to one file, and restoring them from it. */
@Composable
fun BackupPage(viewModel: SettingsViewModel, onBack: () -> Unit, targetRow: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<BackupResult?>(null) }
    val saveLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
        ) { uri ->
            if (uri != null)
                scope.launch { result = viewModel.exportTo(context.contentResolver, uri) }
        }
    val restoreLauncher =
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
            label = stringResource(R.string.backup_save),
            onClick = { saveLauncher.launch(BACKUP_FILE_NAME) },
            rowKey = SettingRowKeys.BACKUP_SAVE,
        )
        SettingsLinkRow(
            icon = R.drawable.ic_app_file_download,
            label = stringResource(R.string.backup_restore),
            onClick = { restoreLauncher.launch(arrayOf(BACKUP_MIME_TYPE)) },
            rowKey = SettingRowKeys.BACKUP_RESTORE,
        )
        Text(
            text = stringResource(R.string.backup_scope),
            style = AwnTypography.small,
            color = Awn.field.mutedContent,
            modifier = Modifier.padding(top = AwnSpacing.small),
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
        BackupResult.Saved -> R.string.backup_saved
        BackupResult.Restored -> R.string.backup_restored
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
            label = stringResource(R.string.notice_learn_title),
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
 * The Recite and Hifz page: the settings of the recite module in [moduleSettings], which draws the
 * "Memorised after" row where it is told to by the slot it passes to the content, so the order is
 * hide words, first-letter hint, memorised after, follow pace.
 */
@Composable
fun ReciteHifzPage(
    memorisedAfterDays: Int,
    onMemorisedAfterChange: (Int) -> Unit,
    onBack: () -> Unit,
    targetRow: String? = null,
    moduleSettings: @Composable (memorisedAfterRow: @Composable () -> Unit) -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.settings_recite),
        onBack = onBack,
        targetRow = targetRow,
    ) {
        moduleSettings {
            AwnChoiceRow(
                label = stringResource(R.string.recite_memorised_after),
                hint = stringResource(R.string.recite_memorised_after_hint),
                options = MEMORISED_AFTER_DAYS.toList(),
                selected = memorisedAfterDays,
                optionLabel = { pluralStringResource(R.plurals.recite_days, it, it) },
                onSelect = onMemorisedAfterChange,
                rowKey = SettingRowKeys.RECITE_MEMORISED_AFTER,
            )
        }
    }
}

/** The daily reminder and the lesson language; the rows belong to the learn module. */
@Composable
fun LearnPage(
    onBack: () -> Unit,
    targetRow: String? = null,
    moduleSettings: @Composable () -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.settings_learn),
        onBack = onBack,
        targetRow = targetRow,
    ) {
        moduleSettings()
    }
}
