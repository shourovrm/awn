package app.awn.quran.feature.recite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnSpacing

/**
 * The top row of the review card: the play and save-word buttons (as on the word card) at the start
 * and Close at the end. [review] is null when nothing is flagged, so the row says so instead of
 * showing buttons that would act on no word. [isSaved] is null when there is no word list.
 */
@Composable
internal fun ReviewCardHeader(
    review: WordReview?,
    isPlaying: Boolean,
    isSaved: Boolean?,
    onListen: (WordKey) -> Unit,
    onStop: () -> Unit,
    onSave: (WordKey) -> Unit,
    onRemove: (WordKey) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (review == null) {
            FindingTitle(stringResource(R.string.recite_review_empty))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayWordButton(
                    isPlaying = isPlaying,
                    onPlay = { onListen(review.key) },
                    onStop = onStop,
                )
                if (isSaved != null) {
                    SaveWordButton(
                        isSaved = isSaved,
                        onSave = { onSave(review.key) },
                        onRemove = { onRemove(review.key) },
                    )
                }
            }
        }
        CloseButton(onDismiss)
    }
}

@Composable
private fun PlayWordButton(isPlaying: Boolean, onPlay: () -> Unit, onStop: () -> Unit) {
    val icon = if (isPlaying) R.drawable.ic_recite_stop else R.drawable.ic_recite_play
    val descriptionRes = if (isPlaying) R.string.recite_word_stop else R.string.recite_word_play
    CardIconButton(
        style = CardIconStyle.Soft,
        icon = icon,
        description = stringResource(descriptionRes),
        tag = REVIEW_LISTEN_TAG,
        onClick = if (isPlaying) onStop else onPlay,
    )
}

@Composable
private fun SaveWordButton(isSaved: Boolean, onSave: () -> Unit, onRemove: () -> Unit) {
    val style = if (isSaved) CardIconStyle.Filled else CardIconStyle.Ring
    val icon = if (isSaved) R.drawable.ic_recite_bookmark_filled else R.drawable.ic_recite_bookmark
    val descriptionRes = if (isSaved) R.string.recite_word_saved else R.string.recite_word_save
    CardIconButton(
        style = style,
        icon = icon,
        description = stringResource(descriptionRes),
        tag = REVIEW_SAVE_TAG,
        onClick = if (isSaved) onRemove else onSave,
    )
}

/** How a round icon button of the card is drawn; see the mock-up q1-a-corners. */
private enum class CardIconStyle {
    /** A soft tint of the card ink: play. */
    Soft,

    /** An outline in the card ink: save word. */
    Ring,

    /** Ochre with an ink icon: the word is saved. */
    Filled,
}

@Composable
private fun CardIconButton(
    style: CardIconStyle,
    icon: Int,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    val ink = Awn.field.content
    val isLightCard = Awn.field.background.luminance() >= LIGHT_CARD_LUMINANCE
    val circle = CircleShape
    val styled =
        when (style) {
            CardIconStyle.Soft -> Modifier.background(ink.copy(alpha = SOFT_ALPHA), circle)
            CardIconStyle.Ring -> Modifier.border(BorderStroke(RING_WIDTH, ink), circle)
            // Ochre on a light card is 2:1, so the circle gets an ink edge there.
            CardIconStyle.Filled ->
                Modifier.background(AwnColors.ochre, circle)
                    .then(
                        if (isLightCard) {
                            Modifier.border(BorderStroke(RING_WIDTH, ink), circle)
                        } else {
                            Modifier
                        },
                    )
        }
    Box(
        Modifier.requiredSize(AwnSpacing.touchTarget)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(ROUND_BUTTON_SIZE).then(styled), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (style == CardIconStyle.Filled) AwnColors.ink else ink,
            )
        }
    }
}

private const val LIGHT_CARD_LUMINANCE = 0.5f
private const val SOFT_ALPHA = 0.14f
private val RING_WIDTH = AwnSpacing.small / 4
private val ROUND_BUTTON_SIZE = AwnSpacing.touchTarget - AwnSpacing.small / 2

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val description = stringResource(R.string.recite_close)
    Box(
        Modifier.requiredSize(AwnSpacing.touchTarget)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(REVIEW_CLOSE_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_recite_close),
            contentDescription = null,
            tint = Awn.field.content,
        )
    }
}
