package app.awn.quran.feature.read.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.settings.failureMessage
import java.util.Locale

private const val BYTES_PER_MEGABYTE = 1_000_000.0
private const val PERCENT = 100

/** Formats [bytes] as megabytes with one decimal ("137.1"); the manifests count in decimal MB. */
internal fun megabytesOf(bytes: Long): String =
    String.format(Locale.ROOT, "%.1f", bytes / BYTES_PER_MEGABYTE)

/**
 * The prompt shown over a page that is drawn in the fallback font because the font pack of the
 * chosen page type is missing. It names the page type ([pageTypeName]) and its download size
 * ([sizeBytes], null while unknown), recommends Wi-Fi, and offers the download or keeping the
 * fallback.
 *
 * While a download runs ([progress] is 0 to 1) the Download button gives way to the progress bar
 * and, when [onCancel] is given, a Cancel action, which makes the prompt two lines tall instead of
 * four so it hides as little of the page as possible. [failure] shows after an error.
 */
@Composable
fun FontDownloadPrompt(
    pageTypeName: String,
    sizeBytes: Long?,
    progress: Float?,
    failure: PackTransfer.Failed?,
    onDownload: () -> Unit,
    onKeepFallback: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val field = Awn.field
    Column(
        // Drawn over the page, so it needs the page colour behind it to stay readable.
        modifier.fillMaxWidth().background(field.background).padding(AwnSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(text = pageTypeName, style = AwnTypography.lead, color = field.content)
        if (progress != null) {
            DownloadProgress(progress = progress, onCancel = onCancel)
        } else {
            DownloadChoice(
                sizeBytes = sizeBytes,
                failure = failure,
                onDownload = onDownload,
                onKeepFallback = onKeepFallback,
            )
        }
    }
}

@Composable
private fun DownloadChoice(
    sizeBytes: Long?,
    failure: PackTransfer.Failed?,
    onDownload: () -> Unit,
    onKeepFallback: () -> Unit,
) {
    val field = Awn.field
    val sizeText = sizeBytes?.let { stringResource(R.string.font_prompt_size, megabytesOf(it)) }
    val details = listOfNotNull(sizeText, stringResource(R.string.font_prompt_wifi))
    Text(
        text = details.joinToString(separator = " · "),
        style = AwnTypography.small,
        color = field.content,
    )
    if (failure != null) {
        Text(text = failureMessage(failure), style = AwnTypography.small, color = field.wrongWord)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AwnButton(label = stringResource(R.string.font_prompt_download), onClick = onDownload)
        PromptTextAction(
            label = stringResource(R.string.font_prompt_keep),
            onClick = onKeepFallback,
        )
    }
}

@Composable
private fun DownloadProgress(progress: Float, onCancel: (() -> Unit)?) {
    val field = Awn.field
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
        color = field.buttonFill,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.font_prompt_progress, (progress * PERCENT).toInt()),
            style = AwnTypography.small,
            color = field.content,
            modifier = Modifier.weight(1f),
        )
        if (onCancel != null) {
            PromptTextAction(
                label = stringResource(R.string.font_prompt_cancel),
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun PromptTextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = AwnTypography.bodyStrong,
        color = Awn.field.buttonFill,
        modifier =
            Modifier.heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AwnSpacing.small),
    )
}
