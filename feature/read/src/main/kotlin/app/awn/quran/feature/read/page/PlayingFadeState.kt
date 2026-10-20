package app.awn.quran.feature.read.page

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.awn.quran.core.data.AyahRef

/** How long the playing-ayah highlight takes to hand over from one ayah to the next. */
internal const val PLAYING_FADE_MS = 500

/**
 * Animates the playing-ayah highlight of one page. [follow] is called with the page's playing ayah;
 * the previous ayah keeps its fill while it fades out, so there is never a moment with no highlight
 * between two ayahs. [snapshot] is read while drawing, so a change redraws the page and does not
 * recompose it.
 */
@Stable
internal class PlayingFadeState(initial: AyahRef?) {
    private var current by mutableStateOf(initial)
    private var outgoing by mutableStateOf<AyahRef?>(null)
    private val progress = Animatable(1f)

    /** The fills to draw now. */
    fun snapshot(): PlayingFade {
        val fading = outgoing
        return if (fading == null) {
            PlayingFade(incoming = current)
        } else {
            PlayingFade(
                incoming = current,
                incomingAlpha = progress.value,
                outgoing = fading,
                outgoingAlpha = 1f - progress.value,
            )
        }
    }

    suspend fun follow(next: AyahRef?) {
        if (next == current) return
        outgoing = current
        current = next
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PLAYING_FADE_MS))
        outgoing = null
    }
}

@Composable
internal fun rememberPlayingFade(playing: AyahRef?): PlayingFadeState {
    val state = remember { PlayingFadeState(playing) }
    LaunchedEffect(playing) { state.follow(playing) }
    return state
}
