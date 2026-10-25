package app.awn.quran.feature.learn.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.session.IntervalLabel
import app.awn.quran.feature.learn.session.IntervalUnit
import app.awn.quran.feature.learn.session.RatingChoices

internal const val RATING_BUTTON_TAG_PREFIX = "rating_"

private const val LARGE_FONT_SCALE = 1.3f
private const val UNEARNED_FILL_ALPHA = 0.12f

// Worst to best, left to right. "Forgot" is the learner's word for FSRS "Again".
private val ratingLabels =
    listOf(
        FsrsRating.Again to R.string.revision_forgot,
        FsrsRating.Hard to R.string.revision_hard,
        FsrsRating.Good to R.string.revision_good,
        FsrsRating.Easy to R.string.revision_easy,
    )

/**
 * The four ratings of an answer, each with the time until the word returns under its name. The
 * rating the answer earned is filled, so one plain tap on it is the quick way on. From a large font
 * scale the buttons sit two by two, so that no label is cut.
 */
@Composable
internal fun RatingButtons(
    choices: RatingChoices,
    onRate: (FsrsRating) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowSize = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else ratingLabels.size
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        for (rowRatings in ratingLabels.chunked(rowSize)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            ) {
                for ((rating, label) in rowRatings) {
                    RatingButton(
                        label = label,
                        days = choices.daysByRating.getValue(rating),
                        isEarned = rating == choices.earned,
                        onClick = { onRate(rating) },
                        modifier = Modifier.weight(1f).testTag(RATING_BUTTON_TAG_PREFIX + rating),
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingButton(
    @StringRes label: Int,
    days: Int,
    isEarned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    val fill = if (isEarned) field.buttonFill else field.content.copy(alpha = UNEARNED_FILL_ALPHA)
    val textColor = if (isEarned) field.onButton else field.content
    Column(
        modifier =
            modifier
                .heightIn(min = AwnSpacing.touchTarget)
                .clip(AwnShapes.control)
                .background(fill)
                .semantics { selected = isEarned }
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AwnSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(label),
            style = AwnTypography.bodyStrong,
            color = textColor,
            textAlign = TextAlign.Center,
        )
        Text(
            text = intervalText(IntervalLabel.of(days)),
            style = AwnTypography.small,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun intervalText(label: IntervalLabel): String {
    val plural =
        when (label.unit) {
            IntervalUnit.Day -> R.plurals.interval_days
            IntervalUnit.Week -> R.plurals.interval_weeks
            IntervalUnit.Month -> R.plurals.interval_months
            IntervalUnit.Year -> R.plurals.interval_years
        }
    return pluralStringResource(plural, label.count, label.count)
}
