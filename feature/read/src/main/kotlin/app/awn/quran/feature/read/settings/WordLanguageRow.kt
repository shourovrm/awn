package app.awn.quran.feature.read.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.downloads.CatalogueState
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.feature.read.R

/**
 * Chooses the language of the word-by-word meanings: the app language (the default) or one of the
 * languages that has a pack on the phone.
 */
@Composable
internal fun WordLanguageRow(
    catalogue: CatalogueState,
    settings: ReadSettings,
    viewModel: ReadSettingsViewModel,
) {
    val installedLanguages = installedWordLanguages(catalogue.rowsIn(CatalogueGroup.WordByWord))
    val options: List<String?> = listOf(null) + installedLanguages
    AwnChoiceRow(
        label = stringResource(R.string.setting_word_language),
        hint = stringResource(R.string.setting_word_language_hint),
        options = options,
        selected = settings.wordByWordLanguage.takeIf { it in installedLanguages },
        optionLabel = { language ->
            if (language == null) {
                stringResource(R.string.word_language_app)
            } else {
                languageDisplayName(language, LocalConfiguration.current.locales[0])
            }
        },
        onSelect = viewModel::setWordByWordLanguage,
        rowKey = SettingRowKeys.READING_WORD_LANGUAGE,
    )
}
