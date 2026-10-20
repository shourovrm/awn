package app.awn.quran.feature.read.page

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import kotlinx.coroutines.withTimeoutOrNull

internal const val LONG_PRESS_MILLIS = 500L

/** What a page reports about the finger. Every call is one user action. */
internal class PageTouchActions(
    /** A tap anywhere on the page, reported when the finger lifts. */
    val onTap: () -> Unit,
    /** A tap that landed on a word, reported before [onTap]. */
    val onWordClick: (WordKey) -> Unit,
    /** The finger stayed down; [ayah] is the nearest ayah to it. */
    val onLongPress: (ayah: AyahRef) -> Unit,
    /** The finger moved on after a long press; the range runs from the first ayah to this one. */
    val onRangeDrag: (AyahRange) -> Unit,
    /** The finger lifted after a long press. */
    val onRangeEnd: () -> Unit,
)

/**
 * Reports a tap the moment the finger lifts, so nothing waits for a second tap (the page has no
 * double-tap gesture). A finger that stays down for [LONG_PRESS_MILLIS] is a long press, and its
 * moves are reported until it lifts. A drag taken over by the pager reports nothing.
 */
internal suspend fun PointerInputScope.detectPageGestures(
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    onLongPressDrag: (Offset) -> Unit = {},
    onLongPressEnd: () -> Unit = {},
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var lifted: PointerInputChange? = null
        var isReleased = false
        withTimeoutOrNull(LONG_PRESS_MILLIS) {
            lifted = waitForUpOrCancellation()
            isReleased = true
        }
        if (isReleased) {
            // Consumed, so a parent tap handler does not toggle the bars a second time.
            lifted?.let { up ->
                up.consume()
                onTap(down.position)
            }
        } else {
            onLongPress(down.position)
            followUntilLifted(down.id, onLongPressDrag)
            onLongPressEnd()
        }
    }
}

/** Reports each move of the pointer [pointerId] until it lifts, consuming what it reports. */
private suspend fun AwaitPointerEventScope.followUntilLifted(
    pointerId: PointerId,
    onDrag: (Offset) -> Unit,
) {
    var isDown = true
    while (isDown) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
        isDown = change != null && change.pressed && !change.changedToUpIgnoreConsumed()
        if (change != null) {
            val hasMoved = isDown && change.position != change.previousPosition
            if (!isDown || hasMoved) change.consume()
            if (hasMoved) onDrag(change.position)
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
 * Connects the finger to [actions]. A tap on a word is also a word click; a long press anywhere
 * selects the nearest ayah, and dragging from it extends the selection to a range.
 */
internal fun Modifier.pageGestures(geometry: PageGeometry, actions: PageTouchActions): Modifier =
    pointerInput(geometry, actions) {
        var anchor: AyahRef? = null
        var lastReported: AyahRef? = null
        detectPageGestures(
            onTap = { point ->
                val token = geometry.tokenAt(point.x, point.y)
                if (token is LineToken.WordToken) actions.onWordClick(token.key)
                actions.onTap()
            },
            onLongPress = { point ->
                val ayah = geometry.nearestAyahAt(point.x, point.y)
                anchor = ayah
                lastReported = ayah
                if (ayah != null) actions.onLongPress(ayah)
            },
            onLongPressDrag = { point ->
                val start = anchor
                val current = geometry.nearestAyahAt(point.x, point.y)
                if (start != null && current != null && current != lastReported) {
                    lastReported = current
                    actions.onRangeDrag(AyahRange.between(start, current))
                }
            },
            onLongPressEnd = {
                if (anchor != null) actions.onRangeEnd()
                anchor = null
                lastReported = null
            },
        )
    }
