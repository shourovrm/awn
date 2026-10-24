package app.awn.quran.feature.read.quran

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.page.FontPackStatus
import app.awn.quran.feature.read.page.pageTypeScripts
import app.awn.quran.feature.read.page.pageTypeText
import app.awn.quran.feature.read.settings.catalogueItemName
import app.awn.quran.feature.read.settings.failureMessage

private const val BYTES_PER_MEGABYTE = 1_000_000.0
private const val PERCENT = 100
private val ROW_GAP = 4.dp

/** What the welcome card needs besides its state. */
internal class PrintOfferActions(
    val onChoose: (ScriptId) -> Unit,
    val onToggleStarter: () -> Unit,
    val onDownload: () -> Unit,
    val onNotNow: () -> Unit,
    val onKeepReading: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The one-time welcome card over the dimmed first Quran page. It asks which Mushaf edition to
 * download and whether to add a translation with word-by-word meanings, then, once the download
 * runs, shows its progress with Keep reading and Cancel. A failure shows in the asking state.
 */
@Composable
internal fun PrintOfferCard(
    offer: PrintOfferState,
    statuses: Map<ScriptId, FontPackStatus>,
    actions: PrintOfferActions,
    modifier: Modifier = Modifier,
) {
    FloatingCard(onDismiss = {}, modifier = modifier) {
        if (offer.isDownloading) {
            DownloadingContent(offer, statuses, actions)
        } else {
            ChoiceContent(offer, statuses, actions)
        }
    }
}

@Composable
private fun ChoiceContent(
    offer: PrintOfferState,
    statuses: Map<ScriptId, FontPackStatus>,
    actions: PrintOfferActions,
) {
    val field = Awn.field
    Text(
        text = stringResource(R.string.print_offer_title),
        style = AwnTypography.lead,
        color = field.content,
    )
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        pageTypeScripts.forEach { script ->
            EditionRow(
                script = script,
                isSelected = script == offer.selectedScript,
                sizeBytes = statuses[script]?.downloadBytes,
                onSelect = { actions.onChoose(script) },
            )
        }
    }
    if (offer.offersStarter) {
        StarterRow(
            translationName = catalogueItemName(offer.starterRows.first().item),
            isTicked = offer.isStarterTicked,
            sizeBytes =
                offer.starterRows.filterNot { it.item.isInstalled }.sumOf { it.item.downloadBytes },
            onToggle = actions.onToggleStarter,
        )
    }
    Text(
        text = stringResource(R.string.print_offer_body),
        style = AwnTypography.small,
        color = field.mutedContent,
    )
    printOfferFailure(offer, statuses)?.let { failure ->
        Text(text = failureMessage(failure), style = AwnTypography.small, color = field.wrongWord)
    }
    ChoiceButtons(printOfferDownloadBytes(offer, statuses), actions)
}

@Composable
private fun ChoiceButtons(downloadBytes: Long, actions: PrintOfferActions) {
    val downloadLabel =
        if (downloadBytes == 0L) {
            stringResource(R.string.print_offer_download)
        } else {
            stringResource(R.string.print_offer_download_sized, megabytesText(downloadBytes))
        }
    Column(Modifier.padding(top = AwnSpacing.small)) {
        AwnButton(
            label = downloadLabel,
            onClick = actions.onDownload,
            modifier = Modifier.fillMaxWidth(),
        )
        CardTextButton(
            label = stringResource(R.string.print_offer_not_now),
            onClick = actions.onNotNow,
        )
    }
}

@Composable
private fun DownloadingContent(
    offer: PrintOfferState,
    statuses: Map<ScriptId, FontPackStatus>,
    actions: PrintOfferActions,
) {
    val field = Awn.field
    val progress = printOfferProgress(offer, statuses)
    Text(
        text =
            stringResource(
                R.string.print_offer_downloading,
                stringResource(pageTypeText(offer.selectedScript).name),
            ),
        style = AwnTypography.lead,
        color = field.content,
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
        color = field.buttonFill,
        trackColor = field.softFill,
    )
    val progressLine =
        if (offer.startedBytes == 0L) {
            stringResource(R.string.print_offer_percent, (progress * PERCENT).toInt())
        } else {
            stringResource(
                R.string.print_offer_progress,
                (progress * PERCENT).toInt(),
                megabytesText(offer.startedBytes),
            )
        }
    Text(text = progressLine, style = AwnTypography.small, color = field.mutedContent)
    Row(
        Modifier.fillMaxWidth().padding(top = AwnSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        CardTextButton(
            label = stringResource(R.string.print_offer_cancel),
            onClick = actions.onCancel,
            modifier = Modifier.weight(1f),
        )
        AwnButton(
            label = stringResource(R.string.print_offer_keep_reading),
            onClick = actions.onKeepReading,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The size in whole decimal megabytes in the app language, such as "137 MB" or "১৩৭ এমবি". */
@Composable
internal fun megabytesText(bytes: Long): String =
    stringResource(R.string.print_offer_size, Math.round(bytes / BYTES_PER_MEGABYTE).toInt())

/**
 * Says once, with a short message, that a print download started from the card failed after the
 * card was closed. The count starts at 0 and the message shows only when it rises.
 */
@Composable
internal fun PrintFailureNotice(closedFailureCount: Int) {
    val context = LocalContext.current
    val message = stringResource(R.string.print_offer_failed)
    LaunchedEffect(closedFailureCount) {
        if (closedFailureCount > 0) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
