package app.awn.quran.feature.recite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.quranfont.QuranWordText
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.tracker.DurationCheck
import app.awn.quran.tracker.DurationVerdict
import app.awn.quran.tracker.TrackerEvent

private const val BAR_TRACK_ALPHA = 0.3f
private val barShape = RoundedCornerShape(AwnSpacing.switchKnobCorner)

/** The test tag of the review card's content. */
const val REVIEW_CARD_TAG = "review-card"

/** The test tag of the Listen action in the review card. */
const val REVIEW_LISTEN_TAG = "review-listen"

/** The test tag of the save-word (bookmark) button in the review card. */
const val REVIEW_SAVE_TAG = "review-save"

/** The test tag of the close button of the review card. */
const val REVIEW_CLOSE_TAG = "review-close"

/** The card is at most this share of the screen height, so the page stays visible above it. */
private const val CARD_MAX_HEIGHT_SHARE = 0.4f

/**
 * The review of flagged words as a card above the recite bar, on the field of the colour mode. The
 * page stays visible and live above it: [focusedKey] is the word on the page that is ringed. The
 * top row holds the play and save-word buttons (as on the word card) at the left and Close at the
 * right; the word is large on its own full-width line under them, then one block per finding.
 * [reviews] holds every flagged word in reading order; Previous and Next call [onFocus] with the
 * neighbour, so the page can turn to it. [isSaved] is null when there is no word list, so no save
 * button shows; that button calls [onSave], or [onRemove] when the word is saved. The card is not
 * modal: nothing behind it is dimmed or blocked.
 */
@Composable
fun ReciteReviewCard(
    reviews: List<WordReview>,
    focusedKey: WordKey?,
    onFocus: (WordKey) -> Unit,
    onListen: (WordKey) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onStop: () -> Unit = {},
    isSaved: Boolean? = null,
    onSave: (WordKey) -> Unit = {},
    onRemove: (WordKey) -> Unit = {},
) {
    // An empty list still shows the card: a card that silently stays closed looked like a bug.
    val position = reviews.indexOfFirst { it.key == focusedKey }.coerceAtLeast(0)
    val review = reviews.getOrNull(position)
    val field = Awn.field
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { (windowHeightPx * CARD_MAX_HEIGHT_SHARE).toDp() }

    Column(
        modifier
            .fillMaxWidth()
            .shadow(AwnSpacing.small, AwnShapes.control)
            .background(field.background, AwnShapes.control)
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AwnSpacing.medium, vertical = TIGHT_GAP)
            .testTag(REVIEW_CARD_TAG),
    ) {
        ReviewCardHeader(
            review = review,
            isPlaying = isPlaying,
            isSaved = isSaved,
            onListen = onListen,
            onStop = onStop,
            onSave = onSave,
            onRemove = onRemove,
            onDismiss = onDismiss,
        )
        if (review != null) {
            ReviewWord(review.key)
        }
        review?.findings?.forEach { finding -> FindingBlock(finding) }
        if (reviews.size > 1) {
            ReviewNavigation(reviews = reviews, position = position, onFocus = onFocus)
        }
        Text(
            text = stringResource(R.string.recite_review_note),
            style = AwnTypography.small,
            modifier = Modifier.padding(bottom = TIGHT_GAP),
        )
    }
}

@Composable
private fun ReviewNavigation(
    reviews: List<WordReview>,
    position: Int,
    onFocus: (WordKey) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SheetAction(
            label = stringResource(R.string.recite_previous),
            enabled = position > 0,
            onClick = { onFocus(reviews[position - 1].key) },
        )
        SheetAction(
            label = stringResource(R.string.recite_next),
            enabled = position < reviews.lastIndex,
            onClick = { onFocus(reviews[position + 1].key) },
        )
    }
}

/** Half the small gap: the card's vertical rhythm, tighter than the rest of the bar. */
internal val TIGHT_GAP = AwnSpacing.small / 2

/** The flagged word on its own line, centred, in the user's Mushaf print and never wrapped. */
@Composable
private fun ReviewWord(key: WordKey) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        QuranWordText(key = key, style = AwnTypography.quran, color = Awn.field.content)
    }
}

@Composable
private fun SheetAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = label,
        style = AwnTypography.bodyStrong,
        color =
            if (enabled) Awn.field.buttonFill else Awn.field.content.copy(alpha = BAR_TRACK_ALPHA),
        modifier =
            modifier
                .defaultMinSize(minHeight = AwnSpacing.touchTarget)
                .clickable(enabled = enabled, onClick = onClick)
                .wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun FindingBlock(finding: ReviewFinding) {
    Column(Modifier.fillMaxWidth().padding(vertical = TIGHT_GAP)) {
        when (finding) {
            ReviewFinding.Skipped -> {
                FindingTitle(stringResource(R.string.recite_verdict_skipped))
            }
            ReviewFinding.WrongWord -> {
                FindingTitle(stringResource(R.string.recite_finding_wrong_word))
                FindingHint(stringResource(R.string.recite_hint_say_again))
            }
            is ReviewFinding.Pronunciation -> PronunciationBlock(finding.substitution)
            is ReviewFinding.Duration -> DurationBlock(finding.check)
        }
    }
}

@Composable
internal fun FindingTitle(title: String) {
    Text(text = title, style = AwnTypography.lead)
}

@Composable
private fun FindingHint(hint: String) {
    Text(text = hint, style = AwnTypography.body)
}

@Composable
private fun PronunciationBlock(substitution: TrackerEvent.Substitution) {
    FindingTitle(stringResource(R.string.recite_finding_pronunciation))
    Row(
        Modifier.fillMaxWidth().padding(vertical = TIGHT_GAP),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        SoundBox(
            label = stringResource(R.string.recite_yours),
            sound = substitution.heard,
            color = Awn.field.wrongWord,
            modifier = Modifier.weight(1f),
        )
        SoundBox(
            label = stringResource(R.string.recite_correct),
            sound = substitution.expected,
            color = Awn.field.confirmedWord,
            modifier = Modifier.weight(1f),
        )
    }
    FindingHint(stringResource(R.string.recite_hint_clear_letter))
}

@Composable
private fun SoundBox(label: String, sound: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = AwnTypography.small)
        Text(text = sound, style = AwnTypography.quran, color = color)
    }
}

@Composable
private fun DurationBlock(check: DurationCheck) {
    FindingTitle(stringResource(R.string.recite_finding_madd))
    Text(text = check.letter, style = AwnTypography.quran)
    Text(
        text =
            stringResource(
                R.string.recite_duration,
                check.measuredSeconds,
                check.expectedSeconds,
            ),
        style = AwnTypography.bodyStrong,
    )
    val barColor: Color =
        if (check.verdict == DurationVerdict.Ok) Awn.field.confirmedWord else Awn.field.wrongWord
    val filledFraction =
        if (check.expectedSeconds > 0f) {
            (check.measuredSeconds / check.expectedSeconds).coerceIn(0f, 1f)
        } else {
            0f
        }
    Box(
        Modifier.fillMaxWidth()
            .height(AwnSpacing.sliderTrackHeight)
            .clip(barShape)
            .background(Awn.field.content.copy(alpha = BAR_TRACK_ALPHA)),
    ) {
        Box(Modifier.fillMaxWidth(filledFraction).fillMaxHeight().background(barColor))
    }
    FindingHint(
        when (check.verdict) {
            DurationVerdict.Ok -> stringResource(R.string.recite_hint_duration_ok)
            DurationVerdict.Underheld -> stringResource(R.string.recite_hint_hold_longer)
            DurationVerdict.Overheld -> stringResource(R.string.recite_hint_hold_shorter)
        },
    )
}
