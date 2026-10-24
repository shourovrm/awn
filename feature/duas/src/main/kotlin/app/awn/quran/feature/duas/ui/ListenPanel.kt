package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.player.PlaybackOptions
import app.awn.quran.player.PlaybackState

internal const val LISTEN_PLAY_TAG = "dua-listen-play"
internal const val LISTEN_REPEAT_TAG = "dua-listen-repeat"
internal const val LISTEN_RECITER_TAG = "dua-listen-reciter"

/**
 * The ayah of [dua] that is playing or paused now, or null when the player is on something else.
 */
internal fun PlaybackState.ayahOf(dua: Dua): AyahRef? =
    when (this) {
        is PlaybackState.Playing -> ayah.takeIf { it in dua.ayahs }
        is PlaybackState.Paused -> ayah.takeIf { it in dua.ayahs }
        else -> null
    }

/** The word the reciter is saying, when the player is playing [dua] and knows the word. */
internal fun PlaybackState.playingWordOf(dua: Dua): WordKey? {
    val playing = this as? PlaybackState.Playing
    val wordPosition = playing?.wordPosition
    return if (playing == null || wordPosition == null || playing.ayah !in dua.ayahs) {
        null
    } else {
        WordKey(playing.ayah.surah, playing.ayah.ayah, wordPosition)
    }
}

/** What the buttons of the Listen panel do. */
internal class ListenPanelActions(
    val onPlay: () -> Unit,
    val onStop: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onChooseReciter: () -> Unit,
)

/**
 * The Listen controls, as on the Mushaf's audio bar: play or stop, the reciter's name, which opens
 * the reciter sheet, with the word being said under it, and the repeat count. [wordCountOfAyah]
 * gives the length of the playing ayah for "Word 7 of 11".
 */
@Composable
internal fun ListenPanel(
    dua: Dua,
    reciterName: String,
    playback: PlaybackState,
    options: PlaybackOptions,
    wordCountOfAyah: (AyahRef) -> Int,
    actions: ListenPanelActions,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    val isBusy = playback.ayahOf(dua) != null || playback is PlaybackState.Downloading
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AwnShapes.control)
                .background(field.softFill)
                .padding(horizontal = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AwnIconButton(
            icon = if (isBusy) R.drawable.ic_duas_stop else R.drawable.ic_duas_play,
            contentDescription =
                stringResource(if (isBusy) R.string.duas_stop else R.string.duas_play),
            onClick = if (isBusy) actions.onStop else actions.onPlay,
            tint = field.buttonFill,
            modifier = Modifier.testTag(LISTEN_PLAY_TAG),
        )
        Row(
            Modifier.weight(1f)
                .heightIn(min = AwnSpacing.touchTarget)
                .clip(AwnShapes.control)
                .clickable(role = Role.Button, onClick = actions.onChooseReciter)
                .padding(horizontal = AwnSpacing.small)
                .testTag(LISTEN_RECITER_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = reciterName,
                    style = AwnTypography.bodyStrong,
                    color = field.content,
                    maxLines = 1,
                )
                ListenStatusLine(dua, playback, wordCountOfAyah)
            }
            Icon(
                painter = painterResource(R.drawable.ic_duas_dropdown),
                contentDescription = stringResource(R.string.duas_choose_reciter),
                tint = field.content,
                modifier = Modifier.size(AwnSpacing.iconSize),
            )
        }
        RepeatButton(count = options.repeatEachAyah, onClick = actions.onCycleRepeat)
    }
}

@Composable
private fun ListenStatusLine(
    dua: Dua,
    playback: PlaybackState,
    wordCountOfAyah: (AyahRef) -> Int,
) {
    val playingWord = playback.playingWordOf(dua)
    val text =
        when {
            playingWord != null ->
                stringResource(
                    R.string.duas_word_position,
                    playingWord.wordPosition,
                    wordCountOfAyah(AyahRef(playingWord.surah, playingWord.ayah)),
                )
            playback is PlaybackState.Downloading -> stringResource(R.string.duas_downloading)
            playback is PlaybackState.Error -> stringResource(R.string.duas_play_failed)
            else -> null
        }
    if (text != null) {
        Text(text = text, style = AwnTypography.small, color = Awn.field.content, maxLines = 1)
    }
}

@Composable
private fun RepeatButton(count: Int, onClick: () -> Unit) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(LISTEN_REPEAT_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small / 2),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_duas_repeat),
            contentDescription = stringResource(R.string.duas_repeat),
            tint = field.content,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
        Text(
            text = stringResource(R.string.duas_repeat_value, count),
            style = AwnTypography.bodyStrong,
            color = field.content,
        )
    }
}
