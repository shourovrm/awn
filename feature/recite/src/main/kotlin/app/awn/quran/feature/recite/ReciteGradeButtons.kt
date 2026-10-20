package app.awn.quran.feature.recite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

/**
 * The four grades in one tap. At large font sizes the labels need two rows of two instead of one
 * row of four, so that no label is cut. The suggested grade is filled; the others are not.
 */
@Composable
internal fun GradeButtons(
    suggested: AyahGrade?,
    onGrade: (AyahGrade) -> Unit,
    colors: ReciteBarColors,
) {
    val rowSize =
        if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else AyahGrade.entries.size
    // Easy to Forgot reads from the best grade to the worst, left to right.
    Column(Modifier.fillMaxWidth()) {
        for (rowGrades in AyahGrade.entries.chunked(rowSize)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            ) {
                for (grade in rowGrades) {
                    GradeButton(
                        grade = grade,
                        isSuggested = grade == suggested,
                        onClick = { onGrade(grade) },
                        colors = colors,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GradeButton(
    grade: AyahGrade,
    isSuggested: Boolean,
    onClick: () -> Unit,
    colors: ReciteBarColors,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(gradeLabels.getValue(grade))
    val fill =
        if (isSuggested) colors.primaryFill else colors.content.copy(alpha = UNSUGGESTED_FILL_ALPHA)
    val textColor = if (isSuggested) colors.onPrimary else colors.content
    Box(
        modifier =
            modifier
                .heightIn(min = AwnSpacing.touchTarget)
                .clip(AwnShapes.control)
                .background(fill)
                .semantics { selected = isSuggested }
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(RECITE_BAR_GRADE_TAG_PREFIX + grade.name.lowercase()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AwnTypography.small,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AwnSpacing.small),
        )
    }
}

private const val UNSUGGESTED_FILL_ALPHA = 0.12f
private const val LARGE_FONT_SCALE = 1.3f

private val gradeLabels =
    mapOf(
        AyahGrade.Easy to R.string.recite_grade_easy,
        AyahGrade.Good to R.string.recite_grade_good,
        AyahGrade.Hard to R.string.recite_grade_hard,
        AyahGrade.Forgot to R.string.recite_grade_forgot,
    )
