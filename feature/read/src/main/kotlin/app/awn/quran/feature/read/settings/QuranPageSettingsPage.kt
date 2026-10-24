package app.awn.quran.feature.read.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.downloads.CatalogueState
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.core.ui.components.SettingsGroupHeading
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.components.SettingsStepperRow
import app.awn.quran.core.ui.components.SettingsSwitchRow
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.page.pageTypeNameRes

private const val PERCENT = 100

/**
 * The Quran page of the Settings hub: the Mushaf edition and the page-details switch, links to the
 * lists of translations, transliteration and word-by-word meanings, a size stepper for each text
 * layer, and the page switches with the one rotation choice. [onOpenMushaf] opens the Mushaf
 * chooser, [onOpenPackList] opens the list of one kind. The surah name meaning belongs to :app's
 * preferences, so its value and setter come in as [showSurahNameMeaning] and
 * [onShowSurahNameMeaningChange]. :app embeds the page in its own theme.
 */
@Composable
fun QuranPageSettingsPage(
    dependencies: ReadDependencies,
    onOpenMushaf: () -> Unit,
    onOpenPackList: (PackListKind) -> Unit,
    showSurahNameMeaning: Boolean,
    onShowSurahNameMeaningChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberReadSettingsViewModel(dependencies)
    val catalogueViewModel = rememberCatalogueViewModel(dependencies)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val catalogue by catalogueViewModel.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth()) {
        MushafRows(settings, viewModel, onOpenMushaf)
        ShownUnderArabicRows(catalogue, settings, onOpenPackList)
        SettingsGroupHeading(stringResource(R.string.reading_group_size))
        TextLayer.entries.forEach { layer ->
            LayerSizeRow(layer, settings, viewModel)
        }
        PageRows(settings, viewModel, showSurahNameMeaning, onShowSurahNameMeaningChange)
    }
}

@Composable
private fun MushafRows(
    settings: ReadSettings,
    viewModel: ReadSettingsViewModel,
    onOpenMushaf: () -> Unit,
) {
    SettingsGroupHeading(stringResource(R.string.reading_group_mushaf), isFirst = true)
    SettingsLinkRow(
        label = stringResource(R.string.mushaf_print),
        value = stringResource(pageTypeNameRes(settings.script)),
        onClick = onOpenMushaf,
        rowKey = SettingRowKeys.QURAN_MUSHAF,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.mushaf_show_page_info),
        checked = settings.showPageInfo,
        onCheckedChange = viewModel::setShowPageInfo,
        rowKey = SettingRowKeys.QURAN_PAGE_INFO,
    )
}

@Composable
private fun ShownUnderArabicRows(
    catalogue: CatalogueState,
    settings: ReadSettings,
    onOpenPackList: (PackListKind) -> Unit,
) {
    SettingsGroupHeading(stringResource(R.string.reading_group_layers))
    SettingsLinkRow(
        label = stringResource(R.string.setting_translations),
        value = translationsSummary(catalogue, settings.translations),
        onClick = { onOpenPackList(PackListKind.Translations) },
        rowKey = SettingRowKeys.QURAN_TRANSLATIONS,
    )
    SettingsLinkRow(
        label = stringResource(R.string.setting_transliteration),
        value =
            layerSummary(catalogue, CatalogueGroup.Transliteration, settings.showTransliteration),
        onClick = { onOpenPackList(PackListKind.Transliteration) },
        rowKey = SettingRowKeys.QURAN_TRANSLITERATION,
    )
    SettingsLinkRow(
        label = stringResource(R.string.setting_word_by_word),
        value = wordByWordRowSummary(catalogue, settings),
        onClick = { onOpenPackList(PackListKind.WordByWord) },
        rowKey = SettingRowKeys.QURAN_WORD_BY_WORD,
    )
}

@Composable
private fun PageRows(
    settings: ReadSettings,
    viewModel: ReadSettingsViewModel,
    showSurahNameMeaning: Boolean,
    onShowSurahNameMeaningChange: (Boolean) -> Unit,
) {
    SettingsGroupHeading(stringResource(R.string.reading_group_page))
    SettingsSwitchRow(
        label = stringResource(R.string.setting_highlight_bookmarks),
        checked = settings.highlightBookmarks,
        onCheckedChange = viewModel::setHighlightBookmarks,
        rowKey = SettingRowKeys.QURAN_HIGHLIGHT_BOOKMARKS,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.setting_volume_keys),
        checked = settings.volumeKeysTurnPages,
        onCheckedChange = viewModel::setVolumeKeysTurnPages,
        rowKey = SettingRowKeys.QURAN_VOLUME_KEYS,
    )
    // One choice for both, so the two stored orientations never disagree; the Quran pages' value
    // stands for the pair when an older install stored two different ones.
    AwnChoiceRow(
        label = stringResource(R.string.reading_group_rotation),
        options = ScreenOrientation.entries,
        selected = settings.quranOrientation,
        optionLabel = { stringResource(orientationLabel(it)) },
        onSelect = viewModel::setScreenRotation,
        rowKey = SettingRowKeys.QURAN_ROTATION,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.setting_surah_name_meaning),
        checked = showSurahNameMeaning,
        onCheckedChange = onShowSurahNameMeaningChange,
        rowKey = SettingRowKeys.QURAN_SURAH_MEANING,
    )
}

@Composable
private fun LayerSizeRow(
    layer: TextLayer,
    settings: ReadSettings,
    viewModel: ReadSettingsViewModel,
) {
    val step = settings.stepOf(layer)
    val scale = TextScale.scaleOf(step)
    SettingsStepperRow(
        label = stringResource(layerLabel(layer)),
        valueText = stringResource(R.string.size_percent, (scale * PERCENT).toInt()),
        decreaseDescription = stringResource(R.string.action_smaller),
        increaseDescription = stringResource(R.string.action_larger),
        onDecrease = { viewModel.setLayerSizeStep(layer, step - 1) },
        onIncrease = { viewModel.setLayerSizeStep(layer, step + 1) },
        canDecrease = step > 0,
        canIncrease = step < TextScale.STEP_COUNT - 1,
        rowKey = layerSizeKey(layer),
    )
}

@StringRes
private fun layerLabel(layer: TextLayer): Int =
    when (layer) {
        TextLayer.Arabic -> R.string.layer_arabic
        TextLayer.Translation -> R.string.layer_translation
        TextLayer.Transliteration -> R.string.setting_transliteration
        TextLayer.WordByWord -> R.string.setting_word_by_word
    }

private fun layerSizeKey(layer: TextLayer): String =
    when (layer) {
        TextLayer.Arabic -> SettingRowKeys.QURAN_SIZE_ARABIC
        TextLayer.Translation -> SettingRowKeys.QURAN_SIZE_TRANSLATION
        TextLayer.Transliteration -> SettingRowKeys.QURAN_SIZE_TRANSLITERATION
        TextLayer.WordByWord -> SettingRowKeys.QURAN_SIZE_WORD_BY_WORD
    }

@StringRes
internal fun orientationLabel(orientation: ScreenOrientation): Int =
    when (orientation) {
        ScreenOrientation.Auto -> R.string.orientation_auto
        ScreenOrientation.Portrait -> R.string.orientation_portrait
        ScreenOrientation.Landscape -> R.string.orientation_landscape
    }
