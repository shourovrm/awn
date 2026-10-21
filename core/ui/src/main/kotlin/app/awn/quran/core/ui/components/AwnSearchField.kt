package app.awn.quran.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import app.awn.quran.core.ui.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill

/**
 * A 44 dp search field with a soft fill and 12 dp corners. [placeholder] shows while the field is
 * empty and names the field for a screen reader; [clearDescription] names the clear button, which
 * appears once there is text.
 */
@Composable
fun AwnSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AwnSpacing.searchFieldHeight)
                .clip(AwnShapes.searchField)
                .background(field.softFill)
                .padding(start = AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ui_search),
            contentDescription = null,
            tint = field.mutedContent,
            modifier = Modifier.size(AwnSpacing.smallIconSize),
        )
        Box(
            modifier = Modifier.weight(1f).padding(start = AwnSpacing.small),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(text = placeholder, style = AwnTypography.body, color = field.mutedContent)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = AwnTypography.body.copy(color = field.content),
                cursorBrush = SolidColor(field.buttonFill),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (query.isNotEmpty()) {
            AwnIconButton(
                icon = R.drawable.ic_ui_close,
                contentDescription = clearDescription,
                onClick = { onQueryChange("") },
                tint = field.mutedContent,
            )
        }
    }
}
