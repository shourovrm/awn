package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.raisedSurface
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.data.CardLayers

internal const val LAYERS_BUTTON_TAG = "dua-layers-button"
internal const val WORD_BY_WORD_TICK_TAG = "dua-layer-word-by-word"
internal const val TRANSLATION_TICK_TAG = "dua-layer-translation"
internal const val TRANSLITERATION_TICK_TAG = "dua-layer-transliteration"

/** The layers button of the top bar; it opens the same three ticks as the Mushaf's layers menu. */
@Composable
internal fun CardLayersButton(
    layers: CardLayers,
    onLayersChange: (CardLayers) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isOpen by remember { mutableStateOf(false) }
    // The menu anchors to its parent, so the button and the menu share one box.
    Box(modifier) {
        AwnIconButton(
            icon = R.drawable.ic_duas_layers,
            contentDescription = stringResource(R.string.duas_layers),
            onClick = { isOpen = true },
            modifier = Modifier.testTag(LAYERS_BUTTON_TAG),
            tint = Awn.field.onBar,
        )
        // The surface, shape and shadow are those of the menus of :core:ui (AwnChoiceRow), not
        // Material's tinted default.
        DropdownMenu(
            expanded = isOpen,
            onDismissRequest = { isOpen = false },
            shape = AwnShapes.menu,
            containerColor = Awn.field.raisedSurface,
            tonalElevation = 0.dp,
            shadowElevation = AwnSpacing.small,
        ) {
            LayerTick(
                label = stringResource(R.string.duas_layer_word_by_word),
                isTicked = layers.showWordByWord,
                onToggle = { onLayersChange(layers.copy(showWordByWord = !layers.showWordByWord)) },
                modifier = Modifier.testTag(WORD_BY_WORD_TICK_TAG),
            )
            LayerTick(
                label = stringResource(R.string.duas_layer_translation),
                isTicked = layers.showTranslation,
                onToggle = {
                    onLayersChange(layers.copy(showTranslation = !layers.showTranslation))
                },
                modifier = Modifier.testTag(TRANSLATION_TICK_TAG),
            )
            LayerTick(
                label = stringResource(R.string.duas_layer_transliteration),
                isTicked = layers.showTransliteration,
                onToggle = {
                    onLayersChange(layers.copy(showTransliteration = !layers.showTransliteration))
                },
                modifier = Modifier.testTag(TRANSLITERATION_TICK_TAG),
            )
        }
    }
}

/** A row that is one tap target: the whole row toggles, the box only shows the state. */
@Composable
private fun LayerTick(
    label: String,
    isTicked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Row(
        modifier =
            modifier
                .fillMaxWidth()
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
        Text(text = label, style = AwnTypography.body, color = field.content)
    }
}
