package app.awn.quran.feature.read.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.read.R

/** Whether the word on the card is in the user's own word list. */
enum class MyWordState {
    NotAdded,
    Added,
}

/** The test tag of the save-word button on the word card. */
const val WORD_SAVE_TAG = "word-save"

/** How a round icon button on the word card is drawn; see the q1-a-corners mock-up. */
internal enum class WordButtonStyle {
    /** A soft tint of the card ink: the Play button. */
    Soft,

    /** An outline in the card ink: the word is not saved. */
    Outline,

    /** Ochre with an ink icon: the word is saved. */
    Filled,
}

/**
 * The save button under the meaning: an outlined bookmark in an outlined circle while the word is
 * not in the list (a tap adds it), a filled bookmark on an ochre circle once it is (a tap takes it
 * out again, so a word saved by mistake can be undone).
 */
@Composable
internal fun SaveWordButton(state: MyWordState, onAdd: () -> Unit, onRemove: () -> Unit) {
    val isAdded = state == MyWordState.Added
    val descriptionRes = if (isAdded) R.string.word_saved else R.string.word_save
    WordRoundButton(
        description = stringResource(descriptionRes),
        icon = if (isAdded) R.drawable.ic_read_bookmark_filled else R.drawable.ic_read_bookmark,
        style = if (isAdded) WordButtonStyle.Filled else WordButtonStyle.Outline,
        onClick = if (isAdded) onRemove else onAdd,
        modifier = Modifier.testTag(WORD_SAVE_TAG),
    )
}

/**
 * One icon-only button of the word card, named for screen readers by [description]. Ochre on a
 * light bar is 1.9:1, so the filled circle gets an ink border there; on the green, dark and black
 * bars it is 3.6:1 or more without one.
 */
@Composable
internal fun WordRoundButton(
    description: String,
    icon: Int,
    style: WordButtonStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = Awn.field.onBar
    val isLightBar = Awn.field.barBackground.luminance() >= LIGHT_BAR_LUMINANCE
    val inkBorder = Modifier.border(BorderStroke(OUTLINE_WIDTH, ink), CircleShape)
    val styled =
        when (style) {
            WordButtonStyle.Soft -> Modifier.background(ink.copy(alpha = SOFT_ALPHA), CircleShape)
            WordButtonStyle.Outline -> inkBorder
            WordButtonStyle.Filled ->
                Modifier.background(AwnColors.ochre, CircleShape)
                    .then(if (isLightBar) inkBorder else Modifier)
        }
    val iconColor = if (style == WordButtonStyle.Filled) AwnColors.ink else ink
    Box(
        modifier
            .size(AwnSpacing.touchTarget)
            .clip(CircleShape)
            .then(styled)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = iconColor)
    }
}

private const val LIGHT_BAR_LUMINANCE = 0.5f
private const val SOFT_ALPHA = 0.14f
private val OUTLINE_WIDTH = AwnSpacing.small / 4
