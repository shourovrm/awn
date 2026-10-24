package app.awn.quran.feature.read.quran

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.page.pageTypeText

private const val MARK_RING_ALPHA = 0.45f

/** One edition of the welcome card: single choice, with a thumbnail of its page at the start. */
@Composable
internal fun EditionRow(
    script: ScriptId,
    isSelected: Boolean,
    sizeBytes: Long?,
    onSelect: () -> Unit,
) {
    val text = pageTypeText(script)
    OfferRow(
        isSelected = isSelected,
        selectionModifier =
            Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect),
        leading = { EditionThumbnail(text.preview) },
        name = stringResource(text.name),
        detail = stringResource(text.year),
        sizeBytes = sizeBytes,
        mark = { SelectionMark(isSelected, CircleShape) },
    )
}

/** The translation and word-by-word row of the welcome card: one tick box. */
@Composable
internal fun StarterRow(
    translationName: String,
    isTicked: Boolean,
    sizeBytes: Long,
    onToggle: () -> Unit,
) {
    OfferRow(
        isSelected = isTicked,
        selectionModifier =
            Modifier.toggleable(
                value = isTicked,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ),
        leading = null,
        name = stringResource(R.string.starter_translation_name, translationName),
        detail = stringResource(R.string.starter_translation_detail),
        sizeBytes = sizeBytes,
        mark = { SelectionMark(isTicked, AwnShapes.tickBox) },
    )
}

@Composable
private fun OfferRow(
    isSelected: Boolean,
    selectionModifier: Modifier,
    leading: (@Composable () -> Unit)?,
    name: String,
    detail: String,
    sizeBytes: Long?,
    mark: @Composable () -> Unit,
) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.giant)
                .clip(AwnShapes.menu)
                .then(if (isSelected) Modifier.background(field.softFill) else Modifier)
                .then(selectionModifier)
                .padding(horizontal = AwnSpacing.medium, vertical = AwnSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(text = name, style = AwnTypography.body, color = field.content)
            Text(text = detail, style = AwnTypography.small, color = field.mutedContent)
        }
        if (sizeBytes != null) {
            Text(
                text = megabytesText(sizeBytes),
                style = AwnTypography.small,
                color = field.mutedContent,
            )
        }
    }
}

/** The page picture of an edition; Indo-Pak has none, so it gets a soft rectangle of its size. */
@Composable
private fun EditionThumbnail(preview: Int?) {
    val thumbnailSize =
        Modifier.size(AwnSpacing.printThumbnailWidth, AwnSpacing.printThumbnailHeight)
            .clip(AwnShapes.wordBlock)
    if (preview != null) {
        Image(
            painter = painterResource(preview),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = thumbnailSize,
        )
    } else {
        Box(thumbnailSize.background(Awn.field.softFill))
    }
}

/** A filled check when selected, an empty ring otherwise; [shape] tells radio from tick box. */
@Composable
private fun SelectionMark(isSelected: Boolean, shape: Shape) {
    val field = Awn.field
    val markModifier =
        if (isSelected) {
            Modifier.background(field.buttonFill, shape)
        } else {
            Modifier.border(
                AwnSpacing.switchKnobRing,
                field.content.copy(alpha = MARK_RING_ALPHA),
                shape,
            )
        }
    Box(
        modifier = Modifier.size(AwnSpacing.tickBoxSize).then(markModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                painter = painterResource(UiR.drawable.ic_ui_check),
                contentDescription = null,
                tint = field.onButton,
                modifier = Modifier.size(AwnSpacing.smallIconSize),
            )
        }
    }
}
