package app.awn.quran.index

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.awn.quran.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import kotlin.math.roundToInt

/**
 * The juz rail at the right edge: the numbers in [JuzRail.landmarks] down a strip, the current juz
 * on a filled marker. Touching or dragging anywhere on the strip calls [onJump] with the juz under
 * the finger and [onDragStateChange] tells when a touch starts and ends (see
 * [JuzRail.juzAtFraction]), so a drag scrubs through all 30.
 */
@Composable
internal fun JuzRailView(
    currentJuz: Int,
    onJump: (juz: Int) -> Unit,
    onDragStateChange: (isDragging: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val markerSize = AwnSpacing.railMarkerSize
    val description = stringResource(R.string.index_rail_description)
    val juzState = stringResource(R.string.index_juz_header, currentJuz)
    BoxWithConstraints(
        modifier =
            modifier.width(AwnSpacing.railWidth).fillMaxHeight().semantics {
                contentDescription = description
                stateDescription = juzState
                progressBarRangeInfo =
                    ProgressBarRangeInfo(
                        current = currentJuz.toFloat(),
                        range = 1f..JuzStarts.JUZ_COUNT.toFloat(),
                        steps = JuzStarts.JUZ_COUNT - 2,
                    )
                setProgress { target ->
                    onJump(target.roundToInt().coerceIn(1, JuzStarts.JUZ_COUNT))
                    true
                }
            },
    ) {
        val density = LocalDensity.current
        val trackHeight = maxHeight - markerSize
        val markerPx = with(density) { markerSize.toPx() }
        val trackPx = with(density) { trackHeight.toPx() }
        Box(
            Modifier.fillMaxHeight()
                .width(AwnSpacing.railWidth)
                .railTouch(markerPx, trackPx, onJump, onDragStateChange),
        )
        JuzRail.landmarks.forEach { landmark ->
            RailMark(
                label = landmark,
                top = trackHeight * JuzRail.fractionOfJuz(landmark),
                markerSize = markerSize,
                isCurrent = false,
            )
        }
        RailMark(
            label = currentJuz,
            top = trackHeight * JuzRail.fractionOfJuz(currentJuz),
            markerSize = markerSize,
            isCurrent = true,
        )
    }
}

@Composable
private fun RailMark(label: Int, top: Dp, markerSize: Dp, isCurrent: Boolean) {
    val field = Awn.field
    val fill = if (isCurrent) Modifier.background(field.buttonFill) else Modifier
    Box(
        modifier =
            Modifier.offset(y = top.coerceAtLeast(0.dp))
                .size(markerSize)
                .clip(AwnShapes.tickBox)
                .then(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.toString(),
            style = AwnTypography.caption.copy(fontWeight = AwnTypography.bodyStrong.fontWeight),
            color = if (isCurrent) field.onButton else field.buttonFill,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Touch handling for the rail: a touch or drag names the juz under the finger, [markerPx] being the
 * marker height and [trackPx] the height the marker centres travel over. The jump is called again
 * only when the juz changes.
 */
private fun Modifier.railTouch(
    markerPx: Float,
    trackPx: Float,
    onJump: (juz: Int) -> Unit,
    onDragStateChange: (isDragging: Boolean) -> Unit,
): Modifier =
    pointerInput(trackPx) {
        awaitEachGesture {
            var lastJumpedJuz = 0
            fun jumpTo(y: Float) {
                val juz = JuzRail.juzAtFraction((y - markerPx / 2) / trackPx)
                if (juz != lastJumpedJuz) {
                    lastJumpedJuz = juz
                    onJump(juz)
                }
            }
            val down = awaitFirstDown()
            // The rail must not move under the finger while the list scrolls.
            onDragStateChange(true)
            jumpTo(down.position.y)
            down.consume()
            do {
                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                jumpTo(change.position.y)
                change.consume()
            } while (change.pressed)
            onDragStateChange(false)
        }
    }
