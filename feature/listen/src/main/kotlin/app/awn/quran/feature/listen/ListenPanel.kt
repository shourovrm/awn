package app.awn.quran.feature.listen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

// Paths of the 24 by 24 icon grid used by the mockup.
private const val ICON_GRID = 24f
private const val PREVIOUS_PATH = "M6 6h2v12H6zm3.5 6 8.5 6V6z"
private const val NEXT_PATH = "M16 6h2v12h-2zM6 18l8.5-6L6 6z"
private const val PLAY_PATH = "M8 5v14l11-7z"
private const val PAUSE_PATH = "M6 5h4v14H6zm8 0h4v14h-4z"
private const val REPEAT_PATH = "M7 7h10v3l4-4-4-4v3H5v6h2zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2z"

private const val TRACK_ALPHA = 0.3f
private const val PRIMARY_WEIGHT = 1.6f

/** The player on a green block: wraps [ListenPanelContent] around a [ListenViewModel]. */
@Composable
fun ListenPanel(viewModel: ListenViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    ListenPanelContent(
        state = state,
        onPrevious = viewModel::previous,
        onPlayPause = viewModel::togglePlayPause,
        onNext = viewModel::next,
        onRepeat = viewModel::cycleRepeat,
        modifier = modifier,
    )
}

/** Draws nothing while [state] is not visible. */
@Composable
fun ListenPanelContent(
    state: ListenPanelState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(AwnColors.green)
                .padding(
                    start = AwnSpacing.screenMargin,
                    end = AwnSpacing.screenMargin,
                    top = AwnSpacing.medium,
                    bottom = AwnSpacing.large,
                ),
    ) {
        Text(
            text = state.reciterName,
            style = AwnTypography.title,
            color = AwnColors.paper,
            maxLines = 1,
        )
        ControlRow(state, onPrevious, onPlayPause, onNext, onRepeat)
        if (state.hasError) {
            Text(
                text = stringResource(R.string.listen_error),
                style = AwnTypography.small,
                color = AwnColors.paper,
                modifier = Modifier.padding(top = AwnSpacing.medium),
            )
        } else {
            ProgressBand(state)
        }
    }
}

@Composable
private fun ControlRow(
    state: ListenPanelState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        ControlButton(stringResource(R.string.listen_previous), PREVIOUS_PATH, onPrevious)
        ControlButton(
            description =
                stringResource(
                    if (state.isPlaying) R.string.listen_pause else R.string.listen_play,
                ),
            iconPath = if (state.isPlaying) PAUSE_PATH else PLAY_PATH,
            onClick = onPlayPause,
            isPrimary = true,
            weight = PRIMARY_WEIGHT,
        )
        ControlButton(stringResource(R.string.listen_next), NEXT_PATH, onNext)
        ControlButton(
            description =
                if (state.repeatTimes == 0) {
                    stringResource(R.string.listen_repeat_off)
                } else {
                    pluralStringResource(
                        R.plurals.listen_repeat_times,
                        state.repeatTimes,
                        state.repeatTimes,
                    )
                },
            iconPath = REPEAT_PATH,
            onClick = onRepeat,
            badge = state.repeatTimes.takeIf { it > 0 }?.toString(),
        )
    }
}

@Composable
private fun ProgressBand(state: ListenPanelState) {
    Box(
        modifier =
            Modifier.padding(top = AwnSpacing.medium)
                .fillMaxWidth()
                .height(AwnSpacing.sliderTrackHeight)
                .clip(AwnShapes.control)
                .background(AwnColors.paper.copy(alpha = TRACK_ALPHA)),
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(state.progress).background(AwnColors.ochre),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatClock(state.positionMs),
            style = AwnTypography.small,
            color = AwnColors.paper,
        )
        Text(
            text = stringResource(R.string.listen_ayah_of, state.ayahNumber, state.ayahCount),
            style = AwnTypography.small,
            color = AwnColors.paper,
        )
        Text(
            text = state.durationMs?.let(::formatClock).orEmpty(),
            style = AwnTypography.small,
            color = AwnColors.paper,
        )
    }
}

/** A 48 dp tall icon button: paper with a green icon, or ochre with an ink icon when primary. */
@Composable
private fun RowScope.ControlButton(
    description: String,
    iconPath: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    weight: Float = 1f,
    badge: String? = null,
) {
    val fill = if (isPrimary) AwnColors.ochre else AwnColors.paper
    val iconColor = if (isPrimary) AwnColors.ink else AwnColors.green
    Row(
        modifier =
            Modifier.weight(weight)
                .height(AwnSpacing.buttonHeight)
                .clip(AwnShapes.control)
                .background(fill)
                .semantics { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconPath, iconColor)
        if (badge != null) {
            Text(
                text = badge,
                style = AwnTypography.small,
                color = iconColor,
                modifier = Modifier.padding(start = AwnSpacing.small),
            )
        }
    }
}

@Composable
private fun Icon(pathData: String, color: Color) {
    val path = PathParser().parsePathString(pathData).toPath()
    Canvas(Modifier.size(AwnSpacing.large)) {
        scale(size.width / ICON_GRID, pivot = Offset.Zero) { drawPath(path, color) }
    }
}
