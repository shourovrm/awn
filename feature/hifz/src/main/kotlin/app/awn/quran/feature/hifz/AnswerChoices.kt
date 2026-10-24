package app.awn.quran.feature.hifz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill

/**
 * One row of equal buttons, one per option, with the [selected] one filled; a single choice read
 * out as radio buttons. Used for the answer of each ayah or page on the End part sheet.
 */
@Composable
internal fun <T> AnswerChoices(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    tagFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Row(
        modifier.fillMaxWidth().padding(vertical = AwnSpacing.small / 2).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small / 2),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier.weight(1f)
                    .heightIn(min = AwnSpacing.touchTarget)
                    .clip(AwnShapes.control)
                    .background(if (isSelected) field.buttonFill else field.softFill)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                    .testTag(tagFor(option)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style =
                        if (isSelected)
                            AwnTypography.small.copy(
                                fontWeight = AwnTypography.bodyStrong.fontWeight,
                            )
                        else AwnTypography.small,
                    color = if (isSelected) field.onButton else field.content,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}
