package app.awn.quran.feature.read.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.CatalogueState
import app.awn.quran.core.ui.components.AwnSwitch
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.ReadIconButton

private const val PERCENT = 100
private const val BANGLA_LANGUAGE = "bn"
private const val ENGLISH_LANGUAGE = "en"

/**
 * The Reading page of the Settings hub: translations (download, choose, order and remove),
 * transliteration, word-by-word meanings, the Arabic and translation sizes, highlighting of
 * bookmarks and the volume keys. :app embeds it in its own theme.
 */
@Composable
fun ReadingSettingsPage(dependencies: ReadDependencies, modifier: Modifier = Modifier) {
    val viewModel = rememberReadSettingsViewModel(dependencies)
    val catalogueViewModel = rememberCatalogueViewModel(dependencies)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val catalogue by catalogueViewModel.state.collectAsStateWithLifecycle()
    val wordLanguage = wordLanguageOf(LocalConfiguration.current.locales[0].language)

    Column(modifier.fillMaxWidth()) {
        TranslationSettings(catalogue, settings, viewModel, catalogueViewModel)
        SwitchRow(
            label = stringResource(R.string.setting_transliteration),
            checked = settings.showTransliteration,
            onCheckedChange = viewModel::setShowTransliteration,
        )
        if (settings.showTransliteration) {
            MissingDownloadOffer(
                row = rowToOffer(catalogue, CatalogueGroup.Transliteration, language = null),
                catalogueViewModel = catalogueViewModel,
            )
        }
        SwitchRow(
            label = stringResource(R.string.setting_word_by_word),
            checked = settings.showWordByWord,
            onCheckedChange = viewModel::setShowWordByWord,
        )
        if (settings.showWordByWord) {
            MissingDownloadOffer(
                row = rowToOffer(catalogue, CatalogueGroup.WordByWord, wordLanguage),
                catalogueViewModel = catalogueViewModel,
            )
        }
        SizeRow(
            label = stringResource(R.string.setting_arabic_size),
            step = settings.arabicSizeStep,
            scale = settings.arabicScale,
            onStepChange = viewModel::setArabicSizeStep,
        )
        SizeRow(
            label = stringResource(R.string.setting_translation_size),
            step = settings.translationSizeStep,
            scale = settings.translationScale,
            onStepChange = viewModel::setTranslationSizeStep,
        )
        SwitchRow(
            label = stringResource(R.string.setting_highlight_bookmarks),
            checked = settings.highlightBookmarks,
            onCheckedChange = viewModel::setHighlightBookmarks,
        )
        SwitchRow(
            label = stringResource(R.string.setting_volume_keys),
            checked = settings.volumeKeysTurnPages,
            onCheckedChange = viewModel::setVolumeKeysTurnPages,
        )
    }
}

/** The word-by-word meanings follow the app language, as on the Quran screen. */
internal fun wordLanguageOf(appLanguage: String): String =
    if (appLanguage == BANGLA_LANGUAGE) BANGLA_LANGUAGE else ENGLISH_LANGUAGE

/**
 * The download a switch needs, shown under the switch when it is on and the download is missing, so
 * the user never has to look for why nothing appears.
 */
@Composable
private fun MissingDownloadOffer(row: CatalogueRow?, catalogueViewModel: CatalogueViewModel) {
    if (row == null) return
    CatalogueItemRow(row = row, actions = catalogueViewModel.actionsFor(row.item.id))
}

@Composable
private fun TranslationSettings(
    catalogue: CatalogueState,
    settings: ReadSettings,
    viewModel: ReadSettingsViewModel,
    catalogueViewModel: CatalogueViewModel,
) {
    Text(
        text = stringResource(R.string.setting_translations),
        style = AwnTypography.body,
        color = Awn.field.content,
        modifier = Modifier.padding(top = AwnSpacing.small),
    )
    orderedForDisplay(catalogue.rowsIn(CatalogueGroup.Translation), settings.translations)
        .forEach { row ->
            val id = TranslationId(row.item.id)
            val position = settings.translations.indexOf(id)
            val isEnabled = position >= 0
            val baseActions = catalogueViewModel.actionsFor(row.item.id)
            CatalogueItemRow(
                row = row,
                actions =
                    CatalogueRowActions(
                        // Downloading a translation also turns it on: the user asked to read it.
                        onInstall = {
                            viewModel.setTranslationEnabled(id, true)
                            baseActions.onInstall()
                        },
                        onCancel = baseActions.onCancel,
                        // A removed translation must not stay on and ask to be downloaded again.
                        onRemove = {
                            viewModel.setTranslationEnabled(id, false)
                            baseActions.onRemove()
                        },
                    ),
                leadingControls = {
                    if (position > 0) {
                        ReadIconButton(
                            iconRes = R.drawable.ic_read_move_up,
                            description = stringResource(R.string.action_move_up),
                            onClick = { viewModel.moveTranslationUp(id) },
                        )
                    }
                },
                trailingControls = {
                    if (row.item.isInstalled || isEnabled) {
                        AwnSwitch(
                            checked = isEnabled,
                            onCheckedChange = { viewModel.setTranslationEnabled(id, it) },
                        )
                    }
                },
            )
        }
}

/** Enabled translations in the user's order first, then the others in manifest order. */
internal fun orderedForDisplay(
    rows: List<CatalogueRow>,
    enabled: List<TranslationId>,
): List<CatalogueRow> {
    val enabledRows = enabled.mapNotNull { id -> rows.firstOrNull { it.item.id == id.id } }
    return enabledRows + rows.filterNot { it in enabledRows }
}

@Composable
private fun SizeRow(label: String, step: Int, scale: Float, onStepChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body, modifier = Modifier.weight(1f))
        ReadIconButton(
            iconRes = R.drawable.ic_read_minus,
            description = stringResource(R.string.action_smaller),
            onClick = { onStepChange(step - 1) },
            isEnabled = step > 0,
        )
        Text(
            text = stringResource(R.string.size_percent, (scale * PERCENT).toInt()),
            style = AwnTypography.body,
            color = Awn.field.buttonFill,
        )
        ReadIconButton(
            iconRes = R.drawable.ic_read_plus,
            description = stringResource(R.string.action_larger),
            onClick = { onStepChange(step + 1) },
            isEnabled = step < TextScale.STEP_COUNT - 1,
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body)
        AwnSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
