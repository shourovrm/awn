package app.awn.quran.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.raisedSurface

/**
 * A small dialog that asks before something is lost: a [title] that names the item, a [message]
 * that says what it costs or frees, and two text actions. Tapping outside or Back dismisses it.
 */
@Composable
fun AwnConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val field = Awn.field
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.widthIn(max = AwnSpacing.dialogMaxWidth)
                    .clip(AwnShapes.menu)
                    .background(field.raisedSurface)
                    .padding(AwnSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            Text(text = title, style = AwnTypography.bodyStrong, color = field.content)
            Text(text = message, style = AwnTypography.small, color = field.mutedContent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DialogAction(dismissLabel, onDismiss)
                DialogAction(confirmLabel, onConfirm)
            }
        }
    }
}

@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AwnTypography.bodyStrong, color = Awn.field.buttonFill)
    }
}
