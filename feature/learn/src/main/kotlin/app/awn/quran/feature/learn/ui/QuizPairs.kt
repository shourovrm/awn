package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.QuizPair
import app.awn.quran.feature.learn.course.QuizQuestion
import kotlinx.coroutines.delay

/**
 * Match: two columns, a letter on the left and its text on the right. Tap one on each side; a right
 * pair fills, a wrong pair flashes.
 */
@Composable
internal fun MatchQuestion(
    question: QuizQuestion.Match,
    result: Boolean?,
    onAnswer: (Boolean) -> Unit,
) {
    val lefts = remember(question) { question.pairs.map { it.left }.shuffled() }
    val rights = remember(question) { question.pairs.map { it.right }.shuffled() }
    var pickedLeft by remember(question) { mutableStateOf<String?>(null) }
    var pickedRight by remember(question) { mutableStateOf<String?>(null) }
    var matched by remember(question) { mutableStateOf(emptyList<QuizPair>()) }
    var misses by remember(question) { mutableStateOf(0) }
    var flashed by remember(question) { mutableStateOf<QuizPair?>(null) }
    LaunchedEffect(flashed) {
        if (flashed != null) {
            delay(FLASH_MILLIS)
            flashed = null
        }
    }
    fun checkPair() {
        val left = pickedLeft ?: return
        val right = pickedRight ?: return
        pickedLeft = null
        pickedRight = null
        if (question.isPair(left, right)) {
            matched = matched + QuizPair(left, right)
            if (matched.size == question.pairs.size) onAnswer(misses == 0)
        } else {
            misses += 1
            flashed = QuizPair(left, right)
        }
    }
    val solved = result != null
    val shownMatches = if (solved) question.pairs else matched
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        if (!solved) MatchHint()
        Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            ) {
                lefts.forEach { left ->
                    val pair = shownMatches.firstOrNull { it.left == left }
                    MatchTile(
                        text = left,
                        state = matchState(pair != null, flashed?.left == left, pickedLeft == left),
                        onTap =
                            tapIfOpen(!solved && pair == null) {
                                pickedLeft = left
                                checkPair()
                            },
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            ) {
                rights.forEach { right ->
                    val pair = shownMatches.firstOrNull { it.right == right }
                    MatchTile(
                        text = right,
                        state =
                            matchState(pair != null, flashed?.right == right, pickedRight == right),
                        onTap =
                            tapIfOpen(!solved && pair == null) {
                                pickedRight = right
                                checkPair()
                            },
                    )
                }
            }
        }
    }
}

private fun matchState(isMatched: Boolean, isFlashed: Boolean, isPicked: Boolean): TileState =
    when {
        isMatched -> TileState.Right
        isFlashed -> TileState.Wrong
        isPicked -> TileState.Picked
        else -> TileState.Open
    }

/**
 * Find: a grid of letters; tap every one that fits. A wrong tap is outlined, and the question is
 * done once every answer is found.
 */
@Composable
internal fun FindQuestion(
    question: QuizQuestion.Find,
    result: Boolean?,
    onAnswer: (Boolean) -> Unit,
) {
    var found by remember(question) { mutableStateOf(emptySet<String>()) }
    var wrongTaps by remember(question) { mutableStateOf(emptySet<String>()) }
    val solved = result != null
    fun tap(item: String) {
        if (!question.isAnswer(item)) {
            wrongTaps = wrongTaps + item
            return
        }
        found = found + item
        if (question.isComplete(found)) onAnswer(wrongTaps.isEmpty())
    }
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        question.items.chunked(LETTER_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                row.forEach { item ->
                    QuizTile(
                        state = findState(item, question, found, wrongTaps, solved),
                        onClick = tapIfOpen(!solved && item !in found) { tap(item) },
                        modifier = Modifier.weight(1f).height(AwnSpacing.quizLetterTileHeight),
                    ) { color ->
                        TileLabel(item, color, AwnTypography.quizLetterSize)
                    }
                }
            }
        }
    }
}

/**
 * Once answered, every answer shows as right; before that, found letters are right and wrong taps
 * outlined.
 */
private fun findState(
    item: String,
    question: QuizQuestion.Find,
    found: Set<String>,
    wrongTaps: Set<String>,
    solved: Boolean,
): TileState =
    when {
        solved && question.isAnswer(item) -> TileState.Right
        item in found -> TileState.Right
        item in wrongTaps -> TileState.Wrong
        else -> TileState.Open
    }

@Composable
private fun MatchTile(
    text: String,
    state: TileState,
    onTap: (() -> Unit)?,
) {
    QuizTile(
        state = state,
        onClick = onTap,
        modifier = Modifier.fillMaxWidth().height(AwnSpacing.quizTileHeight),
    ) { color ->
        TileLabel(text, color, AwnTypography.quizLetterSize)
    }
}

/** The line under the match columns until every pair is found. */
@Composable
private fun MatchHint() {
    Text(
        text = stringResource(R.string.quiz_tap_answer),
        style = AwnTypography.small,
        color = Awn.field.mutedContent,
    )
}
