package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.GridCell
import app.awn.quran.feature.learn.sound.LocalUnitSounds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A page of the Qaida as a grid: the cells in rows of five read from right to left, each with its
 * transliteration under it. A tap plays the cell; Play all plays them in order and lights the one
 * that sounds. A cell with no installed clip is lit for a moment so the order stays visible.
 */
@Composable
internal fun QaidaGrid(cells: List<GridCell>, modifier: Modifier = Modifier) {
    val sounds = LocalUnitSounds.current
    val scope = rememberCoroutineScope()
    var litIndex by remember(cells) { mutableStateOf<Int?>(null) }
    var playing by remember(cells) { mutableStateOf<Job?>(null) }

    fun stopPlaying() {
        playing?.cancel()
        sounds.stop()
        litIndex = null
        playing = null
    }

    fun playFrom(startIndex: Int, onlyThisCell: Boolean) {
        stopPlaying()
        playing = scope.launch {
            val lastIndex = if (onlyThisCell) startIndex else cells.lastIndex
            for (index in startIndex..lastIndex) {
                litIndex = index
                val hasPlayed = sounds.playClips(cells[index].clipIds)
                if (!hasPlayed) delay(SILENT_CELL_MILLIS)
            }
            litIndex = null
            playing = null
        }
    }

    DisposableEffect(cells) { onDispose { stopPlaying() } }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                cells.chunked(CELLS_PER_ROW).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
                    ) {
                        row.forEachIndexed { column, cell ->
                            val index = rowIndex * CELLS_PER_ROW + column
                            GridCellView(
                                cell = cell,
                                isLit = litIndex == index,
                                onClick = { playFrom(index, onlyThisCell = true) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        repeat(CELLS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        PlayAllRow(
            cells = cells,
            litIndex = litIndex,
            isPlaying = playing != null,
            onPlayAll = { playFrom(0, onlyThisCell = false) },
            onStop = ::stopPlaying,
        )
    }
}

@Composable
private fun GridCellView(
    cell: GridCell,
    isLit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val field = Awn.field
    Column(
        modifier =
            modifier
                .testTag(GRID_CELL_TAG)
                .clip(RoundedCornerShape(AwnSpacing.small))
                .background(if (isLit) field.playingWordFill else field.softFill)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AwnSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = cell.arabic,
            style = AwnTypography.quran,
            color = field.content,
            textAlign = TextAlign.Center,
        )
        // The transliteration keeps its own left-to-right order inside the right-to-left grid.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = cell.transliteration,
                style = AwnTypography.small,
                color = field.content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlayAllRow(
    cells: List<GridCell>,
    litIndex: Int?,
    isPlaying: Boolean,
    onPlayAll: () -> Unit,
    onStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        AwnButton(
            label =
                if (isPlaying) stringResource(R.string.unit_grid_stop)
                else stringResource(R.string.unit_grid_play_all),
            onClick = if (isPlaying) onStop else onPlayAll,
            modifier = Modifier.fillMaxWidth().testTag(PLAY_ALL_TAG),
        )
        if (litIndex != null) {
            MutedLine(
                stringResource(
                    R.string.unit_grid_progress,
                    cells[litIndex].transliteration,
                    litIndex + 1,
                    cells.size,
                ),
            )
        }
    }
}

private const val CELLS_PER_ROW = 5

// How long a cell without a sound stays lit during Play all.
private const val SILENT_CELL_MILLIS = 700L

internal const val GRID_CELL_TAG = "grid-cell"
internal const val PLAY_ALL_TAG = "grid-play-all"
