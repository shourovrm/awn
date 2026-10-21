package app.awn.quran.feature.read.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.ui.components.PackTick
import app.awn.quran.core.ui.components.SettingsGroupHeading
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.page.MushafSettingsViewModel
import java.util.Locale

/**
 * One list of downloadable entries (translations, transliteration, word by word or page types),
 * grouped by language. The tick box at the start shows or hides an entry once it is on the phone;
 * bundled entries say "Included" and cannot be removed. The title is [PackListKind.title], drawn by
 * the page that hosts this list.
 */
@Composable
fun PackListPage(
    dependencies: ReadDependencies,
    kind: PackListKind,
    modifier: Modifier = Modifier,
) {
    val settingsViewModel = rememberReadSettingsViewModel(dependencies)
    val catalogueViewModel = rememberCatalogueViewModel(dependencies)
    val mushafViewModel: MushafSettingsViewModel =
        viewModel(
            factory = viewModelFactory { initializer { MushafSettingsViewModel(dependencies) } },
        )
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val catalogue by catalogueViewModel.state.collectAsStateWithLifecycle()
    val rows = catalogue.rowsIn(kind.group)
    val appLanguage = LocalConfiguration.current.locales[0].language
    val wordLanguageShown =
        wordLanguageShown(
            chosen = settings.wordByWordLanguage,
            appLanguage = appLanguage,
            installedLanguages =
                installedWordLanguages(catalogue.rowsIn(CatalogueGroup.WordByWord)).toSet(),
        )

    Column(modifier.fillMaxWidth()) {
        groupByLanguage(rows).forEachIndexed { index, group ->
            if (group.language != null) {
                LanguageHeading(group.language, isFirst = index == 0)
            }
            group.rows.forEach { row ->
                val baseActions = catalogueViewModel.actionsFor(row.item.id)
                when (kind) {
                    PackListKind.Translations ->
                        TranslationEntry(row, settings, settingsViewModel, baseActions)
                    PackListKind.Transliteration ->
                        CatalogueEntryRow(
                            row = row,
                            tick = tickOf(settings.showTransliteration),
                            onTickChange = settingsViewModel::setShowTransliteration,
                            actions =
                                baseActions.afterInstall {
                                    settingsViewModel.setShowTransliteration(true)
                                },
                        )
                    PackListKind.WordByWord ->
                        WordByWordEntry(
                            row = row,
                            languageShown = wordLanguageShown,
                            settingsViewModel = settingsViewModel,
                            baseActions = baseActions,
                        )
                    PackListKind.PageTypes ->
                        PageTypeEntry(row, settings.script, mushafViewModel, baseActions)
                }
            }
        }
    }
}

@Composable
private fun PageTypeEntry(
    row: CatalogueRow,
    scriptInUse: ScriptId,
    mushafViewModel: MushafSettingsViewModel,
    baseActions: CatalogueRowActions,
) {
    val script = ScriptId.entries.firstOrNull { it.packId == row.item.id }
    val isInUse = script != null && script == scriptInUse
    CatalogueEntryRow(
        row = row,
        tick = tickOf(isInUse),
        // One print is in use at a time: ticking another replaces it, unticking is not offered.
        onTickChange = { wantsToUse ->
            if (wantsToUse && script != null) mushafViewModel.use(script)
        },
        actions =
            CatalogueRowActions(
                // The view model also makes the print the one in use once its font is on the phone.
                onInstall = {
                    if (script != null) mushafViewModel.install(script) else baseActions.onInstall()
                },
                onCancel = baseActions.onCancel,
                onRemove = baseActions.onRemove,
            ),
        // The print in use is never removed.
        canRemove = !isInUse,
    )
}

/**
 * One word-by-word pack. The meanings come from one language at a time, so the ticks work like the
 * page types: ticking a pack that is on the phone selects its language, and the selected one stays
 * ticked.
 */
@Composable
private fun WordByWordEntry(
    row: CatalogueRow,
    languageShown: String,
    settingsViewModel: ReadSettingsViewModel,
    baseActions: CatalogueRowActions,
) {
    val language = row.item.language
    CatalogueEntryRow(
        row = row,
        tick = tickOf(language != null && language == languageShown),
        onTickChange = { wantsToUse ->
            if (wantsToUse && language != null) settingsViewModel.useWordByWordLanguage(language)
        },
        actions =
            CatalogueRowActions(
                // Downloading a language also selects it: the user asked to read in it.
                onInstall = {
                    if (language != null) settingsViewModel.useWordByWordLanguage(language)
                    baseActions.onInstall()
                },
                onCancel = baseActions.onCancel,
                onRemove = {
                    if (language != null) settingsViewModel.forgetWordByWordLanguage(language)
                    baseActions.onRemove()
                },
            ),
    )
}

private fun tickOf(isTicked: Boolean): PackTick = if (isTicked) PackTick.On else PackTick.Off

/** Runs [extra] when the user starts a download, then the download itself. */
private fun CatalogueRowActions.afterInstall(extra: () -> Unit) =
    CatalogueRowActions(
        onInstall = {
            extra()
            onInstall()
        },
        onCancel = onCancel,
        onRemove = onRemove,
    )

/** The language's name in its own script, with the name in the app's language at the end. */
@Composable
private fun LanguageHeading(languageCode: String, isFirst: Boolean) {
    val language = Locale.forLanguageTag(languageCode)
    val appLocale = LocalConfiguration.current.locales[0]
    val ownName = language.getDisplayLanguage(language).replaceFirstChar { it.titlecase(language) }
    val appName =
        language.getDisplayLanguage(appLocale).replaceFirstChar { it.titlecase(appLocale) }
    SettingsGroupHeading(
        text = ownName,
        isFirst = isFirst,
        trailingText = appName.takeIf { it != ownName },
    )
}

@Composable
private fun TranslationEntry(
    row: CatalogueRow,
    settings: ReadSettings,
    settingsViewModel: ReadSettingsViewModel,
    baseActions: CatalogueRowActions,
) {
    val id = TranslationId(row.item.id)
    CatalogueEntryRow(
        row = row,
        tick = tickOf(id in settings.translations),
        onTickChange = { settingsViewModel.setTranslationEnabled(id, it) },
        actions =
            CatalogueRowActions(
                // Downloading a translation also shows it: the user asked to read it.
                onInstall = {
                    settingsViewModel.setTranslationEnabled(id, true)
                    baseActions.onInstall()
                },
                onCancel = baseActions.onCancel,
                // A removed translation must not stay on and ask to be downloaded again.
                onRemove = {
                    settingsViewModel.setTranslationEnabled(id, false)
                    baseActions.onRemove()
                },
            ),
    )
}
