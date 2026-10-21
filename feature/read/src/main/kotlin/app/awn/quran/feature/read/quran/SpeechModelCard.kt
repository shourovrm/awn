package app.awn.quran.feature.read.quran

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.models.SpeechModelState
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.settings.failureMessage

private const val PERCENT = 100

/** What the speech download card does besides showing [SpeechModelState]. */
class SpeechModelCardActions(
    val onDownload: () -> Unit,
    val onNotNow: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The card that offers the one-time download recite needs, over the dimmed Quran page, in the style
 * of the print offer: a title, what it costs, Download and Not now. While the download runs it
 * shows progress and Cancel; a failed download shows why above the same two buttons, so the user
 * can try again. [state] is the state of the download and [totalBytes] its whole size.
 */
@Composable
fun SpeechModelCard(
    state: SpeechModelState,
    totalBytes: Long,
    actions: SpeechModelCardActions,
    modifier: Modifier = Modifier,
) {
    FloatingCard(onDismiss = {}, modifier = modifier) {
        if (state is SpeechModelState.Downloading) {
            DownloadingContent(state, actions)
        } else {
            AskingContent(state as? SpeechModelState.Failed, totalBytes, actions)
        }
    }
}

@Composable
private fun AskingContent(
    failure: SpeechModelState.Failed?,
    totalBytes: Long,
    actions: SpeechModelCardActions,
) {
    val field = Awn.field
    val sizeText = megabytesText(totalBytes)
    Text(
        text = stringResource(R.string.speech_offer_title),
        style = AwnTypography.lead,
        color = field.content,
    )
    Text(
        text = stringResource(R.string.speech_offer_body, sizeText),
        style = AwnTypography.small,
        color = field.mutedContent,
    )
    if (failure != null) {
        Text(
            text = failureMessage(failure.reason, failure.neededBytes, failure.freeBytes),
            style = AwnTypography.small,
            color = field.wrongWord,
        )
    }
    Column(Modifier.padding(top = AwnSpacing.small)) {
        AwnButton(
            label = stringResource(R.string.speech_offer_download_sized, sizeText),
            onClick = actions.onDownload,
            modifier = Modifier.fillMaxWidth(),
        )
        CardTextButton(
            label = stringResource(R.string.speech_offer_not_now),
            onClick = actions.onNotNow,
        )
    }
}

@Composable
private fun DownloadingContent(
    state: SpeechModelState.Downloading,
    actions: SpeechModelCardActions,
) {
    val field = Awn.field
    Text(
        text = stringResource(R.string.speech_offer_downloading),
        style = AwnTypography.lead,
        color = field.content,
    )
    LinearProgressIndicator(
        progress = { state.progress },
        modifier = Modifier.fillMaxWidth(),
        color = field.buttonFill,
        trackColor = field.softFill,
    )
    Text(
        text =
            stringResource(
                R.string.speech_offer_progress,
                (state.progress * PERCENT).toInt(),
                megabytesText(state.totalBytes),
            ),
        style = AwnTypography.small,
        color = field.mutedContent,
    )
    Column(Modifier.padding(top = AwnSpacing.small)) {
        CardTextButton(
            label = stringResource(R.string.speech_offer_cancel),
            onClick = actions.onCancel,
        )
    }
}
