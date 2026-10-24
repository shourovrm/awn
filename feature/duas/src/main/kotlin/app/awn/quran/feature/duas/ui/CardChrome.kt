package app.awn.quran.feature.duas.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.accentOnBar
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.duas.R

/** The three things a card can do. */
internal enum class DuaCardMode(@StringRes val labelResource: Int, @DrawableRes val icon: Int) {
    Listen(R.string.duas_mode_listen, R.drawable.ic_duas_headphones),
    Recite(R.string.duas_mode_recite, R.drawable.ic_duas_mic),
    WordByWord(R.string.duas_mode_words, R.drawable.ic_duas_words),
}

/** Test tag of a mode button. */
internal fun modeTag(mode: DuaCardMode): String = "dua-mode-${mode.name}"

/** Three buttons at the bottom of the screen; the chosen mode has the accent. */
@Composable
internal fun DuaModeBar(
    selected: DuaCardMode,
    onSelect: (DuaCardMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Row(
        modifier.fillMaxWidth().background(field.barBackground).navigationBarsPadding(),
    ) {
        DuaCardMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Column(
                modifier =
                    Modifier.weight(1f)
                        .heightIn(min = AwnSpacing.tabBarHeight)
                        .selectable(selected = isSelected, role = Role.Tab) { onSelect(mode) }
                        .testTag(modeTag(mode)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(mode.icon),
                    contentDescription = null,
                    tint = if (isSelected) field.accentOnBar else field.onBar,
                    modifier = Modifier.size(AwnSpacing.iconSize),
                )
                Text(
                    text = stringResource(mode.labelResource),
                    style = AwnTypography.caption,
                    color = field.onBar,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One dot for each card of the deck; the dot of the card on screen has the accent. */
@Composable
internal fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val field = Awn.field
    Row(
        modifier.fillMaxWidth().padding(vertical = AwnSpacing.small),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            Box(
                Modifier.padding(horizontal = AwnSpacing.small / 2)
                    .size(AwnSpacing.small)
                    .clip(CircleShape)
                    .background(
                        if (index == current) field.buttonFill
                        else field.mutedContent.copy(alpha = DOT_ALPHA),
                    ),
            )
        }
    }
}

private const val DOT_ALPHA = 0.35f
