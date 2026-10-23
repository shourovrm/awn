package app.awn.quran.feature.read.quran

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.page.FontPackStatus
import app.awn.quran.feature.read.page.pageTypeScripts
import app.awn.quran.feature.read.page.pageTypeText
import app.awn.quran.feature.read.settings.failureMessage

private const val BYTES_PER_MEGABYTE = 1_000_000.0
private const val PERCENT = 100
private val ROW_GAP = 4.dp
private const val TICK_RING_ALPHA = 0.45f

/** What the print offer card needs besides its state and the three callbacks. */
internal class PrintOfferActions(
    val onChoose: (ScriptId) -> Unit,
    val onDownload: () -> Unit,
    val onNotNow: () -> Unit,
    val onKeepReading: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The one-time card that offers the printed Mushaf over the dimmed first Quran page. It asks which
 * of the prints to download, then, once the download runs, shows its progress with Keep reading and
 * Cancel. A failure of a ticked print shows in the asking state.
 */
@Composable
internal fun PrintOfferCard(
    offer: PrintOfferState,
    statuses: Map<ScriptId, FontPackStatus>,
    actions: PrintOfferActions,
    modifier: Modifier = Modifier,
) {
    FloatingCard(onDismiss = {}, modifier = modifier) {
        val downloading = offer.downloading
        if (downloading.isNotEmpty()) {
            DownloadingContent(downloading, statuses, actions)
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
    Text(
        text = stringResource(R.string.print_offer_body),
        style = AwnTypography.small,
        color = field.mutedContent,
    )
    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        pageTypeScripts.forEach { script ->
            PrintRow(
                script = script,
                isSelected = script in offer.choices,
                sizeBytes = statuses[script]?.downloadBytes,
                onToggle = { actions.onChoose(script) },
            )
        }
    }
    val failure =
        pageTypeScripts
            .filter { it in offer.choices }
            .firstNotNullOfOrNull { statuses[it]?.failure }
    if (failure != null) {
        Text(text = failureMessage(failure), style = AwnTypography.small, color = field.wrongWord)
    }
    val sizeBytes = printOfferDownloadBytes(offer, statuses)
    val downloadLabel =
        if (sizeBytes == 0L) {
            stringResource(R.string.print_offer_download)
        } else {
            stringResource(R.string.print_offer_download_sized, megabytesText(sizeBytes))
        }
    Column(Modifier.padding(top = AwnSpacing.small)) {
        AwnButton(
            label = downloadLabel,
            onClick = actions.onDownload,
            modifier = Modifier.fillMaxWidth(),
            enabled = offer.choices.isNotEmpty(),
        )
        CardTextButton(
            label = stringResource(R.string.print_offer_not_now),
            onClick = actions.onNotNow,
        )
    }
}

@Composable
private fun PrintRow(
    script: ScriptId,
    isSelected: Boolean,
    sizeBytes: Long?,
    onToggle: () -> Unit,
) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.giant)
                .clip(AwnShapes.menu)
                .then(if (isSelected) Modifier.background(field.softFill) else Modifier)
                .toggleable(
                    value = isSelected,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .padding(horizontal = AwnSpacing.medium, vertical = AwnSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TickBox(isSelected)
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(pageTypeText(script).name),
                style = AwnTypography.body,
                color = field.content,
            )
            Text(
                text = stringResource(printDetailOf(script)),
                style = AwnTypography.small,
                color = field.mutedContent,
            )
        }
        if (sizeBytes != null) {
            Text(
                text = megabytesText(sizeBytes),
                style = AwnTypography.small,
                color = field.mutedContent,
            )
        }
    }
}

@Composable
private fun TickBox(isSelected: Boolean) {
    val field = Awn.field
    val boxModifier =
        if (isSelected) {
            Modifier.background(field.buttonFill, AwnShapes.tickBox)
        } else {
            Modifier.border(
                AwnSpacing.switchKnobRing,
                field.content.copy(alpha = TICK_RING_ALPHA),
                AwnShapes.tickBox,
            )
        }
    Box(
        modifier = Modifier.size(AwnSpacing.tickBoxSize).then(boxModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                painter = painterResource(UiR.drawable.ic_ui_check),
                contentDescription = null,
                tint = field.onButton,
                modifier = Modifier.size(AwnSpacing.smallIconSize),
            )
        }
    }
}

@Composable
private fun DownloadingContent(
    downloading: Set<ScriptId>,
    statuses: Map<ScriptId, FontPackStatus>,
    actions: PrintOfferActions,
) {
    val field = Awn.field
    val titlePrint = pageTypeScripts.first { it in downloading }
    // The bar averages the prints so it moves while several downloads run at once.
    val progress = downloading.map { statuses[it]?.progress ?: 0f }.average().toFloat()
    Text(
        text =
            stringResource(
                R.string.print_offer_downloading,
                stringResource(pageTypeText(titlePrint).name),
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
    val sizeBytes = downloading.sumOf { statuses[it]?.downloadBytes ?: 0L }
    val progressLine =
        if (sizeBytes == 0L) {
            stringResource(R.string.print_offer_percent, (progress * PERCENT).toInt())
        } else {
            stringResource(
                R.string.print_offer_progress,
                (progress * PERCENT).toInt(),
                megabytesText(sizeBytes),
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

@StringRes
private fun printDetailOf(script: ScriptId): Int =
    when (script) {
        ScriptId.V1 -> R.string.print_offer_detail_v1
        ScriptId.V4Tajweed -> R.string.print_offer_detail_v4
        ScriptId.IndoPak15 -> R.string.print_offer_detail_indopak
        else -> R.string.print_offer_detail_v2
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
