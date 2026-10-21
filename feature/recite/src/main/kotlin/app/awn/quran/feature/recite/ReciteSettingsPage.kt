package app.awn.quran.feature.recite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.core.ui.components.SettingsSwitchRow
import app.awn.quran.core.ui.components.searchTarget
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import kotlinx.coroutines.launch

/**
 * The Recite and memorise page of the Settings hub: speed, hide words and hint. It draws no
 * background and no title (the hub's bar names it with `R.string.recite_settings_title`).
 * "Memorised after" belongs to Learn and is linked by the hub.
 */
@Composable
fun ReciteSettingsPage(settings: ReciteSettings, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val storedSpeed by settings.speed.collectAsState(initial = RecitationSession.DEFAULT_SPEED)
    val hifzEnabled by settings.hifzEnabled.collectAsState(initial = false)
    val hintEnabled by settings.hintEnabled.collectAsState(initial = true)

    // The slider follows the finger at once and writes to storage only when the finger lifts.
    var draggedSpeed by remember { mutableStateOf<Float?>(null) }

    Column(modifier) {
        Column(
            Modifier.searchTarget(SettingRowKeys.RECITE_SPEED).padding(vertical = AwnSpacing.small),
        ) {
            Text(
                text = stringResource(R.string.recite_speed),
                style = AwnTypography.body,
                color = Awn.field.content,
            )
            SpeedSlider(
                value = draggedSpeed ?: storedSpeed,
                onValueChange = { draggedSpeed = it },
                onValueChangeFinished = {
                    val finalSpeed = draggedSpeed ?: return@SpeedSlider
                    scope.launch { settings.setSpeed(finalSpeed) }
                    draggedSpeed = null
                },
            )
        }
        SettingsSwitchRow(
            label = stringResource(R.string.recite_settings_hide_words),
            checked = hifzEnabled,
            onCheckedChange = { scope.launch { settings.setHifzEnabled(it) } },
            rowKey = SettingRowKeys.RECITE_HIDE_WORDS,
        )
        SettingsSwitchRow(
            label = stringResource(R.string.recite_settings_hint),
            checked = hintEnabled,
            onCheckedChange = { scope.launch { settings.setHintEnabled(it) } },
            rowKey = SettingRowKeys.RECITE_HINT,
        )
    }
}
