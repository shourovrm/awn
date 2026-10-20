package app.awn.quran.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.awn.quran.core.ui.components.AwnSwitch
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

@Composable
internal fun GroupTitle(title: String) {
    Text(
        text = title,
        style = AwnTypography.lead,
        color = Awn.field.buttonFill,
        modifier = Modifier.padding(vertical = AwnSpacing.small),
    )
}

@Composable
internal fun ValueRow(label: String, value: String, onClick: (() -> Unit)?) {
    val clickable = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(
        modifier =
            Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowHeight).then(clickable),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body)
        Text(text = value, style = AwnTypography.bodyStrong, color = Awn.field.buttonFill)
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body)
        AwnSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
