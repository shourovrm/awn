@file:Suppress(
    "MatchingDeclarationName",
) // The card's chrome: the mode enum and the bars that switch it.

package app.awn.quran.feature.duas.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.accentOnBar
import app.awn.quran.feature.duas.R

/** The two things a card can do; the text layers are chosen at the top, not here. */
internal enum class DuaCardMode(@StringRes val labelResource: Int, @DrawableRes val icon: Int) {
    Listen(R.string.duas_mode_listen, R.drawable.ic_duas_headphones),
    Recite(R.string.duas_mode_recite, R.drawable.ic_duas_mic),
}

/** Reads the mode saved by an older version, which also had a word-by-word mode. */
internal fun modeFromStoredName(storedName: String): DuaCardMode =
    DuaCardMode.entries.firstOrNull { it.name == storedName } ?: DuaCardMode.Listen

/** Test tag of a mode button. */
internal fun modeTag(mode: DuaCardMode): String = "dua-mode-${mode.name}"

/** Two buttons at the bottom of the screen; the chosen mode has the accent. */
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
