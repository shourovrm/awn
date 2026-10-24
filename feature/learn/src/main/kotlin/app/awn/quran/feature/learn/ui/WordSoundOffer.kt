package app.awn.quran.feature.learn.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.sound.UnitSounds
import app.awn.quran.feature.learn.sound.WordSoundStatus
import kotlinx.coroutines.delay

private const val PERCENT = 100
private const val POLL_MILLIS = 1_000L

/** The sound status of [word], read again every second while its download runs. */
@Composable
internal fun rememberWordSoundStatus(
    word: WordKey,
    sounds: UnitSounds,
    downloadRequests: Int,
): State<WordSoundStatus> =
    produceState<WordSoundStatus>(WordSoundStatus.Ready, word, sounds, downloadRequests) {
        var status = sounds.wordStatus(word)
        value = status
        while (status is WordSoundStatus.Missing && status.progress != null) {
            delay(POLL_MILLIS)
            status = sounds.wordStatus(word)
            value = status
        }
    }

/**
 * What stands in place of the tap-to-hear hint while the word timings are not on the phone: the
 * download with its size, its progress, or the failure with the download offered again.
 */
@Composable
internal fun WordSoundOffer(
    status: WordSoundStatus.Missing,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val progress = status.progress
        if (progress != null) {
            Text(
                text =
                    stringResource(R.string.word_sound_downloading, (progress * PERCENT).toInt()),
                style = AwnTypography.small,
                color = Awn.field.mutedContent,
            )
        } else {
            DownloadAction(status, onDownload)
        }
    }
}

@Composable
private fun DownloadAction(status: WordSoundStatus.Missing, onDownload: () -> Unit) {
    if (status.hasFailed) {
        Text(
            text = stringResource(R.string.word_sound_failed),
            style = AwnTypography.small,
            color = Awn.field.mutedContent,
        )
    }
    Text(
        text = stringResource(R.string.word_sound_download),
        style = AwnTypography.bodyStrong,
        color = Awn.field.content,
        modifier =
            Modifier.defaultMinSize(minHeight = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onDownload)
                .padding(top = AwnSpacing.small),
    )
    Text(
        text = Formatter.formatShortFileSize(LocalContext.current, status.downloadBytes),
        style = AwnTypography.small,
        color = Awn.field.mutedContent,
    )
}
