package app.awn.quran.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.components.AwnChoiceDialog
import app.awn.quran.core.ui.components.AwnSearchField
import app.awn.quran.core.ui.components.SettingsGroupHeading
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.language.AppLanguage

/** The sections of the Settings list, in the order they are listed. */
enum class SettingsSection(
    val route: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    Mushaf("settings/mushaf", R.drawable.ic_app_menu_book, R.string.settings_mushaf),
    Reading("settings/reading", R.drawable.ic_app_text_fields, R.string.settings_reading),
    Audio("settings/audio", R.drawable.ic_app_headphones, R.string.settings_audio),
    Downloads(
        "settings/downloads",
        R.drawable.ic_app_file_download,
        R.string.settings_downloads,
    ),
    Recite("settings/recite", R.drawable.ic_app_mic, R.string.settings_recite),
    Appearance("settings/appearance", R.drawable.ic_app_palette, R.string.settings_appearance),
    Backup("settings/backup", R.drawable.ic_app_backup, R.string.settings_backup),
    About("settings/about", R.drawable.ic_app_info, R.string.settings_about),
}

/** The headings of the hub and the sections under each, as in the quiet-list design. */
private val HUB_GROUPS: List<Pair<Int, List<SettingsSection>>> =
    listOf(
        R.string.settings_group_reading to listOf(SettingsSection.Mushaf, SettingsSection.Reading),
        R.string.settings_group_listening to
            listOf(SettingsSection.Audio, SettingsSection.Downloads, SettingsSection.Recite),
        R.string.settings_group_app to
            listOf(SettingsSection.Appearance, SettingsSection.Backup, SettingsSection.About),
    )

/**
 * Settings as grouped two-line rows: each section shows its current value under its name. A search
 * field at the top filters every setting of every page; a result opens its page at that row.
 * [summaries] gives the second line of each section; [onOpenRow] opens a page at a row.
 */
@Composable
fun SettingsHubScreen(
    summaries: Map<SettingsSection, String>,
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenRow: (route: String, rowKey: String?) -> Unit,
    modifier: Modifier = Modifier,
    language: AppLanguage = AppLanguage.English,
    onLanguageChange: (AppLanguage) -> Unit = {},
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val entries = remember(locale) { buildSettingsSearchEntries(context) }
    var query by rememberSaveable { mutableStateOf("") }

    SettingsPage(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        AwnSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.settings_search_placeholder),
            clearDescription = stringResource(R.string.settings_search_clear),
            modifier = Modifier.padding(top = AwnSpacing.medium),
        )
        if (query.isBlank()) {
            HubGroups(summaries, language, onLanguageChange, onOpenSection)
        } else {
            SearchResults(searchSettings(entries, query), onOpenRow)
        }
    }
}

@Composable
private fun HubGroups(
    summaries: Map<SettingsSection, String>,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
) {
    HUB_GROUPS.forEach { (heading, sections) ->
        SettingsGroupHeading(stringResource(heading))
        // Language is the one setting reachable straight from the hub, first in the App group.
        if (heading == R.string.settings_group_app) HubLanguageRow(language, onLanguageChange)
        sections.forEach { section ->
            SettingsLinkRow(
                label = stringResource(section.label),
                value = summaries[section],
                icon = section.icon,
                onClick = { onOpenSection(section) },
            )
        }
    }
}

/** The Language row of the hub: the current language in its own script, and the choice dialog. */
@Composable
private fun HubLanguageRow(language: AppLanguage, onLanguageChange: (AppLanguage) -> Unit) {
    var isDialogOpen by rememberSaveable { mutableStateOf(false) }
    SettingsLinkRow(
        label = stringResource(R.string.appearance_language),
        value = stringResource(language.ownNameRes),
        icon = R.drawable.ic_app_language,
        onClick = { isDialogOpen = true },
    )
    if (isDialogOpen) {
        AwnChoiceDialog(
            title = stringResource(R.string.appearance_language),
            options = AppLanguage.entries,
            selected = language,
            optionLabel = { stringResource(it.ownNameRes) },
            onSelect = {
                isDialogOpen = false
                onLanguageChange(it)
            },
            onDismiss = { isDialogOpen = false },
        )
    }
}

@Composable
private fun SearchResults(
    results: List<SettingsSearchEntry>,
    onOpenRow: (route: String, rowKey: String?) -> Unit,
) {
    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_search_empty),
            style = AwnTypography.body,
            color = Awn.field.mutedContent,
            modifier = Modifier.padding(top = AwnSpacing.large),
        )
        return
    }
    Column(Modifier.padding(top = AwnSpacing.small)) {
        results.forEach { entry ->
            SettingsLinkRow(
                label = entry.label,
                value = entry.pageLabel,
                onClick = { onOpenRow(entry.route, entry.rowKey) },
            )
        }
    }
}
