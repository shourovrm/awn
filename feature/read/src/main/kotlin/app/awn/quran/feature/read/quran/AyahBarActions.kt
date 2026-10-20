package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadIconButton
import app.awn.quran.feature.read.page.SelectionBounds

/** What the buttons of the floating bar over a selected ayah do. */
internal class AyahBarActions(
    val onPlay: () -> Unit,
    val onRecite: () -> Unit,
    val onBookmark: () -> Unit,
    val onTranslation: () -> Unit,
    val onCopy: () -> Unit,
    val onShare: () -> Unit,
)

/**
 * The bar of six icon buttons for the selected ayahs. [bounds] is where they are, in the
 * coordinates of the parent; [placeSelectionBar] puts the bar above the first line, below the
 * selection when there is no room above, and inside the parent. Nothing is drawn until the bounds
 * are known.
 */
@Suppress("LongMethod") // Placement plus six buttons.
@Composable
internal fun AyahActionBar(
    bounds: SelectionBounds?,
    isBookmarked: Boolean,
    actions: AyahBarActions,
    modifier: Modifier = Modifier,
) {
    if (bounds == null) return
    val field = Awn.field
    Box(
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val gap = AwnSpacing.small.roundToPx()
            layout(constraints.maxWidth, constraints.maxHeight) {
                val place =
                    placeSelectionBar(
                        bounds = bounds,
                        barSize = IntSize(placeable.width, placeable.height),
                        screenSize = IntSize(constraints.maxWidth, constraints.maxHeight),
                        gapPx = gap,
                        flipBelowTopPx = (placeable.height + gap).toFloat(),
                    )
                placeable.place(place)
            }
        },
    ) {
        Row(
            modifier =
                Modifier.shadow(AwnSpacing.small, AwnShapes.control)
                    .background(field.buttonFill, AwnShapes.control),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadIconButton(
                R.drawable.ic_read_play,
                stringResource(R.string.ayah_play),
                actions.onPlay,
                tint = field.onButton,
            )
            ReadIconButton(
                R.drawable.ic_read_recite,
                stringResource(R.string.ayah_recite),
                actions.onRecite,
                tint = field.onButton,
            )
            ReadIconButton(
                iconRes =
                    if (isBookmarked) R.drawable.ic_read_bookmark_filled
                    else R.drawable.ic_read_bookmark,
                description =
                    stringResource(
                        if (isBookmarked) R.string.ayah_remove_bookmark else R.string.ayah_bookmark,
                    ),
                onClick = actions.onBookmark,
                tint = field.onButton,
            )
            ReadIconButton(
                R.drawable.ic_read_translate,
                stringResource(R.string.ayah_translation),
                actions.onTranslation,
                tint = field.onButton,
            )
            ReadIconButton(
                R.drawable.ic_read_copy,
                stringResource(R.string.ayah_copy),
                actions.onCopy,
                tint = field.onButton,
            )
            ReadIconButton(
                R.drawable.ic_read_share,
                stringResource(R.string.ayah_share),
                actions.onShare,
                tint = field.onButton,
            )
        }
    }
}

/**
 * The control that brings the reader back to the playing ayah. It shows while audio plays and the
 * reader is elsewhere; pressing it also turns following back on.
 */
@Composable
internal fun BackToPlayingButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val field = Awn.field
    ReadIconButton(
        iconRes = R.drawable.ic_read_playing_ayah,
        description = stringResource(R.string.action_go_to_playing_ayah),
        onClick = onClick,
        modifier =
            modifier
                .shadow(AwnSpacing.small, AwnShapes.control)
                .background(field.buttonFill, AwnShapes.control),
        tint = field.onButton,
    )
}
