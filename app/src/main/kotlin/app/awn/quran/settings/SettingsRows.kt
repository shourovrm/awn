package app.awn.quran.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.R
import app.awn.quran.core.ui.components.AwnSwitch
import app.awn.quran.core.ui.components.AwnTopBar
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography

/**
 * A settings screen: a top bar with a back arrow above a scrolling list of rows. A page whose
 * content fills the screen itself, such as the Mushaf page's swiped preview, passes [scrolls]
 * false: inside a scroll it would get no height and draw nothing.
 */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrolls: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        Column(modifier.fillMaxSize()) {
            AwnTopBar(
                title = title,
                onNavigateBack = onBack,
                backDescription = stringResource(R.string.navigate_back),
            )
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .then(
                            if (scrolls) Modifier.verticalScroll(rememberScrollState())
                            else Modifier,
                        )
                        .padding(horizontal = AwnSpacing.screenMargin),
                content = content,
            )
        }
    }
}

/** A row with an icon and a label that opens a page. */
@Composable
internal fun IconRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Awn.field.buttonFill,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
        Text(text = label, style = AwnTypography.body, color = Awn.field.content)
    }
}

/** A label on the left and the current value on the right; tapping it opens a picker. */
@Composable
internal fun ValueRow(label: String, value: String, onClick: (() -> Unit)?) {
    val clickable =
        if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Row(
        modifier =
            Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowHeight).then(clickable),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body, color = Awn.field.content)
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
        Text(text = label, style = AwnTypography.body, color = Awn.field.content)
        AwnSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
