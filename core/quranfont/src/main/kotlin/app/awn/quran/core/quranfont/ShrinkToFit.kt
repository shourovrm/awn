package app.awn.quran.core.quranfont

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Draws [content] on one line at its natural size, scaled down evenly when it is wider than the
 * space it is given. A word printed in a page font cannot change its font size to fit, so a long
 * word or a pair of words is scaled as a whole instead of wrapping or running past its box.
 */
@Composable
fun ShrinkToFit(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val naturalWidthConstraints = constraints.copy(maxWidth = Constraints.Infinity)
        val placeables = measurables.map { measurable ->
            measurable.measure(naturalWidthConstraints)
        }
        val naturalWidth = placeables.maxOfOrNull { it.width } ?: 0
        val naturalHeight = placeables.maxOfOrNull { it.height } ?: 0
        val scale =
            if (naturalWidth > constraints.maxWidth && naturalWidth > 0) {
                constraints.maxWidth.toFloat() / naturalWidth
            } else {
                1f
            }
        layout((naturalWidth * scale).roundToInt(), (naturalHeight * scale).roundToInt()) {
            placeables.forEach { placeable ->
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}
