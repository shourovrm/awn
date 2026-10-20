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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.TranslationInfo
import app.awn.quran.core.ui.components.AwnSwitch
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.ReadIconButton

private const val PERCENT = 100

/**
 * The Reading page of the Settings hub: translations (choose and order), transliteration,
 * word-by-word meanings, the Arabic and translation sizes, highlighting of bookmarks and the volume
 * keys. :app embeds it in its own theme.
 */
@Composable
fun ReadingSettingsPage(dependencies: ReadDependencies, modifier: Modifier = Modifier) {
    val viewModel = rememberReadSettingsViewModel(dependencies)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val availableTranslations by viewModel.availableTranslations.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth()) {
        TranslationSettings(availableTranslations, settings, viewModel)
        SwitchRow(
            label = stringResource(R.string.setting_transliteration),
            checked = settings.showTransliteration,
            onCheckedChange = viewModel::setShowTransliteration,
        )
        SwitchRow(
            label = stringResource(R.string.setting_word_by_word),
            checked = settings.showWordByWord,
            onCheckedChange = viewModel::setShowWordByWord,
        )
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

@Composable
private fun TranslationSettings(
    available: List<TranslationInfo>,
    settings: ReadSettings,
    viewModel: ReadSettingsViewModel,
) {
    Text(
        text = stringResource(R.string.setting_translations),
        style = AwnTypography.body,
        modifier = Modifier.padding(top = AwnSpacing.small),
    )
    orderedForDisplay(available, settings).forEach { info ->
        val position = settings.translations.indexOf(info.id)
        TranslationRow(
            info = info,
            isEnabled = position >= 0,
            canMoveUp = position > 0,
            onEnabledChange = { viewModel.setTranslationEnabled(info.id, it) },
            onMoveUp = { viewModel.moveTranslationUp(info.id) },
        )
    }
}

/** Enabled translations in the user's order first, then the others in manifest order. */
internal fun orderedForDisplay(
    available: List<TranslationInfo>,
    settings: ReadSettings,
): List<TranslationInfo> {
    val enabled = settings.translations.mapNotNull { id -> available.firstOrNull { it.id == id } }
    return enabled + available.filterNot { it in enabled }
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

@Composable
private fun TranslationRow(
    info: TranslationInfo,
    isEnabled: Boolean,
    canMoveUp: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowHeight),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = info.name, style = AwnTypography.body, modifier = Modifier.weight(1f))
        if (!info.installed) {
            Text(
                text = stringResource(R.string.translation_not_installed),
                style = AwnTypography.small,
                color = Awn.field.buttonFill,
            )
        } else {
            if (canMoveUp) {
                ReadIconButton(
                    iconRes = R.drawable.ic_read_move_up,
                    description = stringResource(R.string.action_move_up),
                    onClick = onMoveUp,
                )
            }
            AwnSwitch(checked = isEnabled, onCheckedChange = onEnabledChange)
        }
    }
}
