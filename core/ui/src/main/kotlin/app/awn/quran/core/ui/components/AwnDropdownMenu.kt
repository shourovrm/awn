package app.awn.quran.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.raisedSurface

/**
 * A dropdown menu in the Awn style: the menu shape, the raised surface of the field and a shadow,
 * with no tonal tint, which on Material's default shows as pale purple. Rows put their text in
 * `Awn.field.content`.
 */
@Composable
fun AwnDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = AwnShapes.menu,
        containerColor = Awn.field.raisedSurface,
        tonalElevation = 0.dp,
        shadowElevation = AwnSpacing.small,
        content = content,
    )
}
