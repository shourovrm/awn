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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

private const val BAR_TRACK_ALPHA = 0.3f
private val barShape = RoundedCornerShape(AwnSpacing.switchKnobCorner)

/** The test tag of the Listen action in the review sheet. */
const val REVIEW_LISTEN_TAG = "review-listen"

/**
 * The review of flagged words on the paper field: the word large at the top right, Listen at the
 * top left, then one block per finding. [reviews] holds the flagged words of one ayah in order; the
 * sheet opens at [startKey] and offers Previous and Next when there is more than one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciteReviewSheet(
    reviews: List<WordReview>,
    startKey: WordKey,
    words: List<Word>,
    onListen: (WordKey) -> Unit,
    onDismiss: () -> Unit,
) {
    var position by remember {
        mutableIntStateOf(reviews.indexOfFirst { it.key == startKey }.coerceAtLeast(0))
    }
    val review = reviews.getOrNull(position) ?: return
    val wordText = words.firstOrNull { it.key == review.key }?.textUthmani.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AwnColors.paper,
        contentColor = AwnColors.ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AwnSpacing.screenMargin)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetAction(
                    label = stringResource(R.string.recite_listen),
                    modifier = Modifier.testTag(REVIEW_LISTEN_TAG),
                    onClick = { onListen(review.key) },
                )
                Text(text = wordText, style = AwnTypography.title)
            }
            review.findings.forEach { finding -> FindingBlock(finding) }
            if (reviews.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SheetAction(
                        label = stringResource(R.string.recite_previous),
                        enabled = position > 0,
                        onClick = { position-- },
                    )
                    SheetAction(
                        label = stringResource(R.string.recite_next),
                        enabled = position < reviews.lastIndex,
                        onClick = { position++ },
                    )
                }
            }
        }
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
        color = if (enabled) AwnColors.green else AwnColors.ink.copy(alpha = BAR_TRACK_ALPHA),
        modifier =
            modifier
                .defaultMinSize(minHeight = AwnSpacing.touchTarget)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = AwnSpacing.small),
    )
}

@Composable
private fun FindingBlock(finding: ReviewFinding) {
    Column(Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small)) {
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
private fun FindingTitle(title: String) {
    Text(text = title, style = AwnTypography.lead)
}

@Composable
private fun FindingHint(hint: String) {
    Text(text = hint, style = AwnTypography.body)
}

@Composable
private fun PronunciationBlock(substitution: SubstitutionFinding) {
    FindingTitle(stringResource(R.string.recite_finding_pronunciation))
    Row(
        Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        SoundBox(
            label = stringResource(R.string.recite_yours),
            sound = substitution.heard,
            color = AwnColors.red,
            modifier = Modifier.weight(1f),
        )
        SoundBox(
            label = stringResource(R.string.recite_correct),
            sound = substitution.expected,
            color = AwnColors.green,
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
private fun DurationBlock(check: DurationFinding) {
    FindingTitle(check.rule)
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
        if (check.verdict == DurationVerdict.Ok) AwnColors.green else AwnColors.red
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
            .background(AwnColors.ink.copy(alpha = BAR_TRACK_ALPHA)),
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
