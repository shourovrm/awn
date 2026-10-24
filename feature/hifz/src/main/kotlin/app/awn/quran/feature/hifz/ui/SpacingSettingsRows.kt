package app.awn.quran.feature.hifz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.awn.quran.core.ui.components.AwnChoiceDialog
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.hifz.R
import app.awn.quran.feature.hifz.method.MethodSettings
import app.awn.quran.feature.hifz.method.RevisionSpacing

const val SPACING_ADD_STEP_TAG = "hifz-spacing-add-step"

/** A step chip's tag is this prefix plus its days. */
const val SPACING_STEP_TAG_PREFIX = "hifz-spacing-step-"

private val stepDayChoices = listOf(1, 2, 3, 5, 7, 10, 14, 21, 30, 45, 60, 90, 120, 180)

/**
 * How old pages come back: the Manzil cycle with its length, fixed steps the user edits, or the
 * gaps set by the grades. Recent lessons are not affected; Sabqi still revises them every day.
 */
@Composable
internal fun SpacingSettingsRows(settings: MethodSettings, onChange: (MethodSettings) -> Unit) {
    val spacing = settings.revisionSpacing
    val fixed = spacing as? RevisionSpacing.FixedSteps ?: RevisionSpacing.FixedSteps()
    AwnChoiceRow(
        label = stringResource(R.string.setting_spacing),
        options = listOf(RevisionSpacing.ManzilCycle, fixed, RevisionSpacing.Adaptive),
        selected = spacing,
        optionLabel = { spacingName(it) },
        onSelect = { onChange(settings.copy(revisionSpacing = it)) },
        hint = spacingHint(spacing),
    )
    when (spacing) {
        RevisionSpacing.ManzilCycle ->
            AwnChoiceRow(
                label = stringResource(R.string.setting_manzil_cycle),
                options = MethodSettings.manzilCycleChoices,
                selected = settings.manzilCycleDays,
                optionLabel = { daysText(it) },
                onSelect = { onChange(settings.copy(manzilCycleDays = it)) },
            )
        is RevisionSpacing.FixedSteps ->
            StepEditor(spacing.stepDays) { steps ->
                onChange(settings.copy(revisionSpacing = RevisionSpacing.FixedSteps(steps)))
            }
        RevisionSpacing.Adaptive -> Unit
    }
}

@Composable
private fun spacingName(spacing: RevisionSpacing): String =
    stringResource(
        when (spacing) {
            RevisionSpacing.ManzilCycle -> R.string.spacing_manzil_cycle
            is RevisionSpacing.FixedSteps -> R.string.spacing_fixed_steps
            RevisionSpacing.Adaptive -> R.string.spacing_adaptive
        },
    )

@Composable
private fun spacingHint(spacing: RevisionSpacing): String =
    stringResource(
        when (spacing) {
            RevisionSpacing.ManzilCycle -> R.string.spacing_hint_cycle
            is RevisionSpacing.FixedSteps -> R.string.spacing_hint_fixed
            RevisionSpacing.Adaptive -> R.string.spacing_hint_adaptive
        },
    )

/**
 * The steps as chips in order; tapping a step removes it while more than one is left, and the last
 * chip adds a step longer than the current last one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepEditor(steps: List<Int>, onChange: (List<Int>) -> Unit) {
    var isChoosing by remember { mutableStateOf(false) }
    Heading(stringResource(R.string.spacing_steps), Modifier.padding(top = AwnSpacing.small))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        steps.forEach { days ->
            val removeLabel = stringResource(R.string.spacing_remove_step, days)
            StepChip(
                text = stringResource(R.string.spacing_step_days, days),
                isAdd = false,
                enabled = steps.size > 1,
                modifier =
                    Modifier.testTag(SPACING_STEP_TAG_PREFIX + days).semantics {
                        contentDescription = removeLabel
                    },
            ) {
                onChange(steps - days)
            }
        }
        val longer = stepDayChoices.filter { it > steps.last() }
        if (longer.isNotEmpty()) {
            StepChip(
                text = stringResource(R.string.spacing_add_step),
                isAdd = true,
                enabled = true,
                modifier = Modifier.testTag(SPACING_ADD_STEP_TAG),
            ) {
                isChoosing = true
            }
        }
    }
    if (isChoosing) {
        val longer = stepDayChoices.filter { it > steps.last() }
        AwnChoiceDialog(
            title = stringResource(R.string.spacing_choose_step),
            options = longer,
            selected = longer.first(),
            optionLabel = { daysText(it) },
            onSelect = { days ->
                onChange(steps + days)
                isChoosing = false
            },
            onDismiss = { isChoosing = false },
        )
    }
}

@Composable
private fun StepChip(
    text: String,
    isAdd: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val field = Awn.field
    Box(
        modifier
            .heightIn(min = AwnSpacing.touchTarget)
            .clip(AwnShapes.control)
            .background(field.softFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = AwnSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AwnTypography.bodyStrong,
            color = if (isAdd) field.buttonFill else field.content,
        )
    }
}
