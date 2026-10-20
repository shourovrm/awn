package app.awn.quran.feature.read.page

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
 * ([sizeBytes], null while unknown), recommends Wi-Fi, and offers the download with its progress
 * ([progress] is 0 to 1 while downloading, [failure] after an error) or keeping the fallback.
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
) {
    val field = Awn.field
    Column(
        modifier.fillMaxWidth().padding(AwnSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(text = pageTypeName, style = AwnTypography.lead, color = field.content)
        val sizeText = sizeBytes?.let { stringResource(R.string.font_prompt_size, megabytesOf(it)) }
        val details = listOfNotNull(sizeText, stringResource(R.string.font_prompt_wifi))
        Text(
            text = details.joinToString(separator = " · "),
            style = AwnTypography.small,
            color = field.content,
        )
        if (failure != null) {
            Text(
                text = failureMessage(failure),
                style = AwnTypography.small,
                color = field.wrongWord,
            )
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = field.buttonFill,
            )
            Text(
                text = stringResource(R.string.font_prompt_progress, (progress * PERCENT).toInt()),
                style = AwnTypography.small,
                color = field.content,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AwnButton(
                label = stringResource(R.string.font_prompt_download),
                onClick = onDownload,
                enabled = progress == null,
            )
            Text(
                text = stringResource(R.string.font_prompt_keep),
                style = AwnTypography.bodyStrong,
                color = field.buttonFill,
                modifier =
                    Modifier.heightIn(min = AwnSpacing.touchTarget)
                        .clickable(role = Role.Button, onClick = onKeepFallback)
                        .padding(vertical = AwnSpacing.small),
            )
        }
    }
}
