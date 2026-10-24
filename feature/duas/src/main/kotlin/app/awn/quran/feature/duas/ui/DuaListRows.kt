package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.duas.state.DuasFilter
import java.text.NumberFormat

/** One chip for each filter; the chosen one is filled. */
@Composable
internal fun DuaFilterChips(
    selected: DuasFilter,
    onSelect: (DuasFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = AwnSpacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        DuasFilter.entries.forEach { filter ->
            FilterChip(
                label = stringResource(filter.labelResource),
                isSelected = filter == selected,
                onClick = { onSelect(filter) },
            )
        }
    }
}

// The chip is 40 dp tall inside a 48 dp touch target.
@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val field = Awn.field
    Box(
        modifier =
            Modifier.heightIn(min = AwnSpacing.touchTarget)
                .selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.heightIn(min = AwnSpacing.numberBadge)
                    .clip(AwnShapes.control)
                    .background(if (isSelected) field.buttonFill else field.softFill)
                    .padding(horizontal = AwnSpacing.medium),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = AwnTypography.small,
                color = if (isSelected) field.onButton else field.content,
                maxLines = 1,
            )
        }
    }
}

/** The ochre disc with a group's number or the starter count. */
@Composable
internal fun NumberBadge(number: Int) {
    Box(
        modifier =
            Modifier.size(AwnSpacing.numberBadge).clip(CircleShape).background(AwnColors.ochre),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = NumberFormat.getIntegerInstance().format(number),
            style = AwnTypography.small.copy(fontWeight = AwnTypography.bodyStrong.fontWeight),
            color = AwnColors.ink,
            maxLines = 1,
        )
    }
}

/** A row with a badge, a title, a second line and a chevron; [isSoft] gives it the soft fill. */
@Composable
internal fun DuaListRow(
    number: Int,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSoft: Boolean = false,
) {
    val field = Awn.field
    val fill = if (isSoft) Modifier.clip(AwnShapes.control).background(field.softFill) else Modifier
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.giant)
                .then(fill)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.medium, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        NumberBadge(number)
        Column(Modifier.weight(1f)) {
            Text(text = title, style = AwnTypography.bodyStrong, color = field.content)
            Text(text = detail, style = AwnTypography.small, color = field.content)
        }
        Icon(
            painter = painterResource(UiR.drawable.ic_ui_chevron),
            contentDescription = null,
            tint = field.content,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
    }
}
