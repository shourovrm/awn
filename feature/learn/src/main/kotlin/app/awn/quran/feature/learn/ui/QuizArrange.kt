package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.QuizGroup
import app.awn.quran.feature.learn.course.QuizQuestion
import kotlinx.coroutines.delay

private val DASH_WIDTH: Dp = 2.dp

/**
 * Order: the slots of a right-to-left row fill as the letters are tapped, in the order the answer
 * needs. Once full, the row is marked right or wrong.
 */
@Composable
internal fun OrderQuestion(
    question: QuizQuestion.Order,
    result: Boolean?,
    onAnswer: (Boolean) -> Unit,
) {
    val shuffled = remember(question) { question.items.shuffled() }
    var placed by remember(question) { mutableStateOf(emptyList<String>()) }
    fun pickNext(letter: String) {
        placed = placed + letter
        if (placed.size == question.items.size) onAnswer(question.isRight(placed))
    }
    val solved = result != null
    val shown = if (solved) placed.ifEmpty { question.items } else placed
    val slotState =
        when (result) {
            null -> TileState.Open
            true -> TileState.Right
            false -> if (placed.isEmpty()) TileState.Right else TileState.Wrong
        }
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                question.items.indices.forEach { index ->
                    QuizTile(
                        state = slotState,
                        onClick = null,
                        modifier =
                            Modifier.weight(1f)
                                .height(AwnSpacing.quizTileHeight)
                                .dashedBorder(Awn.field.mutedContent),
                    ) { color ->
                        shown.getOrNull(index)?.let {
                            TileLabel(it, color, AwnTypography.quizLetterSize)
                        }
                    }
                }
            }
        }
        LetterPool(
            letters = if (solved) emptyList() else shuffled.filterNot { it in placed },
            picked = null,
            onPick = if (solved) null else ::pickNext,
        )
    }
}

/**
 * Sort: each group is a dashed box. Tap a letter, then the box it belongs to; a wrong box flashes
 * and the letter stays in the pool.
 */
@Composable
internal fun SortQuestion(
    question: QuizQuestion.Sort,
    result: Boolean?,
    onAnswer: (Boolean) -> Unit,
) {
    val shuffled = remember(question) { question.allItems.shuffled() }
    var selected by remember(question) { mutableStateOf<String?>(null) }
    var placed by remember(question) { mutableStateOf(emptyMap<String, Int>()) }
    var misses by remember(question) { mutableStateOf(0) }
    var flashedGroup by remember(question) { mutableStateOf<Int?>(null) }
    LaunchedEffect(flashedGroup) {
        if (flashedGroup != null) {
            delay(FLASH_MILLIS)
            flashedGroup = null
        }
    }
    fun pickLetter(letter: String) {
        selected = letter
    }
    fun placeIn(group: Int) {
        val letter = selected ?: return
        selected = null
        if (question.isRightGroup(letter, group)) {
            placed = placed + (letter to group)
            if (placed.size == question.allItems.size) onAnswer(misses == 0)
        } else {
            misses += 1
            flashedGroup = group
        }
    }
    val solved = result != null
    val shown = if (solved) solutionOf(question) else placed
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        if (!solved) {
            Text(
                text = stringResource(R.string.quiz_tap_group),
                style = AwnTypography.small,
                color = Awn.field.mutedContent,
            )
        }
        question.groups.forEachIndexed { groupIndex, group ->
            SortGroupBox(
                group = group,
                letters = shown.filterValues { it == groupIndex }.keys.toList(),
                state = if (flashedGroup == groupIndex) TileState.Wrong else TileState.Open,
                onTap = tapIfOpen(!solved) { placeIn(groupIndex) },
            )
        }
        LetterPool(
            letters = if (solved) emptyList() else shuffled.filterNot { it in placed },
            picked = selected,
            onPick = if (solved) null else ::pickLetter,
        )
    }
}

/** Where each letter of a sort belongs, for the answer shown after a question. */
private fun solutionOf(question: QuizQuestion.Sort): Map<String, Int> =
    question.groups.flatMapIndexed { index, group -> group.items.map { it to index } }.toMap()

@Composable
private fun SortGroupBox(
    group: QuizGroup,
    letters: List<String>,
    state: TileState,
    onTap: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        RichText(group.label, style = AwnTypography.bodyStrong)
        QuizTile(
            state = state,
            onClick = onTap,
            modifier =
                Modifier.fillMaxWidth()
                    .height(AwnSpacing.quizTileHeight)
                    .dashedBorder(Awn.field.mutedContent),
        ) { color ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                letters.forEach { TileLabel(it, color, AwnTypography.quizLetterSize) }
            }
        }
    }
}

/** The letters a question offers, [LETTER_COLUMNS] to a row; [onPick] is null once answered. */
@Composable
internal fun LetterPool(
    letters: List<String>,
    picked: String?,
    onPick: ((String) -> Unit)?,
) {
    letters.chunked(LETTER_COLUMNS).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            row.forEach { letter ->
                QuizTile(
                    state = if (letter == picked) TileState.Picked else TileState.Open,
                    onClick = onPick?.let { pick -> { pick(letter) } },
                    modifier = Modifier.weight(1f).height(AwnSpacing.quizLetterTileHeight),
                ) { color ->
                    TileLabel(letter, color, AwnTypography.quizLetterSize)
                }
            }
        }
    }
}

/** A dashed outline drawn over the box, for a place that takes a letter. */
private fun Modifier.dashedBorder(color: Color): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(AwnSpacing.small.toPx()),
        style =
            Stroke(
                width = DASH_WIDTH.toPx(),
                pathEffect =
                    PathEffect.dashPathEffect(
                        floatArrayOf(AwnSpacing.small.toPx(), AwnSpacing.quizDashGap.toPx())
                    ),
            ),
    )
}
