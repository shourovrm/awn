package app.awn.quran.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.R

/** The sections of the Settings list, in the order they are listed. */
enum class SettingsSection(
    val route: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    Mushaf("settings/mushaf", R.drawable.ic_app_menu_book, R.string.settings_mushaf),
    Reading("settings/reading", R.drawable.ic_app_text_fields, R.string.settings_reading),
    Audio("settings/audio", R.drawable.ic_app_headphones, R.string.settings_audio),
    Recite("settings/recite", R.drawable.ic_app_mic, R.string.settings_recite),
    Appearance("settings/appearance", R.drawable.ic_app_palette, R.string.settings_appearance),
    Backup("settings/backup", R.drawable.ic_app_backup, R.string.settings_backup),
    About("settings/about", R.drawable.ic_app_info, R.string.settings_about),
}

/** Settings as a short list: one icon and one label per section, each opening its own page. */
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsSection.entries.forEach { section ->
            IconRow(
                icon = section.icon,
                label = stringResource(section.label),
                onClick = { onOpenSection(section) },
            )
        }
    }
}
