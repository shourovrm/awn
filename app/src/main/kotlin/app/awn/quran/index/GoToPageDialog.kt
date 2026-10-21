package app.awn.quran.index

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import app.awn.quran.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.raisedSurface
import app.awn.quran.core.ui.theme.softFill

private const val DISABLED_ALPHA = 0.4f

/** The page the typed [text] names, or null when it is not a page from 1 to [pageCount]. */
fun parsePageNumber(text: String, pageCount: Int): Int? =
    text.trim().toIntOrNull()?.takeIf { it in 1..pageCount }

/** Asks for a page number and opens it. The Go action is off until the number is a real page. */
@Composable
internal fun GoToPageDialog(
    pageCount: Int,
    onGo: (page: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val field = Awn.field
    var text by remember { mutableStateOf("") }
    val page = parsePageNumber(text, pageCount)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.widthIn(max = AwnSpacing.dialogMaxWidth)
                    .clip(AwnShapes.menu)
                    .background(field.raisedSurface)
                    .padding(AwnSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            Text(
                text = stringResource(R.string.go_to_page),
                style = AwnTypography.bodyStrong,
                color = field.content,
            )
            val placeholder = stringResource(R.string.go_to_page_hint, pageCount)
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = AwnSpacing.searchFieldHeight)
                        .clip(AwnShapes.searchField)
                        .background(field.softFill)
                        .padding(horizontal = AwnSpacing.medium),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(text = placeholder, style = AwnTypography.body, color = field.mutedContent)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                    singleLine = true,
                    textStyle = AwnTypography.body.copy(color = field.content),
                    cursorBrush = SolidColor(field.buttonFill),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Go,
                        ),
                    keyboardActions = KeyboardActions(onGo = { page?.let(onGo) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DialogAction(stringResource(R.string.dialog_cancel), isEnabled = true, onDismiss)
                DialogAction(
                    stringResource(R.string.go_to_page_go),
                    isEnabled = page != null,
                    onClick = { page?.let(onGo) },
                )
            }
        }
    }
}

private const val MAX_DIGITS = 3

@Composable
private fun DialogAction(label: String, isEnabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.heightIn(min = AwnSpacing.touchTarget)
                .alpha(if (isEnabled) 1f else DISABLED_ALPHA)
                .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AwnTypography.bodyStrong, color = Awn.field.buttonFill)
    }
}
