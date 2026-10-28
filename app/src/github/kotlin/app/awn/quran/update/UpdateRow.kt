package app.awn.quran.update

import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awn.quran.AwnApplication
import app.awn.quran.R
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.player.UnmeteredOnlyPolicy
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

private const val UPDATES_FOLDER = "updates"

/** The "Check for update" row of the About page, in the state of the check or download. */
@Composable
fun UpdateRow() {
    val context = LocalContext.current
    val viewModel: UpdateViewModel = viewModel {
        val application = context.applicationContext as AwnApplication
        val httpClient = OkHttpClient()
        val releaseClient = ReleaseClient(httpClient)
        UpdateViewModel(
            releaseClient = releaseClient,
            apkDownloader =
                ApkDownloader(
                    httpClient,
                    releaseClient,
                    directory = application.cacheDir.resolve(UPDATES_FOLDER),
                ),
            installedVersionName =
                context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            networkPolicy =
                UnmeteredOnlyPolicy(application) {
                    application.services.listenSettings.wifiOnly.first()
                },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    UpdateRowContent(
        state = state,
        onCheck = viewModel::check,
        onDownload = viewModel::download,
        onCancel = viewModel::cancelDownload,
        onRetry = viewModel::retry,
        onInstall = { ready -> installApk(context, ready.apkFile) },
    )
}

@Composable
private fun UpdateRowContent(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: (UpdateState.Available) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onInstall: (UpdateState.Ready) -> Unit,
) {
    when (state) {
        UpdateState.Idle ->
            SettingsLinkRow(
                label = stringResource(R.string.update_check),
                onClick = onCheck,
                icon = R.drawable.ic_update_refresh,
            )
        UpdateState.Checking ->
            SettingsLinkRow(
                label = stringResource(R.string.update_check),
                onClick = {},
                icon = R.drawable.ic_update_refresh,
                trailing = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AwnSpacing.smallIconSize),
                        color = Awn.field.buttonFill,
                        strokeWidth = SPINNER_STROKE,
                    )
                },
            )
        UpdateState.UpToDate ->
            SettingsLinkRow(
                label = stringResource(R.string.update_up_to_date),
                onClick = onCheck,
                icon = UiR.drawable.ic_ui_check,
                trailing = {
                    RowIconButton(R.drawable.ic_update_refresh, R.string.update_check, onCheck)
                },
            )
        is UpdateState.Available -> AvailableRow(state, onDownload)
        is UpdateState.Downloading -> DownloadingRow(state, onCancel)
        is UpdateState.Ready -> ReadyRow(state, onInstall)
        is UpdateState.Failed -> FailedRow(state, onRetry)
    }
}

private val SPINNER_STROKE = 2.dp

@Composable
private fun AvailableRow(
    state: UpdateState.Available,
    onDownload: (UpdateState.Available) -> Unit,
) {
    val context = LocalContext.current
    SettingsLinkRow(
        label = state.versionName,
        value = Formatter.formatShortFileSize(context, state.apk.sizeBytes),
        onClick = { onDownload(state) },
        icon = R.drawable.ic_update_arrow_up,
        trailing = {
            RowIconButton(UiR.drawable.ic_ui_download, R.string.update_download) {
                onDownload(state)
            }
        },
    )
}

@Composable
private fun DownloadingRow(state: UpdateState.Downloading, onCancel: () -> Unit) {
    Column {
        SettingsLinkRow(
            label = state.available.versionName,
            value = "${state.percent}%",
            onClick = {},
            icon = UiR.drawable.ic_ui_download,
            trailing = {
                RowIconButton(UiR.drawable.ic_ui_close, R.string.update_cancel, onCancel)
            },
        )
        LinearProgressIndicator(
            progress = { state.percent / PERCENT_SCALE },
            color = Awn.field.buttonFill,
            modifier = Modifier.padding(bottom = AwnSpacing.small),
        )
    }
}

private const val PERCENT_SCALE = 100f

@Composable
private fun ReadyRow(state: UpdateState.Ready, onInstall: (UpdateState.Ready) -> Unit) {
    SettingsLinkRow(
        label = state.versionName,
        value = stringResource(R.string.update_install_hint),
        onClick = { onInstall(state) },
        icon = UiR.drawable.ic_ui_check,
        trailing = {
            AwnButton(
                label = stringResource(R.string.update_install),
                onClick = { onInstall(state) },
            )
        },
    )
}

@Composable
private fun FailedRow(state: UpdateState.Failed, onRetry: () -> Unit) {
    SettingsLinkRow(
        label = stringResource(state.reason.messageResource()),
        onClick = onRetry,
        icon = R.drawable.ic_update_warning,
        trailing = { RowIconButton(R.drawable.ic_update_refresh, R.string.update_retry, onRetry) },
    )
}

@Composable
private fun RowIconButton(icon: Int, descriptionResource: Int, onClick: () -> Unit) {
    AwnIconButton(
        icon = icon,
        contentDescription = stringResource(descriptionResource),
        onClick = onClick,
        tint = Awn.field.buttonFill,
    )
}

private fun UpdateFailure.messageResource(): Int =
    when (this) {
        UpdateFailure.NoConnection -> R.string.update_no_connection
        UpdateFailure.WifiRequired -> R.string.update_wifi_required
        UpdateFailure.NotFound -> R.string.update_not_found
        UpdateFailure.DownloadFailed -> R.string.update_download_failed
    }
