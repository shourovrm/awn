package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.MouthArea
import app.awn.quran.feature.learn.ui.MouthShapes.Anchor

/**
 * A side view of the head with the mouth, throat and nose cut open, for the lesson on where letters
 * are made. The areas in [highlighted] are filled in the button colour and their labels are bold;
 * the other labels stay quiet. The head faces right, as in the source drawing, so the throat label
 * sits on the left and the others on the right.
 */
@Composable
internal fun MouthDiagram(highlighted: Set<MouthArea>, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.mouth_diagram_description)
    val field = Awn.field
    val colours =
        FigureColours(
            body = field.content.copy(alpha = BODY_ALPHA),
            outline = field.content.copy(alpha = OUTLINE_ALPHA),
            highlight = field.buttonFill.copy(alpha = HIGHLIGHT_ALPHA),
            accent = field.buttonFill,
        )
    // The drawing has one fixed orientation, so it does not mirror in a right-to-left layout.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier
                .fillMaxWidth()
                .aspectRatio(FRAME_ASPECT)
                .clip(AwnShapes.control)
                .background(Awn.field.softFill)
                .semantics { contentDescription = description },
        ) {
            Canvas(Modifier.fillMaxSize()) { drawDiagram(highlighted, colours) }
            LABELS.forEach { label ->
                AreaLabelText(label, isHighlighted = label.area in highlighted)
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.AreaLabelText(label: AreaLabel, isHighlighted: Boolean) {
    val field = Awn.field
    val startFraction = if (label.isOnLeft) LEFT_LABEL_START else RIGHT_LABEL_START
    Box(
        modifier =
            Modifier.absoluteOffset(
                    x = maxWidth * startFraction,
                    y = maxHeight * label.centerY - LABEL_HEIGHT / 2,
                )
                .width(maxWidth * LABEL_WIDTH)
                .height(LABEL_HEIGHT),
        contentAlignment = if (label.isOnLeft) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(label.text),
            style = AwnTypography.small,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlighted) field.buttonFill else field.mutedContent,
            textAlign = if (label.isOnLeft) TextAlign.End else TextAlign.Start,
        )
    }
}

private fun DrawScope.drawDiagram(highlighted: Set<MouthArea>, colours: FigureColours) {
    val frameWidth = size.width
    val frameHeight = size.height
    val millimetreScale = frameWidth * FIGURE_WIDTH / MouthShapes.WIDTH
    val figureLeft = frameWidth * FIGURE_START
    val figureTop = (frameHeight - MouthShapes.HEIGHT * millimetreScale) / 2f
    withTransform(
        {
            translate(
                figureLeft - MouthShapes.LEFT * millimetreScale,
                figureTop - MouthShapes.TOP * millimetreScale,
            )
            scale(millimetreScale, millimetreScale, pivot = Offset.Zero)
        },
    ) {
        drawFigure(highlighted, colours, strokeWidth = OUTLINE_WIDTH_PX / millimetreScale)
    }
    LABELS.forEach { label ->
        val anchor = label.anchor
        val anchorPoint =
            Offset(
                figureLeft + (anchor.x - MouthShapes.LEFT) * millimetreScale,
                figureTop + (anchor.y - MouthShapes.TOP) * millimetreScale,
            )
        val labelEdgeX =
            if (label.isOnLeft) frameWidth * LEFT_LABEL_END else frameWidth * RIGHT_LABEL_START
        val color = if (label.area in highlighted) colours.accent else colours.outline
        drawLine(
            color,
            anchorPoint,
            Offset(labelEdgeX, frameHeight * label.centerY),
            strokeWidth = LEADER_WIDTH_PX,
        )
        drawCircle(color, radius = ANCHOR_RADIUS_PX, center = anchorPoint)
    }
}

private class FigureColours(
    val body: Color,
    val outline: Color,
    val highlight: Color,
    val accent: Color,
)

private fun DrawScope.drawFigure(
    highlighted: Set<MouthArea>,
    colours: FigureColours,
    strokeWidth: Float,
) {
    val solids = listOf(MouthShapes.lowerJaw, MouthShapes.palate)
    val outlineStyle = Stroke(width = strokeWidth)
    drawPath(MouthShapes.head, colours.body)
    solids.forEach { drawPath(it, colours.body) }
    drawPath(MouthShapes.windpipe, colours.body)
    highlightedAreas(highlighted).forEach { drawPath(it, colours.highlight) }
    drawPath(MouthShapes.headFace, colours.outline, style = outlineStyle)
    solids.forEach { drawPath(it, colours.outline, style = outlineStyle) }
    drawPath(MouthShapes.windpipe, colours.outline, style = outlineStyle)
    drawPath(MouthShapes.epiglottis, colours.outline, style = outlineStyle)
}

private fun highlightedAreas(highlighted: Set<MouthArea>) = highlighted.map { area ->
    when (area) {
        MouthArea.Throat -> MouthShapes.throatArea
        MouthArea.Tongue -> MouthShapes.tongueArea
        MouthArea.Lips -> MouthShapes.lipsArea
        MouthArea.Nose -> MouthShapes.noseArea
        MouthArea.OpenMouth -> MouthShapes.openMouthArea
    }
}

/**
 * One label of the drawing: the text, where its leader line touches the head, which side it sits
 * on, and the height of its middle as a fraction of the frame.
 */
private class AreaLabel(
    val area: MouthArea,
    val text: Int,
    val anchor: Anchor,
    val isOnLeft: Boolean,
    val centerY: Float,
)

private val LABELS =
    listOf(
        AreaLabel(
            MouthArea.Throat,
            R.string.mouth_area_throat,
            MouthShapes.throatAnchor,
            true,
            0.7f,
        ),
        AreaLabel(MouthArea.Nose, R.string.mouth_area_nose, MouthShapes.noseAnchor, false, 0.12f),
        AreaLabel(
            MouthArea.OpenMouth,
            R.string.mouth_area_open_mouth,
            MouthShapes.openMouthAnchor,
            false,
            0.3f,
        ),
        AreaLabel(MouthArea.Lips, R.string.mouth_area_lips, MouthShapes.lipsAnchor, false, 0.5f),
        AreaLabel(
            MouthArea.Tongue,
            R.string.mouth_area_tongue,
            MouthShapes.tongueAnchor,
            false,
            0.7f,
        ),
    )

// Widths are fractions of the frame's width: the throat label, the drawing, the other labels.
private const val LEFT_LABEL_START = 0f
private const val LEFT_LABEL_END = 0.24f
private const val FIGURE_START = 0.25f
private const val FIGURE_WIDTH = 0.5f
private const val RIGHT_LABEL_START = 0.77f
private const val LABEL_WIDTH = 0.23f

// The drawing is 95 by 145 mm and takes half the width, so the frame is 0.8 times as high as wide.
private const val FRAME_ASPECT = 1.25f
private val LABEL_HEIGHT: Dp = 40.dp

private const val BODY_ALPHA = 0.12f
private const val OUTLINE_ALPHA = 0.55f
private const val HIGHLIGHT_ALPHA = 0.6f
private const val OUTLINE_WIDTH_PX = 3f
private const val LEADER_WIDTH_PX = 2f
private const val ANCHOR_RADIUS_PX = 5f
