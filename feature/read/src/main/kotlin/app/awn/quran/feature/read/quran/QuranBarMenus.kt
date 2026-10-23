package app.awn.quran.feature.read.quran

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import app.awn.quran.core.ui.components.translationNameWithNote
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadIconButton
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.TextLayer
import app.awn.quran.feature.read.settings.TextScale
import app.awn.quran.feature.read.settings.wordByWordMenuLabel

private const val PERCENT = 100
private val SIZE_LABEL_WIDTH = AwnSpacing.giant * 2
private val SIZE_VALUE_WIDTH = AwnSpacing.giant

internal fun layerLabel(layer: TextLayer): Int =
    when (layer) {
        TextLayer.Arabic -> R.string.layer_arabic
        TextLayer.Translation -> R.string.setting_translations
        TextLayer.Transliteration -> R.string.setting_transliteration
        TextLayer.WordByWord -> R.string.setting_word_by_word
    }

/** One tickbox per layer: Arabic, each translation on the phone, transliteration, word by word. */
@Composable
internal fun LayerTicks(state: QuranUiState, actions: TopBarActions) {
    val settings = state.settings
    LayerTick(
        label = stringResource(layerLabel(TextLayer.Arabic)),
        isTicked = settings.showArabic,
        onToggle = { actions.onToggleLayer(TextLayer.Arabic) },
    )
    state.installedTranslations.forEach { translation ->
        LayerTick(
            label = translationNameWithNote(translation.name, translation.id.id),
            isTicked = translation.id in settings.translations,
            onToggle = { actions.onToggleTranslation(translation.id) },
        )
    }
    LayerTick(
        label = stringResource(layerLabel(TextLayer.Transliteration)),
        isTicked = settings.showTransliteration,
        onToggle = { actions.onToggleLayer(TextLayer.Transliteration) },
    )
    LayerTick(
        label =
            wordByWordMenuLabel(
                baseLabel = stringResource(layerLabel(TextLayer.WordByWord)),
                languageCode = state.wordLanguageShown,
                appLocale = LocalConfiguration.current.locales[0],
            ),
        isTicked = settings.showWordByWord,
        onToggle = { actions.onToggleLayer(TextLayer.WordByWord) },
    )
}

/** A row that is one tap target: the whole row toggles, the box only shows the state. */
@Composable
private fun LayerTick(label: String, isTicked: Boolean, onToggle: () -> Unit) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .toggleable(value = isTicked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = AwnSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isTicked,
            onCheckedChange = null,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = field.buttonFill,
                    checkmarkColor = field.onButton,
                ),
        )
        Text(text = label, style = AwnTypography.body)
    }
}

/**
 * One minus and plus row per layer the line-by-line view shows; the Mushaf view has the Arabic row
 * only, and it sets the size of the printed page's letters.
 */
@Composable
internal fun TextSizeRows(state: QuranUiState, actions: TopBarActions) {
    val settings = state.settings
    Column {
        if (settings.pageView == PageView.Mushaf) {
            SizeRow(
                label = stringResource(layerLabel(TextLayer.Arabic)),
                scale = settings.mushafScale,
                canShrink = settings.mushafSizeStep > 0,
                canGrow = settings.mushafSizeStep < TextScale.MUSHAF_LAST_STEP,
                onChange = actions.onMushafSizeChange,
            )
        } else {
            settings.visibleLayers.forEach { layer ->
                val step = settings.stepOf(layer)
                SizeRow(
                    label = stringResource(layerLabel(layer)),
                    scale = TextScale.scaleOf(step),
                    canShrink = step > 0,
                    canGrow = step < TextScale.STEP_COUNT - 1,
                    onChange = { delta -> actions.onTextSizeChange(layer, delta) },
                )
            }
        }
    }
}

@Composable
private fun SizeRow(
    label: String,
    scale: Float,
    canShrink: Boolean,
    canGrow: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.padding(start = AwnSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed columns, so the buttons line up whatever the label says.
        Text(
            text = label,
            style = AwnTypography.body,
            modifier = Modifier.width(SIZE_LABEL_WIDTH),
        )
        ReadIconButton(
            iconRes = R.drawable.ic_read_minus,
            description = stringResource(R.string.action_smaller),
            onClick = { onChange(-1) },
            isEnabled = canShrink,
        )
        Text(
            text = stringResource(R.string.size_percent, (scale * PERCENT).toInt()),
            style = AwnTypography.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(SIZE_VALUE_WIDTH),
        )
        ReadIconButton(
            iconRes = R.drawable.ic_read_plus,
            description = stringResource(R.string.action_larger),
            onClick = { onChange(1) },
            isEnabled = canGrow,
        )
    }
}

@Composable
internal fun OverflowItems(
    state: QuranUiState,
    actions: TopBarActions,
    includesSettings: Boolean = false,
) {
    MenuItem(R.string.menu_search, R.drawable.ic_read_search, actions.onSearch)
    MenuItem(R.string.menu_go_to_page, R.drawable.ic_read_go_to_page, actions.onGoToPage)
    MenuItem(
        label =
            if (state.isPageBookmarked) R.string.menu_remove_page_bookmark
            else R.string.menu_bookmark_page,
        icon =
            if (state.isPageBookmarked) R.drawable.ic_read_bookmark_filled
            else R.drawable.ic_read_bookmark,
        onClick = actions.onBookmarkPage,
    )
    MenuItem(R.string.bar_rotate, R.drawable.ic_read_screen_rotation, actions.onRotate)
    MenuItem(R.string.menu_learn, R.drawable.ic_read_learn, actions.onLearn)
    actions.onWordsOnPage?.let {
        MenuItem(R.string.menu_words_on_page, R.drawable.ic_read_learn, it)
    }
    actions.onToggleMyWords?.let { MyWordsTick(state.settings.showMyWords, it) }
    if (includesSettings) {
        MenuItem(R.string.menu_settings, R.drawable.ic_read_settings, actions.onSettings)
    }
}

/** The "Show my words" switch of the page menu; the menu stays open so the effect shows. */
@Composable
private fun MyWordsTick(isTicked: Boolean, onToggle: () -> Unit) {
    LayerTick(
        label = stringResource(R.string.menu_show_my_words),
        isTicked = isTicked,
        onToggle = onToggle,
    )
}

@Composable
private fun MenuItem(label: Int, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = stringResource(label), style = AwnTypography.body) },
        leadingIcon = {
            Icon(painter = painterResource(icon), contentDescription = null)
        },
        onClick = onClick,
    )
}
