package app.awn.quran.feature.read.settings

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.CatalogueItem
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.ItemAction
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.downloads.actionsOf
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.core.ui.components.AwnConfirmDialog
import app.awn.quran.core.ui.components.AwnPackRow
import app.awn.quran.core.ui.components.PackRowButton
import app.awn.quran.core.ui.components.PackTick
import app.awn.quran.core.ui.components.translationNameWithNote
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.page.pageTypeText

private const val PERCENT = 100

/**
 * What the user can do with one downloadable item: [onInstall] downloads or updates it, [onCancel]
 * stops a running download and [onRemove] deletes it after the user confirms.
 */
class CatalogueRowActions(
    val onInstall: () -> Unit,
    val onCancel: () -> Unit,
    val onRemove: () -> Unit,
)

/**
 * One entry of a download list: a tick box when the entry is on the phone, its name and a line that
 * says what state it is in (size, progress or why a download failed), and one button that fits that
 * state: download, cancel or remove. Removing asks first, naming the entry and the space it frees.
 * [tick] is the state of the tick box and [onTickChange] reports a tap on it.
 */
@Composable
fun CatalogueEntryRow(
    row: CatalogueRow,
    actions: CatalogueRowActions,
    tick: PackTick,
    onTickChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    canRemove: Boolean = true,
) {
    val context = LocalContext.current
    var pendingRemoval by remember { mutableStateOf<PackRemoval?>(null) }
    val itemActions = actionsOf(row)
    val name = catalogueItemName(row.item)
    val progress = (row.transfer as? PackTransfer.Running)?.progress
    AwnPackRow(
        title = name,
        detail = statusText(row),
        // Absent means the list has no tick boxes, so no space is kept for one either.
        tick = if (row.item.isInstalled || tick == PackTick.Absent) tick else PackTick.None,
        onTickChange = onTickChange,
        modifier = modifier,
        isDetailError = row.transfer is PackTransfer.Failed,
        progress = progress,
        download =
            buttonIf(
                ItemAction.Install in itemActions || ItemAction.Update in itemActions,
                if (ItemAction.Update in itemActions) R.string.catalogue_update
                else R.string.catalogue_download,
                actions.onInstall,
            ),
        cancel =
            buttonIf(ItemAction.Cancel in itemActions, R.string.catalogue_cancel, actions.onCancel),
        remove =
            buttonIf(ItemAction.Remove in itemActions && canRemove, R.string.catalogue_remove) {
                pendingRemoval = removalOf(row, name)
            },
    )
    pendingRemoval?.let { removal ->
        AwnConfirmDialog(
            title = stringResource(R.string.catalogue_remove_title, removal.name),
            message =
                stringResource(
                    R.string.catalogue_remove_frees,
                    sizeText(context, removal.freedBytes),
                ),
            confirmLabel = stringResource(R.string.catalogue_remove),
            dismissLabel = stringResource(R.string.catalogue_dialog_cancel),
            onConfirm = {
                pendingRemoval = null
                actions.onRemove()
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun buttonIf(isOffered: Boolean, @StringRes description: Int, onClick: () -> Unit) =
    if (isOffered) PackRowButton(stringResource(description), onClick) else null

/**
 * Returns the name to show for [item]. The page types have names of their own in the app; every
 * other item keeps the name its manifest gives it.
 */
@Composable
fun catalogueItemName(item: CatalogueItem): String {
    val script = ScriptId.entries.firstOrNull { it.packId == item.id }
    val baseName =
        if (item.group == CatalogueGroup.PageType && script != null) {
            stringResource(pageTypeText(script).name)
        } else {
            item.name
        }
    return translationNameWithNote(baseName, item.id)
}

/** The line under an item name: its size, its progress or why the download failed. */
@Composable
internal fun statusText(row: CatalogueRow): String {
    val context = LocalContext.current
    val item = row.item
    return when (val transfer = row.transfer) {
        PackTransfer.Waiting -> stringResource(R.string.catalogue_status_waiting)
        is PackTransfer.Running ->
            stringResource(R.string.catalogue_status_downloading, percentOf(transfer.progress))
        is PackTransfer.Failed -> failureMessage(transfer)
        null ->
            when {
                item.isInstalled && item.hasUpdate ->
                    stringResource(
                        R.string.catalogue_status_update,
                        sizeText(context, item.downloadBytes),
                    )
                item.isInstalled && item.isBundled ->
                    stringResource(R.string.catalogue_status_included)
                item.isInstalled ->
                    stringResource(
                        R.string.catalogue_status_installed,
                        sizeText(context, item.installedBytes ?: item.downloadBytes),
                    )
                else -> sizeText(context, item.downloadBytes)
            }
    }
}

@Composable
internal fun statusColour(row: CatalogueRow) =
    if (row.transfer is PackTransfer.Failed) Awn.field.wrongWord else Awn.field.content

/** Says why a download failed, with the sizes when the phone is too full. */
@Composable
internal fun failureMessage(failure: PackTransfer.Failed): String =
    failureMessage(failure.reason, failure.neededBytes, failure.freeBytes)

/** Says why a download failed; [neededBytes] and [freeBytes] are shown when the phone is full. */
@Composable
internal fun failureMessage(
    reason: PackFailureReason,
    neededBytes: Long?,
    freeBytes: Long?,
): String {
    val context = LocalContext.current
    return when (reason) {
        PackFailureReason.NoSpace ->
            stringResource(
                R.string.catalogue_error_no_space,
                sizeText(context, neededBytes ?: 0L),
                sizeText(context, freeBytes ?: 0L),
            )
        PackFailureReason.NoInternet -> stringResource(R.string.catalogue_error_no_internet)
        PackFailureReason.ServerError -> stringResource(R.string.catalogue_error_server)
        PackFailureReason.Damaged -> stringResource(R.string.catalogue_error_damaged)
        PackFailureReason.WifiOnly -> stringResource(R.string.catalogue_error_wifi)
        PackFailureReason.Other -> stringResource(R.string.catalogue_error_other)
    }
}

private fun percentOf(progress: Float): Int = (progress * PERCENT).toInt()

/** Formats [bytes] in the phone's language, such as "54 MB". */
internal fun sizeText(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes)
