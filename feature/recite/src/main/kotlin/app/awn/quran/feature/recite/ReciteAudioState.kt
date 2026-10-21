package app.awn.quran.feature.recite

import app.awn.quran.player.PlaybackState

/**
 * What the recite bar shows of the audio player. Anything but [Silent] means the reciter may be
 * heard, so the microphone must not be listening.
 */
enum class ReciteAudioState {
    /** Nothing plays (idle, or the last play failed): the bar shows its normal controls. */
    Silent,

    /** The audio is being fetched; nothing is heard yet, but a stop is offered. */
    Loading,
    Playing,
    Paused,
}

/** What the Listen button does next. */
enum class ListenAction {
    PlayAyah,
    Pause,
    Resume,
}

fun reciteAudioStateOf(playback: PlaybackState): ReciteAudioState =
    when (playback) {
        is PlaybackState.Idle,
        is PlaybackState.Error -> ReciteAudioState.Silent
        is PlaybackState.Downloading -> ReciteAudioState.Loading
        is PlaybackState.Playing -> ReciteAudioState.Playing
        is PlaybackState.Paused -> ReciteAudioState.Paused
    }

fun ReciteAudioState.isActive(): Boolean = this != ReciteAudioState.Silent

/** What the Listen button does, or null while the audio loads and only Stop is useful. */
fun listenActionFor(audio: ReciteAudioState): ListenAction? =
    when (audio) {
        ReciteAudioState.Silent -> ListenAction.PlayAyah
        ReciteAudioState.Loading -> null
        ReciteAudioState.Playing -> ListenAction.Pause
        ReciteAudioState.Paused -> ListenAction.Resume
    }

/**
 * How far the audio has come, 0 to 1, for the thin line on the bar; null when it cannot be known
 * (no duration yet, or nothing playing).
 */
fun audioProgressOf(playback: PlaybackState): Float? =
    when (playback) {
        is PlaybackState.Playing -> fractionOf(playback.positionMs, playback.durationMs)
        is PlaybackState.Paused -> null
        is PlaybackState.Downloading -> playback.fraction
        is PlaybackState.Idle,
        is PlaybackState.Error -> null
    }

private fun fractionOf(positionMs: Long, durationMs: Long?): Float? =
    durationMs?.takeIf { it > 0L }?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) }
