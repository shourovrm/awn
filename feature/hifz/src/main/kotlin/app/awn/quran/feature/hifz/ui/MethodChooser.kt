package app.awn.quran.feature.hifz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.hifz.R
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.LessonSize
import app.awn.quran.feature.hifz.method.MethodDefaults
import app.awn.quran.feature.hifz.method.MethodSettings

private val ayahsPerDayChoices = listOf(1, 2, 3, 5, 10, 15, 20)
private val lessonSizeChoices =
    listOf(LessonSize.OnePage, LessonSize.HalfPage, LessonSize.Ayahs(5), LessonSize.Ayahs(10))

/**
 * The list of methods with a tick on the chosen one, the settings of that method and one button.
 * [initialMethod] and [initialSettings] are the current plan, or the first choice on first open.
 */
@Composable
internal fun MethodChooser(
    initialMethod: HifzMethodId,
    initialSettings: MethodSettings,
    onUse: (HifzMethodId, MethodSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var method by remember { mutableStateOf(initialMethod) }
    var settings by remember { mutableStateOf(initialSettings) }
    Column(
        modifier.fillMaxWidth().padding(horizontal = AwnSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(
            text = stringResource(R.string.choose_method_title),
            style = AwnTypography.lead,
            color = Awn.field.content,
            modifier = Modifier.padding(top = AwnSpacing.small, bottom = AwnSpacing.small),
        )
        HifzMethodId.entries.forEach { option ->
            MethodOption(
                method = option,
                isSelected = option == method,
                onSelect = {
                    method = option
                    settings =
                        if (option == initialMethod) initialSettings
                        else MethodDefaults.settingsFor(option)
                },
            )
        }
        if (method != HifzMethodId.TrackOnly) {
            Heading(methodName(method), Modifier.padding(top = AwnSpacing.medium))
            MethodSettingsRows(method, settings) { settings = it }
        }
        AwnButton(
            label = stringResource(R.string.use_method),
            onClick = { onUse(method, settings) },
            modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.medium),
        )
    }
}

@Composable
private fun MethodOption(method: HifzMethodId, isSelected: Boolean, onSelect: () -> Unit) {
    val field = Awn.field
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = AwnSpacing.small),
        verticalAlignment = Alignment.Top,
    ) {
        // Outline when off, filled with a tick when on; the tick is the only difference of shape.
        Box(
            Modifier.size(AwnSpacing.large)
                .clip(CircleShape)
                .then(
                    if (isSelected) Modifier.background(field.buttonFill)
                    else Modifier.border(2.dp, field.content, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_ui_check),
                    contentDescription = null,
                    tint = field.onButton,
                    modifier = Modifier.size(AwnSpacing.medium),
                )
            }
        }
        Column(Modifier.padding(start = AwnSpacing.medium)) {
            Text(text = methodName(method), style = AwnTypography.bodyStrong, color = field.content)
            Text(text = methodDetail(method), style = AwnTypography.body, color = field.content)
        }
    }
}

@Composable
private fun MethodSettingsRows(
    method: HifzMethodId,
    settings: MethodSettings,
    onChange: (MethodSettings) -> Unit,
) {
    if (method == HifzMethodId.SabaqSabqiManzil) {
        AwnChoiceRow(
            label = stringResource(R.string.setting_manzil_cycle),
            options = MethodSettings.manzilCycleChoices,
            selected = settings.manzilCycleDays,
            optionLabel = { daysText(it) },
            onSelect = { onChange(settings.copy(manzilCycleDays = it)) },
        )
        AwnChoiceRow(
            label = stringResource(R.string.setting_lesson_size),
            options = lessonSizeChoices,
            selected = settings.lessonSize,
            optionLabel = { lessonSizeText(it) },
            onSelect = { onChange(settings.copy(lessonSize = it)) },
        )
    } else {
        AwnChoiceRow(
            label = stringResource(R.string.setting_ayahs_per_day),
            options = ayahsPerDayChoices,
            selected = settings.ayahsPerDay,
            optionLabel = { ayahsCountText(it) },
            onSelect = { onChange(settings.copy(ayahsPerDay = it)) },
        )
    }
    AwnChoiceRow(
        label = stringResource(R.string.setting_revision_limit),
        options = MethodSettings.revisionLimitChoices,
        selected = settings.revisionPageLimit,
        optionLabel = { pagesCountText(it) },
        onSelect = { onChange(settings.copy(revisionPageLimit = it)) },
    )
}
