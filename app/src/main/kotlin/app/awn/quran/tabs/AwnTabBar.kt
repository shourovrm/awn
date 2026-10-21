package app.awn.quran.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnFieldColors
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.core.ui.theme.accentOnBar
import app.awn.quran.core.ui.theme.awnFieldColors

/** The colours of the tab bar: the bar of the Paper field in the active colour mode. */
@Composable
internal fun tabBarColours(): AwnFieldColors =
    awnFieldColors(AwnField.Paper, LocalColourMode.current)

private val INDICATOR_SHAPE = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)

/**
 * The bottom bar: one 64 dp destination per tab, an icon over a 12 sp label. The chosen tab has its
 * icon and a 4 dp mark at the top edge in the accent; its label is in the full ink of the bar and
 * semibold, because ochre on the green bar is only 3.6:1, too little for 12 sp text. The bar fills
 * the space under the system navigation bar.
 */
@Composable
fun AwnTabBar(selected: AwnTab, onSelect: (AwnTab) -> Unit, modifier: Modifier = Modifier) {
    val colours = tabBarColours()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colours.barBackground)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .selectableGroup(),
    ) {
        AwnTab.entries.forEach { tab ->
            TabButton(
                tab = tab,
                isSelected = tab == selected,
                colours = colours,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabButton(
    tab: AwnTab,
    isSelected: Boolean,
    colours: AwnFieldColors,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val iconColour = if (isSelected) colours.accentOnBar else colours.onBar
    Box(
        modifier =
            modifier
                .heightIn(min = AwnSpacing.tabBarHeight)
                .selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                Modifier.align(Alignment.TopCenter)
                    .width(AwnSpacing.tabIndicatorWidth)
                    .height(AwnSpacing.tabIndicatorHeight)
                    .clip(INDICATOR_SHAPE)
                    .background(colours.accentOnBar),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                painter = painterResource(tab.icon),
                contentDescription = null,
                tint = iconColour,
                modifier = Modifier.size(AwnSpacing.iconSize),
            )
            Text(
                text = stringResource(tab.label),
                style =
                    AwnTypography.caption.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                color = colours.onBar,
            )
        }
    }
}
