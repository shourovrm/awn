@file:Suppress("TooManyFunctions") // One composable per row of the panel.

package app.awn.quran.feature.listen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.AwnSwitch
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.player.PauseAfterAyah
import app.awn.quran.player.PlaybackOptions
import app.awn.quran.player.SleepTimer
import kotlin.math.roundToInt

private const val SPEED_STEPS_PER_UNIT = 20
private const val SPEED_SLIDER_STEPS = 14
private const val CHIP_BACKGROUND_ALPHA = 0.12f
private const val TRACK_ALPHA = 0.3f

/**
 * The Play tab of the ayah panel, for the `playPanel` slot: a range from [ayah] to the last ayah of
 * [page], repeat counts, speed, "play only this range" and Play.
 */
@Composable
fun PlayRangePanel(
    feature: AudioFeature,
    ayah: AyahRef,
    page: Int,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlayRangeViewModel = viewModel(factory = feature.viewModelFactory)
    LaunchedEffect(ayah, page) { viewModel.load(ayah, page) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection = state.selection ?: return
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(AwnSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        RangeEnd(
            label = stringResource(R.string.panel_from),
            ayah = selection.from,
            chapters = state.chapters,
            onSurah = viewModel::setFromSurah,
            onAyah = viewModel::setFromAyah,
        )
        RangeEnd(
            label = stringResource(R.string.panel_to),
            ayah = selection.to,
            chapters = state.chapters,
            onSurah = viewModel::setToSurah,
            onAyah = viewModel::setToAyah,
        )
        RepeatRow(
            label = stringResource(R.string.panel_repeat_each),
            selected = state.options.repeatEachAyah,
            onSelect = viewModel::setRepeatEachAyah,
        )
        RepeatRow(
            label = stringResource(R.string.panel_repeat_range),
            selected = state.options.repeatRange,
            onSelect = viewModel::setRepeatRange,
        )
        SpeedRow(state.options.speed, viewModel::setSpeed)
        MemorisationControls(state, viewModel)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.panel_only_range),
                style = AwnTypography.body,
                modifier = Modifier.weight(1f),
            )
            AwnSwitch(checked = selection.onlyRange, onCheckedChange = viewModel::setOnlyRange)
        }
        AwnButton(
            label = stringResource(R.string.audio_play),
            onClick = viewModel::play,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun RangeEnd(
    label: String,
    ayah: AyahRef,
    chapters: List<Chapter>,
    onSurah: (Int) -> Unit,
    onAyah: (Int) -> Unit,
) {
    val chapter = chapters.firstOrNull { it.number == ayah.surah }
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        Text(text = label, style = AwnTypography.small)
        Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            Picker(
                text = chapter?.let { "${it.number}  ${it.nameSimple}" } ?: ayah.surah.toString(),
                options = chapters.map { it.number to "${it.number}  ${it.nameSimple}" },
                onSelect = onSurah,
                modifier = Modifier.weight(2f),
            )
            Picker(
                text = ayah.ayah.toString(),
                options = (1..(chapter?.versesCount ?: 1)).map { it to it.toString() },
                onSelect = onAyah,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A 48 dp field that opens a menu of numbered [options] (value to label). */
@Composable
private fun Picker(
    text: String,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val field = Awn.field
    Box(modifier) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = AwnSpacing.touchTarget)
                    .clip(AwnShapes.control)
                    .background(field.content.copy(alpha = CHIP_BACKGROUND_ALPHA))
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(horizontal = AwnSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = AwnTypography.body,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            ListenIcon(R.drawable.ic_listen_dropdown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((value, optionLabel) in options) {
                DropdownMenuItem(
                    text = { Text(optionLabel, style = AwnTypography.body) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun RepeatRow(label: String, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        Text(text = label, style = AwnTypography.small)
        Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            for (plays in REPEAT_CHOICES) {
                Chip(
                    label = repeatLabel(plays),
                    isSelected = plays == selected,
                    onClick = { onSelect(plays) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Pause after each ayah, cumulative repeat and the sleep timer. */
@Composable
private fun MemorisationControls(state: PlayRangeState, viewModel: PlayRangeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        PauseAfterRow(state.options.pauseAfterAyah, viewModel::setPauseAfterAyah)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.panel_cumulative),
                    style = AwnTypography.body,
                    modifier = Modifier.weight(1f),
                )
                AwnSwitch(
                    checked = state.options.cumulative,
                    onCheckedChange = viewModel::setCumulative,
                )
            }
            Text(
                text = stringResource(R.string.panel_cumulative_hint),
                style = AwnTypography.small,
            )
        }
        SleepTimerRow(state.sleepTimer, viewModel::setSleepTimer)
    }
}

@Composable
private fun PauseAfterRow(selected: PauseAfterAyah, onSelect: (PauseAfterAyah) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        Text(text = stringResource(R.string.panel_pause_after), style = AwnTypography.small)
        Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            for (choice in PauseAfterAyah.entries) {
                Chip(
                    label = stringResource(pauseLabel(choice)),
                    isSelected = choice == selected,
                    onClick = { onSelect(choice) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@StringRes
private fun pauseLabel(choice: PauseAfterAyah): Int =
    when (choice) {
        PauseAfterAyah.Off -> R.string.pause_off
        PauseAfterAyah.Once -> R.string.pause_once
        PauseAfterAyah.OneAndHalf -> R.string.pause_one_half
        PauseAfterAyah.Twice -> R.string.pause_twice
    }

@Composable
private fun SleepTimerRow(selected: SleepTimer?, onSelect: (SleepTimer?) -> Unit) {
    val choices = SLEEP_TIMER_CHOICES
    val labels = choices.map { sleepTimerLabel(it) }
    val selectedIndex = choices.indexOf(selected).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        Text(text = stringResource(R.string.panel_sleep), style = AwnTypography.small)
        Picker(
            text = labels[selectedIndex],
            options = labels.mapIndexed { index, label -> index to label },
            onSelect = { index -> onSelect(choices[index]) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun sleepTimerLabel(timer: SleepTimer?): String =
    when (timer) {
        null -> stringResource(R.string.sleep_off)
        SleepTimer.EndOfAyah -> stringResource(R.string.sleep_end_ayah)
        SleepTimer.EndOfSurah -> stringResource(R.string.sleep_end_surah)
        is SleepTimer.Minutes ->
            pluralStringResource(R.plurals.sleep_minutes, timer.minutes, timer.minutes)
    }

@Composable
internal fun Chip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val field = Awn.field
    Box(
        modifier =
            modifier
                .heightIn(min = AwnSpacing.touchTarget)
                .clip(AwnShapes.control)
                .background(
                    if (isSelected) field.buttonFill
                    else field.content.copy(alpha = CHIP_BACKGROUND_ALPHA),
                )
                .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AwnTypography.bodyStrong,
            color = if (isSelected) field.onButton else field.content,
        )
    }
}

@Composable
private fun SpeedRow(speed: Float, onChange: (Float) -> Unit) {
    val field = Awn.field
    Column {
        Row {
            Text(
                text = stringResource(R.string.panel_speed),
                style = AwnTypography.small,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.speed_value, formatSpeed(speed)),
                style = AwnTypography.bodyStrong,
            )
        }
        Slider(
            value = speed,
            onValueChange = {
                onChange((it * SPEED_STEPS_PER_UNIT).roundToInt().toFloat() / SPEED_STEPS_PER_UNIT)
            },
            valueRange = PlaybackOptions.MIN_SPEED..PlaybackOptions.MAX_SPEED,
            steps = SPEED_SLIDER_STEPS,
            colors =
                SliderDefaults.colors(
                    thumbColor = field.buttonFill,
                    activeTrackColor = field.buttonFill,
                    inactiveTrackColor = field.content.copy(alpha = TRACK_ALPHA),
                    activeTickColor = field.onButton,
                    inactiveTickColor = field.content,
                ),
        )
    }
}
