package app.awn.quran.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

/**
 * The collapsed player: a 72 dp green dock that floats above the bottom edge with a play or pause
 * control, the reciter and ayah as one line, and an expand control.
 *
 * The three labels are passed in because this module holds no user-facing text.
 */
@Composable
fun AwnPlayerDock(
    title: String,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onExpandClick: () -> Unit,
    playLabel: String,
    pauseLabel: String,
    expandLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(horizontal = AwnSpacing.screenMargin)
                .padding(bottom = AwnSpacing.dockFloatGap)
                .fillMaxWidth()
                .height(AwnSpacing.dockHeight)
                .clip(AwnShapes.control)
                .background(AwnColors.green),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockControl(
            description = if (isPlaying) pauseLabel else playLabel,
            onClick = onPlayPauseClick,
            drawGlyph = { if (isPlaying) drawPauseGlyph() else drawPlayGlyph() },
        )
        Text(
            text = title,
            style = AwnTypography.bodyStrong,
            color = AwnColors.paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DockControl(
            description = expandLabel,
            onClick = onExpandClick,
            drawGlyph = { drawExpandGlyph() },
        )
    }
}

@Composable
private fun DockControl(description: String, onClick: () -> Unit, drawGlyph: DrawScope.() -> Unit) {
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.touchTarget)
                .semantics { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(AwnSpacing.large)) { drawGlyph() }
    }
}
