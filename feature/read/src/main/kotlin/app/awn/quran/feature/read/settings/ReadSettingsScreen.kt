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
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.downloads.CatalogueState
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.core.ui.components.SettingsGroupHeading
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.components.SettingsStepperRow
import app.awn.quran.core.ui.components.SettingsSwitchRow
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies

private const val PERCENT = 100

/**
 * The Reading page of the Settings hub: links to the lists of translations, transliteration and
 * word-by-word meanings, a size stepper for each text layer, two page switches and the rotation
 * defaults. [onOpenPackList] opens the list of one kind. :app embeds it in its own theme.
 */
@Composable
fun ReadingSettingsPage(
    dependencies: ReadDependencies,
    onOpenPackList: (PackListKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberReadSettingsViewModel(dependencies)
    val catalogueViewModel = rememberCatalogueViewModel(dependencies)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val catalogue by catalogueViewModel.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth()) {
        SettingsGroupHeading(stringResource(R.string.reading_group_layers), isFirst = true)
        SettingsLinkRow(
            label = stringResource(R.string.setting_translations),
            value = translationsSummary(catalogue, settings.translations),
            onClick = { onOpenPackList(PackListKind.Translations) },
            rowKey = SettingRowKeys.READING_TRANSLATIONS,
        )
        SettingsLinkRow(
            label = stringResource(R.string.setting_transliteration),
            value =
                layerSummary(
                    catalogue,
                    CatalogueGroup.Transliteration,
                    settings.showTransliteration,
                ),
            onClick = { onOpenPackList(PackListKind.Transliteration) },
            rowKey = SettingRowKeys.READING_TRANSLITERATION,
        )
        SettingsLinkRow(
            label = stringResource(R.string.setting_word_by_word),
            value = layerSummary(catalogue, CatalogueGroup.WordByWord, settings.showWordByWord),
            onClick = { onOpenPackList(PackListKind.WordByWord) },
            rowKey = SettingRowKeys.READING_WORD_BY_WORD,
        )

        SettingsGroupHeading(stringResource(R.string.reading_group_size))
        TextLayer.entries.forEach { layer ->
            LayerSizeRow(layer, settings, viewModel)
        }

        PageAndRotationRows(settings, viewModel)
    }
}

@Composable
private fun PageAndRotationRows(settings: ReadSettings, viewModel: ReadSettingsViewModel) {
    SettingsGroupHeading(stringResource(R.string.reading_group_page))
    SettingsSwitchRow(
        label = stringResource(R.string.setting_highlight_bookmarks),
        checked = settings.highlightBookmarks,
        onCheckedChange = viewModel::setHighlightBookmarks,
        rowKey = SettingRowKeys.READING_HIGHLIGHT_BOOKMARKS,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.setting_volume_keys),
        checked = settings.volumeKeysTurnPages,
        onCheckedChange = viewModel::setVolumeKeysTurnPages,
        rowKey = SettingRowKeys.READING_VOLUME_KEYS,
    )

    SettingsGroupHeading(stringResource(R.string.reading_group_rotation))
    OrientationRow(
        label = R.string.setting_orientation_quran,
        selected = settings.quranOrientation,
        onSelect = viewModel::setQuranOrientation,
        rowKey = SettingRowKeys.READING_ORIENTATION_QURAN,
    )
    OrientationRow(
        label = R.string.setting_orientation_app,
        selected = settings.appOrientation,
        onSelect = viewModel::setAppOrientation,
        rowKey = SettingRowKeys.READING_ORIENTATION_APP,
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
        TextLayer.Arabic -> SettingRowKeys.READING_SIZE_ARABIC
        TextLayer.Translation -> SettingRowKeys.READING_SIZE_TRANSLATION
        TextLayer.Transliteration -> SettingRowKeys.READING_SIZE_TRANSLITERATION
        TextLayer.WordByWord -> SettingRowKeys.READING_SIZE_WORD_BY_WORD
    }

@Composable
private fun OrientationRow(
    @StringRes label: Int,
    selected: ScreenOrientation,
    onSelect: (ScreenOrientation) -> Unit,
    rowKey: String,
) {
    AwnChoiceRow(
        label = stringResource(label),
        options = ScreenOrientation.entries,
        selected = selected,
        optionLabel = { stringResource(orientationLabel(it)) },
        onSelect = onSelect,
        rowKey = rowKey,
    )
}

@StringRes
internal fun orientationLabel(orientation: ScreenOrientation): Int =
    when (orientation) {
        ScreenOrientation.Auto -> R.string.orientation_auto
        ScreenOrientation.Portrait -> R.string.orientation_portrait
        ScreenOrientation.Landscape -> R.string.orientation_landscape
    }

/** The names of the translations that are ticked, or "None" when no translation is shown. */
@Composable
private fun translationsSummary(catalogue: CatalogueState, enabled: List<TranslationId>): String {
    val names =
        enabled.mapNotNull { id -> catalogue.rowFor(id.id) }.map { catalogueItemName(it.item) }
    return if (names.isEmpty()) stringResource(R.string.value_none) else names.joinToString(", ")
}

/** The names of the entries on the phone while the layer is shown, else "Off". */
@Composable
private fun layerSummary(
    catalogue: CatalogueState,
    group: CatalogueGroup,
    isShown: Boolean,
): String {
    if (!isShown) return stringResource(R.string.value_off)
    val names =
        catalogue.rowsIn(group).filter { it.item.isInstalled }.map { catalogueItemName(it.item) }
    return if (names.isEmpty()) stringResource(R.string.value_none) else names.joinToString(", ")
}
