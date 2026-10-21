package app.awn.quran.feature.read.page

import androidx.compose.ui.graphics.Color
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
