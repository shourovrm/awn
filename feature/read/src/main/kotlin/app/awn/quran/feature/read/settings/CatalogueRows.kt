package app.awn.quran.feature.read.settings

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadIconButton
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
 * One row of the download catalogue: the name, a line that says what state the item is in (size,
 * progress or the reason a download failed) and the buttons that fit that state. [leadingControls]
 * and [trailingControls] let a page add its own buttons, such as the switch of a translation, on
 * either side of the download buttons.
 */
@Composable
fun CatalogueItemRow(
    row: CatalogueRow,
    actions: CatalogueRowActions,
    modifier: Modifier = Modifier,
    leadingControls: @Composable RowScope.() -> Unit = {},
    trailingControls: @Composable RowScope.() -> Unit = {},
) {
    var isRemoveConfirmationOpen by remember { mutableStateOf(false) }
    val itemActions = actionsOf(row)
    val name = catalogueItemName(row.item)
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowHeight),
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = AwnSpacing.small)) {
                Text(text = name, style = AwnTypography.body, color = Awn.field.content)
                Text(
                    text = statusText(row),
                    style = AwnTypography.small,
                    color = statusColour(row),
                )
            }
            leadingControls()
            ItemButtons(itemActions, actions) { isRemoveConfirmationOpen = true }
            trailingControls()
        }
        val transfer = row.transfer
        if (transfer is PackTransfer.Running) {
            LinearProgressIndicator(
                progress = { transfer.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Awn.field.buttonFill,
            )
        }
    }
    if (isRemoveConfirmationOpen) {
        RemoveConfirmation(
            name = name,
            group = row.item.group,
            onConfirm = {
                isRemoveConfirmationOpen = false
                actions.onRemove()
            },
            onDismiss = { isRemoveConfirmationOpen = false },
        )
    }
}

/** The download, update, cancel and remove buttons that fit [itemActions]. */
@Composable
private fun ItemButtons(
    itemActions: List<ItemAction>,
    actions: CatalogueRowActions,
    onRemoveRequested: () -> Unit,
) {
    if (ItemAction.Install in itemActions) {
        ReadIconButton(
            iconRes = R.drawable.ic_read_download,
            description = stringResource(R.string.catalogue_download),
            onClick = actions.onInstall,
        )
    }
    if (ItemAction.Update in itemActions) {
        ReadIconButton(
            iconRes = R.drawable.ic_read_download,
            description = stringResource(R.string.catalogue_update),
            onClick = actions.onInstall,
        )
    }
    if (ItemAction.Cancel in itemActions) {
        ReadIconButton(
            iconRes = R.drawable.ic_read_close,
            description = stringResource(R.string.catalogue_cancel),
            onClick = actions.onCancel,
        )
    }
    if (ItemAction.Remove in itemActions) {
        ReadIconButton(
            iconRes = R.drawable.ic_read_delete,
            description = stringResource(R.string.catalogue_remove),
            onClick = onRemoveRequested,
        )
    }
}

/** Asks before a download is deleted; the text names the item and says it can come back. */
@Composable
private fun RemoveConfirmation(
    name: String,
    group: CatalogueGroup,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalogue_remove_title, name)) },
        text = { Text(stringResource(removeMessageOf(group))) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.catalogue_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.catalogue_dialog_cancel))
            }
        },
    )
}

private fun removeMessageOf(group: CatalogueGroup): Int =
    when (group) {
        CatalogueGroup.Translation -> R.string.catalogue_remove_translation
        CatalogueGroup.PageType -> R.string.catalogue_remove_page_type
        CatalogueGroup.WordByWord -> R.string.catalogue_remove_word_by_word
        CatalogueGroup.Transliteration -> R.string.catalogue_remove_transliteration
    }

/**
 * Returns the name to show for [item]. The three page types have names of their own in the app;
 * every other item keeps the name its manifest gives it.
 */
@Composable
fun catalogueItemName(item: CatalogueItem): String {
    val script = ScriptId.entries.firstOrNull { it.packId == item.id }
    return if (item.group == CatalogueGroup.PageType && script != null) {
        stringResource(pageTypeText(script).name)
    } else {
        item.name
    }
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
internal fun failureMessage(failure: PackTransfer.Failed): String {
    val context = LocalContext.current
    return when (failure.reason) {
        PackFailureReason.NoSpace ->
            stringResource(
                R.string.catalogue_error_no_space,
                sizeText(context, failure.neededBytes ?: 0L),
                sizeText(context, failure.freeBytes ?: 0L),
            )
        PackFailureReason.NoInternet -> stringResource(R.string.catalogue_error_no_internet)
        PackFailureReason.ServerError -> stringResource(R.string.catalogue_error_server)
        PackFailureReason.Damaged -> stringResource(R.string.catalogue_error_damaged)
        PackFailureReason.Other -> stringResource(R.string.catalogue_error_other)
    }
}

private fun percentOf(progress: Float): Int = (progress * PERCENT).toInt()

/** Formats [bytes] in the phone's language, such as "54 MB". */
internal fun sizeText(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes)
