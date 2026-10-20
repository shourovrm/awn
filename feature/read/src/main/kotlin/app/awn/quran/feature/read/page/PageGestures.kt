package app.awn.quran.feature.read.page

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import kotlinx.coroutines.withTimeoutOrNull

internal const val LONG_PRESS_MILLIS = 500L

/**
 * Reports a tap when the finger lifts within [LONG_PRESS_MILLIS], or a long press when it stays
 * down that long. A drag taken over by the pager reports nothing.
 */
internal suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectPageGestures(
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var released = false
        var lifted: androidx.compose.ui.input.pointer.PointerInputChange? = null
        withTimeoutOrNull(LONG_PRESS_MILLIS) {
            lifted = waitForUpOrCancellation()
            released = true
        }
        if (released) {
            if (lifted != null) onTap(down.position)
        } else {
            onLongPress(down.position)
            waitForUpOrCancellation()
        }
    }
}

internal fun ayahOfToken(token: LineToken?): AyahRef? =
    when (token) {
        is LineToken.WordToken -> AyahRef(token.key.surah, token.key.ayah)
        is LineToken.RosetteToken -> token.ayah
        null -> null
    }

/**
 * Reports a tap on a word as [onWordClick] and a long press on a word or end sign as
 * [onAyahLongPress] with the ayah and its bounds moved by [pageOrigin] into window coordinates.
 */
internal fun Modifier.pageGestures(
    geometry: PageGeometry,
    pageOrigin: Offset,
    onWordClick: (WordKey) -> Unit,
    onAyahLongPress: (AyahRef, Rect) -> Unit,
): Modifier =
    pointerInput(geometry, pageOrigin, onWordClick, onAyahLongPress) {
        detectPageGestures(
            onTap = { point ->
                val token = geometry.tokenAt(point.x, point.y)
                if (token is LineToken.WordToken) onWordClick(token.key)
            },
            onLongPress = { point ->
                val ayah = ayahOfToken(geometry.tokenAt(point.x, point.y))
                val bounds = ayah?.let { geometry.ayahBounds(it) }
                if (ayah != null && bounds != null) {
                    onAyahLongPress(ayah, bounds.translate(pageOrigin))
                }
            },
        )
    }
