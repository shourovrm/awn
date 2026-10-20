package app.awn.quran.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.ThemePreference

/** Settings stub: the theme row and a way to About. Other groups arrive with their features. */
@Composable
fun SettingsScreen(
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onAboutClick: () -> Unit,
) {
    AwnTheme(field = AwnField.Paper) {
        Column(
            modifier = Modifier.fillMaxSize().padding(AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        ) {
            Text(text = stringResource(R.string.settings_title), style = AwnTypography.title)
            SettingsRow(
                label = stringResource(R.string.settings_theme),
                value = stringResource(themePreference.labelResource()),
                onClick = { onThemePreferenceChange(themePreference.next()) },
            )
            SettingsRow(
                label = stringResource(R.string.settings_about),
                value = null,
                onClick = onAboutClick,
            )
        }
    }
}

/** Tapping the Theme row steps through Light, Dark and System. */
private fun ThemePreference.next(): ThemePreference =
    ThemePreference.entries[(ordinal + 1) % ThemePreference.entries.size]

private fun ThemePreference.labelResource(): Int =
    when (this) {
        ThemePreference.Light -> R.string.theme_light
        ThemePreference.Dark -> R.string.theme_dark
        ThemePreference.System -> R.string.theme_system
    }

@Composable
private fun SettingsRow(label: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body)
        if (value != null) {
            Text(text = value, style = AwnTypography.bodyStrong, color = Awn.field.buttonFill)
        }
    }
}
