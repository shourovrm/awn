package app.awn.quran.feature.read.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.TranslationInfo
import app.awn.quran.core.ui.components.AwnSwitch
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies

/**
 * The "Reading" group of the Settings screen: script, page view, translations (ordered), the
 * transliteration and word-by-word lines, and the text size. :app embeds it in its settings list,
 * inside its own theme.
 */
@Composable
fun ReadSettingsScreen(dependencies: ReadDependencies, modifier: Modifier = Modifier) {
    val viewModel = rememberReadSettingsViewModel(dependencies)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val availableTranslations by viewModel.availableTranslations.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_reading),
            style = AwnTypography.lead,
            color = Awn.field.buttonFill,
            modifier = Modifier.padding(vertical = AwnSpacing.small),
        )
        ValueRow(
            label = stringResource(R.string.setting_script),
            value = stringResource(settings.script.labelRes()),
            onClick = { viewModel.setScript(settings.script.next()) },
        )
        ValueRow(
            label = stringResource(R.string.setting_page_view),
            value = stringResource(settings.pageView.labelRes()),
            onClick = {
                viewModel.setPageView(
                    if (settings.pageView == PageView.Mushaf) PageView.AyahList
                    else PageView.Mushaf,
                )
            },
        )
        ValueRow(
            label = stringResource(R.string.setting_text_size),
            value = stringResource(settings.textSize.labelRes()),
            onClick = { viewModel.setTextSize(settings.textSize.next()) },
        )
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
        TranslationSettings(availableTranslations, settings, viewModel)
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
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.settingsRowHeight)
                .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body)
        Text(text = value, style = AwnTypography.body, color = Awn.field.buttonFill)
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
        when {
            !info.installed ->
                Text(
                    text = stringResource(R.string.translation_not_installed),
                    style = AwnTypography.small,
                    color = Awn.field.buttonFill,
                )
            else -> {
                if (canMoveUp) {
                    Text(
                        text = stringResource(R.string.action_move_up),
                        style = AwnTypography.bodyStrong,
                        color = Awn.field.buttonFill,
                        modifier =
                            Modifier.heightIn(min = AwnSpacing.touchTarget)
                                .clickable(role = Role.Button, onClick = onMoveUp)
                                .padding(AwnSpacing.small),
                    )
                }
                AwnSwitch(checked = isEnabled, onCheckedChange = onEnabledChange)
            }
        }
    }
}
