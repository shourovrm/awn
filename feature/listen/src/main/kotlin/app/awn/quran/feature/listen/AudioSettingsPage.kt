package app.awn.quran.feature.listen

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.AwnSettingsPage
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.core.ui.components.SettingsGroupHeading
import app.awn.quran.core.ui.components.SettingsInfoRow
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.components.SettingsSwitchRow
import app.awn.quran.player.DownloadAmount

/**
 * The Audio settings page for the Settings hub: reciter, download unit, Wi-Fi only, stop on close,
 * the downloads manager and the space used. It draws its own title bar because the manager opens
 * inside it; [onBack] leaves the page (the Back key first closes the manager). A search result
 * passes [targetRow] to scroll to one row.
 */
@Composable
fun AudioSettingsPage(
    feature: AudioFeature,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetRow: String? = null,
) {
    var managerOpen by rememberSaveable { mutableStateOf(false) }
    var managerReciterId by rememberSaveable { mutableStateOf<String?>(null) }
    val goBack: () -> Unit = {
        when {
            managerReciterId != null -> managerReciterId = null
            managerOpen -> managerOpen = false
            else -> onBack()
        }
    }
    BackHandler(enabled = managerOpen, onBack = goBack)
    val title = if (managerOpen) R.string.audio_setting_downloads else R.string.audio_settings_title
    AwnSettingsPage(
        title = stringResource(title),
        backDescription = stringResource(R.string.audio_back),
        onBack = goBack,
        modifier = modifier,
        scrolls = managerReciterId == null,
        targetRow = targetRow,
    ) {
        val reciterId = managerReciterId
        when {
            !managerOpen -> AudioSettingsMain(feature, onOpenManager = { managerOpen = true })
            reciterId == null ->
                ManagerReciterList(feature, onOpenReciter = { managerReciterId = it })
            else -> ManagerReciterDetail(feature, reciterId)
        }
    }
}

@Composable
private fun AudioSettingsMain(feature: AudioFeature, onOpenManager: () -> Unit) {
    val viewModel: AudioSettingsViewModel = viewModel(factory = feature.viewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var reciterSheetOpen by rememberSaveable { mutableStateOf(false) }

    SettingsGroupHeading(stringResource(R.string.audio_group_playback), isFirst = true)
    SettingsLinkRow(
        label = stringResource(R.string.audio_setting_reciter),
        value = state.reciterName,
        onClick = { reciterSheetOpen = true },
        rowKey = SettingRowKeys.AUDIO_RECITER,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.audio_setting_stop_on_close),
        checked = state.stopOnTaskRemoved,
        onCheckedChange = viewModel::setStopOnTaskRemoved,
        rowKey = SettingRowKeys.AUDIO_STOP_ON_CLOSE,
    )

    SettingsGroupHeading(stringResource(R.string.audio_group_downloads))
    AwnChoiceRow(
        label = stringResource(R.string.audio_setting_download_amount),
        hint = stringResource(R.string.audio_setting_download_amount_hint),
        options = DownloadAmount.entries,
        selected = state.downloadAmount,
        optionLabel = { stringResource(amountLabel(it)) },
        onSelect = viewModel::setDownloadAmount,
        rowKey = SettingRowKeys.AUDIO_DOWNLOAD_BY,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.audio_setting_wifi),
        checked = state.wifiOnly,
        onCheckedChange = viewModel::setWifiOnly,
        rowKey = SettingRowKeys.AUDIO_WIFI_ONLY,
    )
    SettingsLinkRow(
        label = stringResource(R.string.audio_setting_downloads),
        value = stringResource(R.string.audio_downloads_hint),
        onClick = onOpenManager,
        rowKey = SettingRowKeys.AUDIO_DOWNLOADS,
    )
    SettingsInfoRow(
        label = stringResource(R.string.audio_setting_space),
        value = Formatter.formatShortFileSize(LocalContext.current, state.usedBytes),
        rowKey = SettingRowKeys.AUDIO_SPACE_USED,
    )
    if (reciterSheetOpen) {
        ReciterSheet(feature, page = null, onDismiss = { reciterSheetOpen = false })
    }
}

private fun amountLabel(amount: DownloadAmount): Int =
    when (amount) {
        DownloadAmount.Page -> R.string.amount_page
        DownloadAmount.Surah -> R.string.amount_surah
        DownloadAmount.Juz -> R.string.amount_juz
    }
