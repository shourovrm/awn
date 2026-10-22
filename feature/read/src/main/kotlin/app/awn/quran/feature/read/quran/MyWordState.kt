package app.awn.quran.feature.read.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R

/** Whether the word on the card is in the user's own word list. */
enum class MyWordState {
    NotAdded,
    Added,
}

/** The test tag of the save-word button on the word card. */
const val WORD_SAVE_TAG = "word-save"

/** How a labelled button on the word card is drawn; see the mock-ups in sessions/round7-mocks. */
internal enum class WordPillStyle {
    /** A soft tint of the card ink: the Play button. */
    Soft,

    /** An outline in the card ink: Save word. */
    Outline,

    /** Ochre with ink text: Saved. */
    Filled,
}

/**
 * The save button under the meaning: an outlined bookmark and "Save word" while the word is not in
 * the list (a tap adds it), a filled bookmark on an ochre pill reading "Saved" once it is (a tap
 * takes it out again, so a word saved by mistake can be undone).
 */
@Composable
internal fun SaveWordPill(state: MyWordState, onAdd: () -> Unit, onRemove: () -> Unit) {
    val isAdded = state == MyWordState.Added
    WordPill(
        label = stringResource(if (isAdded) R.string.word_saved else R.string.word_save),
        icon = if (isAdded) R.drawable.ic_read_bookmark_filled else R.drawable.ic_read_bookmark,
        style = if (isAdded) WordPillStyle.Filled else WordPillStyle.Outline,
        onClick = if (isAdded) onRemove else onAdd,
        modifier = Modifier.testTag(WORD_SAVE_TAG),
    )
}

/**
 * One labelled button of the word card. The pill is the card's ink on the card's bar colour, or
 * ochre with ink text; an ochre pill on a light bar (1.9:1) gets an ink border so its edge is
 * visible there too, and on the green, dark and black bars it is 3.6:1 or more without one.
 */
@Composable
internal fun WordPill(
    label: String,
    icon: Int,
    style: WordPillStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    val ink = field.onBar
    val contentColor = if (style == WordPillStyle.Filled) AwnColors.ink else ink
    val isLightBar = field.barBackground.luminance() >= LIGHT_BAR_LUMINANCE
    val shape = CircleShape
    val styled =
        when (style) {
            WordPillStyle.Soft -> Modifier.background(ink.copy(alpha = SOFT_ALPHA), shape)
            WordPillStyle.Outline -> Modifier.border(BorderStroke(OUTLINE_WIDTH, ink), shape)
            WordPillStyle.Filled ->
                Modifier.background(AwnColors.ochre, shape)
                    .then(
                        if (isLightBar) {
                            Modifier.border(BorderStroke(OUTLINE_WIDTH, ink), shape)
                        } else {
                            Modifier
                        },
                    )
        }
    Row(
        modifier
            .defaultMinSize(minHeight = AwnSpacing.touchTarget)
            .clip(shape)
            .then(styled)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AwnSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
        Text(text = label, style = AwnTypography.bodyStrong, color = contentColor)
    }
}

private const val LIGHT_BAR_LUMINANCE = 0.5f
private const val SOFT_ALPHA = 0.14f
private val OUTLINE_WIDTH = AwnSpacing.small / 4
