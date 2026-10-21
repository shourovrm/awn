package app.awn.quran.feature.read.quran

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.settings.failureMessage
import app.awn.quran.feature.read.settings.sizeText

/**
 * What the word card shows in place of the play button while the reciter's word audio is not on the
 * phone: the download with its size, the progress, or the reason the last download failed with the
 * download offered again.
 */
@Composable
internal fun WordAudioOfferLine(
    offer: WordAudioOffer,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colour = Awn.field.onBar
    Column(
        modifier.testTag(WORD_AUDIO_OFFER_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (offer) {
            is WordAudioOffer.Downloading ->
                Text(
                    text =
                        stringResource(
                            R.string.catalogue_status_downloading,
                            (offer.progress * PERCENT).toInt(),
                        ),
                    style = AwnTypography.small,
                    color = colour,
                )
            is WordAudioOffer.Download -> DownloadAction(offer.downloadBytes, onDownload)
            is WordAudioOffer.Failed -> {
                Text(
                    text = failureMessage(offer.failure),
                    style = AwnTypography.small,
                    color = colour,
                )
                DownloadAction(offer.downloadBytes, onDownload)
            }
        }
    }
}

@Composable
private fun DownloadAction(downloadBytes: Long, onDownload: () -> Unit) {
    val colour = Awn.field.onBar
    Text(
        text = stringResource(R.string.word_audio_download),
        style = AwnTypography.bodyStrong,
        color = colour,
        modifier =
            Modifier.defaultMinSize(minHeight = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onDownload)
                .padding(top = AwnSpacing.small)
                .testTag(WORD_AUDIO_DOWNLOAD_TAG),
    )
    Text(
        text = sizeText(LocalContext.current, downloadBytes),
        style = AwnTypography.small,
        color = colour,
    )
}

private const val PERCENT = 100
const val WORD_AUDIO_OFFER_TAG = "word-audio-offer"
const val WORD_AUDIO_DOWNLOAD_TAG = "word-audio-download"
