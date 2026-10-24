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
import app.awn.quran.core.ui.theme.mutedContent
import kotlinx.coroutines.launch

/**
 * The Recite and Hifz page of the Settings hub: hide words, first-letter hint, then
 * [memorisedAfterRow], then the follow-pace slider. "Memorised after" belongs to Learn's rules and
 * is drawn by :app, so it comes in as a slot between the switches and the slider. The page draws no
 * background and no title (the hub's bar names it with `R.string.recite_settings_title`).
 */
@Composable
fun ReciteSettingsPage(
    settings: ReciteSettings,
    modifier: Modifier = Modifier,
    memorisedAfterRow: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val storedSpeed by settings.speed.collectAsState(initial = RecitationSession.DEFAULT_SPEED)
    val hifzEnabled by settings.hifzEnabled.collectAsState(initial = false)
    val hintEnabled by settings.hintEnabled.collectAsState(initial = true)

    // The slider follows the finger at once and writes to storage only when the finger lifts.
    var draggedSpeed by remember { mutableStateOf<Float?>(null) }

    Column(modifier) {
        SettingsSwitchRow(
            label = stringResource(R.string.recite_settings_hide_words),
            hint = stringResource(R.string.recite_settings_hide_words_hint),
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
        memorisedAfterRow()
        FollowPaceRow(
            value = draggedSpeed ?: storedSpeed,
            onValueChange = { draggedSpeed = it },
            onValueChangeFinished = {
                val finalSpeed = draggedSpeed ?: return@FollowPaceRow
                scope.launch { settings.setSpeed(finalSpeed) }
                draggedSpeed = null
            },
        )
    }
}

/** The label and hint of the pace, with the slider from patient (0) to eager (1) under them. */
@Composable
private fun FollowPaceRow(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val label = stringResource(R.string.recite_follow_pace)
    Column(
        Modifier.searchTarget(SettingRowKeys.RECITE_FOLLOW_PACE)
            .padding(vertical = AwnSpacing.small),
    ) {
        Text(text = label, style = AwnTypography.body, color = Awn.field.content)
        Text(
            text = stringResource(R.string.recite_follow_pace_hint),
            style = AwnTypography.small,
            color = Awn.field.mutedContent,
        )
        SpeedSlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            description = label,
            startLabel = stringResource(R.string.recite_pace_patient),
            endLabel = stringResource(R.string.recite_pace_eager),
        )
    }
}
