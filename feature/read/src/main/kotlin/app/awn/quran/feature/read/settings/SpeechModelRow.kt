package app.awn.quran.feature.read.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.models.SpeechModelState
import app.awn.quran.core.data.models.SpeechModelStore
import app.awn.quran.core.ui.components.AwnConfirmDialog
import app.awn.quran.core.ui.components.AwnPackRow
import app.awn.quran.core.ui.components.PackRowButton
import app.awn.quran.core.ui.components.PackTick
import app.awn.quran.feature.read.R
import kotlinx.coroutines.launch

/**
 * The download row of what recite needs: its size or progress, and one button that fits the state
 * (download, cancel or remove). Removing asks first and says how much space it frees; [onRemoved]
 * runs after the files are deleted, so the caller can drop anything it keeps loaded.
 */
@Composable
fun SpeechModelRow(
    store: SpeechModelStore,
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isConfirmingRemoval by remember { mutableStateOf(false) }
    val title = stringResource(R.string.speech_row_title)
    val sizeText = sizeText(context, store.totalBytes)

    val detail =
        when (val current = state) {
            SpeechModelState.NotInstalled -> sizeText
            is SpeechModelState.Downloading ->
                stringResource(
                    R.string.catalogue_status_downloading,
                    (current.progress * PERCENT).toInt(),
                )
            SpeechModelState.Installed ->
                stringResource(R.string.catalogue_status_installed, sizeText)
            is SpeechModelState.Failed ->
                failureMessage(current.reason, current.neededBytes, current.freeBytes)
        }
    val downloadButton =
        PackRowButton(stringResource(R.string.catalogue_download)) { store.download() }
    val cancelButton = PackRowButton(stringResource(R.string.catalogue_cancel)) { store.cancel() }
    val removeButton =
        PackRowButton(stringResource(R.string.catalogue_remove)) { isConfirmingRemoval = true }

    AwnPackRow(
        title = title,
        detail = detail,
        tick = PackTick.Absent,
        onTickChange = {},
        modifier = modifier,
        isDetailError = state is SpeechModelState.Failed,
        progress = (state as? SpeechModelState.Downloading)?.progress,
        download =
            downloadButton.takeIf {
                state is SpeechModelState.NotInstalled || state is SpeechModelState.Failed
            },
        cancel = cancelButton.takeIf { state is SpeechModelState.Downloading },
        remove = removeButton.takeIf { state is SpeechModelState.Installed },
    )
    if (isConfirmingRemoval) {
        AwnConfirmDialog(
            title = stringResource(R.string.catalogue_remove_title, title),
            message = stringResource(R.string.catalogue_remove_frees, sizeText),
            confirmLabel = stringResource(R.string.catalogue_remove),
            dismissLabel = stringResource(R.string.catalogue_dialog_cancel),
            onConfirm = {
                isConfirmingRemoval = false
                scope.launch {
                    store.remove()
                    onRemoved()
                }
            },
            onDismiss = { isConfirmingRemoval = false },
        )
    }
}

private const val PERCENT = 100
