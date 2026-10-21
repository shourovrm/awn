// The quiet-list rows of every settings page live together so that they share one rhythm.
@file:Suppress("TooManyFunctions")

package app.awn.quran.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import kotlinx.coroutines.delay

/** The key of the row that a search result points at; null when the page was opened normally. */
val LocalSettingsTargetRow = staticCompositionLocalOf<String?> { null }

private const val TARGET_HIGHLIGHT_MILLISECONDS = 1_500L

/**
 * A settings screen: a top bar with a back arrow above a column of rows. Rows named by [targetRow]
 * scroll into view and are highlighted briefly. A page whose content fills the screen itself passes
 * [scrolls] false, because inside a scroll it would get no height.
 */
@Composable
fun AwnSettingsPage(
    title: String,
    backDescription: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrolls: Boolean = true,
    targetRow: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollModifier = if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier
    CompositionLocalProvider(LocalSettingsTargetRow provides targetRow) {
        Column(modifier.fillMaxSize()) {
            AwnTopBar(title = title, onNavigateBack = onBack, backDescription = backDescription)
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .then(scrollModifier)
                        .padding(horizontal = AwnSpacing.screenMargin),
                content = content,
            )
        }
    }
}

/**
 * Makes the row scroll into view and flash once when [rowKey] is the row a search result opened.
 * The highlight is the soft fill, so it needs no colour of its own.
 */
@Composable
fun Modifier.searchTarget(rowKey: String?): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val isTarget = rowKey != null && rowKey == LocalSettingsTargetRow.current
    var isHighlighted by remember { mutableStateOf(isTarget) }
    if (isTarget) {
        LaunchedEffect(rowKey) {
            requester.bringIntoView()
            delay(TARGET_HIGHLIGHT_MILLISECONDS)
            isHighlighted = false
        }
    }
    val highlight = if (isHighlighted) Modifier.background(Awn.field.softFill) else Modifier
    return this.then(highlight).bringIntoViewRequester(requester)
}

/** A small accent heading that starts a group of rows. */
@Composable
fun SettingsGroupHeading(
    text: String,
    modifier: Modifier = Modifier,
    isFirst: Boolean = false,
    trailingText: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    top = if (isFirst) AwnSpacing.medium else AwnSpacing.groupHeadingTop,
                    bottom = 4.dp,
                )
                .semantics { heading() },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = text,
            style = AwnTypography.bodyStrong,
            color = Awn.field.buttonFill,
            modifier = Modifier.weight(1f),
        )
        if (trailingText != null) {
            Text(text = trailingText, style = AwnTypography.caption, color = Awn.field.mutedContent)
        }
    }
}

/** The label with its muted second line; shared by every row so they line up. */
@Composable
internal fun RowLabels(label: String, secondLine: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text = label, style = AwnTypography.body, color = Awn.field.content)
        if (!secondLine.isNullOrEmpty()) {
            Text(
                text = secondLine,
                style = AwnTypography.small,
                color = Awn.field.mutedContent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowChevron() {
    Icon(
        painter = painterResource(R.drawable.ic_ui_chevron),
        contentDescription = null,
        tint = Awn.field.mutedContent,
        modifier = Modifier.size(AwnSpacing.smallIconSize),
    )
}

private fun rowHeight(hasSecondLine: Boolean): Dp =
    if (hasSecondLine) AwnSpacing.settingsRowTall else AwnSpacing.settingsRowShort

/**
 * A row that opens another page: an optional icon, the label, the current value as a muted second
 * line, and a chevron (or [trailing] in its place, such as a remove button).
 */
@Composable
fun SettingsLinkRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    @DrawableRes icon: Int? = null,
    rowKey: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = rowHeight(!value.isNullOrEmpty()))
                .searchTarget(rowKey)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Awn.field.buttonFill,
                modifier = Modifier.size(AwnSpacing.iconSize),
            )
        }
        RowLabels(label, value, Modifier.weight(1f))
        if (trailing != null) trailing() else RowChevron()
    }
}

/** A row that only shows a value, such as the space used or the version. */
@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    rowKey: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.settingsRowTall)
                .searchTarget(rowKey)
                .padding(vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabels(label, value, Modifier.weight(1f))
    }
}

/** A row with a switch at the end; the whole row toggles it. [hint] is the muted second line. */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    rowKey: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = rowHeight(!hint.isNullOrEmpty()))
                .searchTarget(rowKey)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabels(label, hint, Modifier.weight(1f))
        SwitchGraphic(checked)
    }
}

/**
 * A row with a minus button, a value and a plus button at the end. The buttons are 48 dp touch
 * targets; [valueText] uses tabular figures so the buttons do not move as the number changes.
 */
@Composable
fun SettingsStepperRow(
    label: String,
    valueText: String,
    decreaseDescription: String,
    increaseDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    rowKey: String? = null,
) {
    val accent = Awn.field.buttonFill
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.settingsRowShort)
                .searchTarget(rowKey),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AwnTypography.body,
            color = Awn.field.content,
            modifier = Modifier.weight(1f),
        )
        StepperButton(
            icon = R.drawable.ic_ui_minus,
            description = decreaseDescription,
            isEnabled = canDecrease,
            onClick = onDecrease,
        )
        Text(
            text = valueText,
            style = AwnTypography.bodyStrong.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = accent,
            modifier = Modifier.widthIn(min = AwnSpacing.stepperValueWidth),
            textAlign = TextAlign.Center,
        )
        StepperButton(
            icon = R.drawable.ic_ui_plus,
            description = increaseDescription,
            isEnabled = canIncrease,
            onClick = onIncrease,
        )
    }
}

private const val TABULAR_FIGURES = "tnum"
private const val DISABLED_STEP_ALPHA = 0.35f

@Composable
private fun StepperButton(
    @DrawableRes icon: Int,
    description: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = Awn.field.buttonFill
    AwnIconButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        isEnabled = isEnabled,
        tint = if (isEnabled) accent else accent.copy(alpha = DISABLED_STEP_ALPHA),
    )
}
