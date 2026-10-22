package app.awn.quran.feature.read.quran

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged

/**
 * The bars stacked at the bottom of the Quran screen. They float over the lists and the Mushaf
 * page, so the screen needs their measured height in pixels to end the lists above them and to
 * slide the page up when the word being recited goes under them. An empty stack reports 0, which
 * tells the screen that no bar covers the page.
 */
@Composable
internal fun BottomBarStack(
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Placed before the caller's modifiers so the reported size is the size of the whole stack.
    Column(
        modifier = Modifier.onSizeChanged { onHeightChanged(it.height) }.then(modifier),
        content = content,
    )
}
