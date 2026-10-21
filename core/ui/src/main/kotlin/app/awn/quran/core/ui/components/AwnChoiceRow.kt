package app.awn.quran.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.raisedSurface

/**
 * A single-choice setting: the label (and an optional [hint] line) at the start, the chosen value
 * in the accent with a small chevron at the end. Tapping the row opens a small menu anchored under
 * the value, with the chosen option ticked; choosing one calls [onSelect] and closes the menu.
 * Every single-choice setting in the app uses this row. With [isCompact] the row is only as wide as
 * its label and value, for a choice that shares a line with another control (the Show choice above
 * the Quran tab list).
 */
@Composable
fun <T> AwnChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    rowKey: String? = null,
    isCompact: Boolean = false,
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val field = Awn.field
    val hasHint = !hint.isNullOrEmpty()
    Row(
        modifier =
            modifier
                .then(if (isCompact) Modifier else Modifier.fillMaxWidth())
                .heightIn(
                    min = if (hasHint) AwnSpacing.settingsRowTall else AwnSpacing.settingsRowShort,
                )
                .searchTarget(rowKey)
                .clickable(role = Role.DropdownList) { isMenuOpen = true }
                .padding(vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        RowLabels(label, hint, if (isCompact) Modifier else Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = optionLabel(selected),
                    style = AwnTypography.bodyStrong,
                    color = field.buttonFill,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_ui_chevron),
                    contentDescription = null,
                    tint = field.buttonFill,
                    modifier = Modifier.size(AwnSpacing.smallIconSize),
                )
            }
            ChoiceMenu(
                isOpen = isMenuOpen,
                options = options,
                selected = selected,
                optionLabel = optionLabel,
                onSelect = {
                    isMenuOpen = false
                    onSelect(it)
                },
                onDismiss = { isMenuOpen = false },
            )
        }
    }
}

@Composable
private fun <T> ChoiceMenu(
    isOpen: Boolean,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val field = Awn.field
    DropdownMenu(
        expanded = isOpen,
        onDismissRequest = onDismiss,
        shape = AwnShapes.menu,
        containerColor = field.raisedSurface,
        tonalElevation = 0.dp,
        shadowElevation = AwnSpacing.small,
        modifier = Modifier.widthIn(min = AwnSpacing.menuMinWidth),
    ) {
        options.forEach { option ->
            val isChosen = option == selected
            DropdownMenuItem(
                text = {
                    Text(
                        text = optionLabel(option),
                        style = if (isChosen) AwnTypography.bodyStrong else AwnTypography.body,
                        color = if (isChosen) field.buttonFill else field.content,
                    )
                },
                trailingIcon = {
                    if (isChosen) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ui_check),
                            contentDescription = null,
                            tint = field.buttonFill,
                            modifier = Modifier.size(AwnSpacing.smallIconSize),
                        )
                    }
                },
                onClick = { onSelect(option) },
                colors = MenuDefaults.itemColors(textColor = field.content),
                modifier =
                    Modifier.heightIn(min = AwnSpacing.menuItemHeight).semantics {
                        role = Role.RadioButton
                        this.selected = isChosen
                    },
            )
        }
    }
}
