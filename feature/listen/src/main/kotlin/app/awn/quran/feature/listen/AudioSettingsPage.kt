package app.awn.quran.feature.listen

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awn.quran.core.ui.components.AwnSwitch
import app.awn.quran.core.ui.components.AwnTopBar
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.player.DownloadAmount

/**
 * The Audio settings page for the Settings hub: reciter, download amount, Wi-Fi only, the downloads
 * manager and the space used. It draws its own back arrow and title; [onBack] leaves the page (the
 * Back key first closes the manager).
 */
@Composable
fun AudioSettingsPage(feature: AudioFeature, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var managerOpen by rememberSaveable { mutableStateOf(false) }
    var managerReciterId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = managerOpen) {
        if (managerReciterId != null) managerReciterId = null else managerOpen = false
    }
    Column(modifier.fillMaxSize()) {
        val goBack: () -> Unit = {
            when {
                managerReciterId != null -> managerReciterId = null
                managerOpen -> managerOpen = false
                else -> onBack()
            }
        }
        // The shared bar, like every other settings page: it also keeps clear of the status bar.
        AwnTopBar(
            title = stringResource(R.string.audio_settings_title),
            onNavigateBack = goBack,
            backDescription = stringResource(R.string.audio_back),
        )
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
    Column(
        modifier = Modifier.padding(horizontal = AwnSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        SettingRow(
            label = stringResource(R.string.audio_setting_reciter),
            value = state.reciterName,
            onClick = { reciterSheetOpen = true },
        )
        DownloadAmountRow(state.downloadAmount, viewModel::setDownloadAmount)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.audio_setting_wifi),
                style = AwnTypography.body,
                modifier = Modifier.weight(1f),
            )
            AwnSwitch(checked = state.wifiOnly, onCheckedChange = viewModel::setWifiOnly)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.audio_setting_stop_on_close),
                style = AwnTypography.body,
                modifier = Modifier.weight(1f),
            )
            AwnSwitch(
                checked = state.stopOnTaskRemoved,
                onCheckedChange = viewModel::setStopOnTaskRemoved,
            )
        }
        SettingRow(
            label = stringResource(R.string.audio_setting_downloads),
            value = "",
            onClick = onOpenManager,
        )
        SpaceUsedRow(state.usedBytes)
    }
    if (reciterSheetOpen) {
        ReciterSheet(feature, page = null, onDismiss = { reciterSheetOpen = false })
    }
}

@Composable
private fun DownloadAmountRow(selected: DownloadAmount, onSelect: (DownloadAmount) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        Text(
            text = stringResource(R.string.audio_setting_download_amount),
            style = AwnTypography.small,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            for (amount in DownloadAmount.entries) {
                Chip(
                    label = stringResource(amountLabel(amount)),
                    isSelected = amount == selected,
                    onClick = { onSelect(amount) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SpaceUsedRow(usedBytes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwnSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.audio_setting_space),
            style = AwnTypography.body,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Formatter.formatShortFileSize(LocalContext.current, usedBytes),
            style = AwnTypography.bodyStrong,
        )
    }
}

private fun amountLabel(amount: DownloadAmount): Int =
    when (amount) {
        DownloadAmount.Page -> R.string.amount_page
        DownloadAmount.Surah -> R.string.amount_surah
        DownloadAmount.Juz -> R.string.amount_juz
    }

/** A tappable row: label at the left, [value] and a chevron at the right. */
@Composable
internal fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body, modifier = Modifier.weight(1f))
        Text(text = value, style = AwnTypography.bodyStrong)
        ListenIcon(R.drawable.ic_listen_chevron, null)
    }
}
