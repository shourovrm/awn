package app.awn.quran.feature.read.page

import androidx.compose.ui.graphics.Color
import app.awn.quran.core.data.MyWordMark
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnFieldColors
import app.awn.quran.tracker.WordState

/**
 * The fill behind a word cell in a list view: the recite fill of [state], or the block of a Hidden
 * word, or null for a word with no mark. Same colours as the Mushaf page.
 */
internal fun wordCellFill(state: WordState, field: AwnFieldColors): Color? =
    if (state == WordState.Hidden) {
        field.content.copy(alpha = HIDDEN_BLOCK_ALPHA)
    } else {
        reciteFillColor(state, field)
    }

private const val KNOWN_FILL_ALPHA = 0.26f
private const val LEARNING_FILL_ALPHA = 0.16f

/**
 * The fill behind a word of a form the user knows (green) or is learning (ochre, fainter). The
 * night family lifts the green so it shows on a dark page, as the recite fills do.
 */
internal fun myWordFillColor(mark: MyWordMark, field: AwnFieldColors): Color =
    when (mark) {
        MyWordMark.Known ->
            (if (field.isLight) AwnColors.green else AwnColors.nightConfirmed).copy(
                alpha = KNOWN_FILL_ALPHA,
            )
        MyWordMark.Learning -> AwnColors.ochre.copy(alpha = LEARNING_FILL_ALPHA)
    }
