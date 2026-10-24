package app.awn.quran.feature.duas.ui

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.quranfont.QuranWordText
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.raisedSurface
import app.awn.quran.feature.duas.R
import app.awn.quran.tracker.WordProblem

internal const val REVIEW_WORD_TAG = "dua-review-word"
internal const val REVIEW_CLOSE_TAG = "dua-review-close"

/**
 * The small card above the Recite panel for a word that did not sound right: the word, which plays
 * when tapped, and one line saying what to do.
 */
@Composable
internal fun WordReviewCard(
    key: WordKey,
    problem: WordProblem,
    onPlayWord: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Column(
        modifier
            .fillMaxWidth()
            .shadow(AwnSpacing.cardElevation, AwnShapes.control)
            .clip(AwnShapes.control)
            .background(field.raisedSurface)
            .padding(horizontal = AwnSpacing.medium, vertical = AwnSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuranWordText(
                key = key,
                style = AwnTypography.quranLead,
                color = field.content,
                modifier =
                    Modifier.heightIn(min = AwnSpacing.touchTarget)
                        .clickable(role = Role.Button, onClick = onPlayWord)
                        .testTag(REVIEW_WORD_TAG),
            )
            AwnIconButton(
                icon = UiR.drawable.ic_ui_close,
                contentDescription = stringResource(R.string.duas_review_close),
                onClick = onClose,
                modifier = Modifier.testTag(REVIEW_CLOSE_TAG),
            )
        }
        Text(
            text =
                stringResource(
                    if (problem == WordProblem.Skipped) R.string.duas_review_skipped
                    else R.string.duas_review_wrong,
                ),
            style = AwnTypography.small,
            color = field.content,
        )
    }
}
