package app.awn.quran.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import app.awn.quran.core.ui.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

/**
 * A dialog that lists [options] with the [selected] one ticked. Choosing an option calls [onSelect]
 * and the caller closes the dialog; tapping outside or pressing Back calls [onDismiss]. It replaces
 * rows that cycle through values on every tap.
 */
@Composable
fun <T> AwnChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val field = Awn.field
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.widthIn(max = AwnSpacing.dialogMaxWidth)
                    .clip(AwnShapes.control)
                    .background(field.background)
                    .padding(vertical = AwnSpacing.small),
        ) {
            Text(
                text = title,
                style = AwnTypography.lead,
                color = field.content,
                modifier = Modifier.padding(AwnSpacing.medium),
            )
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    ChoiceRow(
                        label = optionLabel(option),
                        isSelected = option == selected,
                        onClick = { onSelect(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AwnTypography.body,
            color = field.content,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                painter = painterResource(R.drawable.ic_ui_check),
                contentDescription = null,
                tint = field.buttonFill,
                modifier = Modifier.size(AwnSpacing.iconSize),
            )
        } else {
            Spacer(Modifier.size(AwnSpacing.iconSize))
        }
    }
}
