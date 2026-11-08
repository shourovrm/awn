package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.QuizQuestion
import app.awn.quran.feature.learn.sound.LocalUnitSounds
import kotlinx.coroutines.launch


/** Pick and Name: a grid of large tiles, one of them right. */
@Composable
internal fun ChoiceQuestion(
    options: List<String>,
    correctIndex: Int,
    result: Boolean?,
    onAnswer: (Boolean) -> Unit,
) {
    var chosen by remember { mutableStateOf<Int?>(null) }
    fun choose(index: Int) {
        chosen = index
        onAnswer(index == correctIndex)
    }
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        options.indices.chunked(CHOICE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                row.forEach { index ->
                    QuizTile(
                        state = choiceState(index, correctIndex, chosen, result),
                        onClick = tapIfOpen(result == null) { choose(index) },
                        modifier = Modifier.weight(1f).height(AwnSpacing.quizChoiceTileHeight),
                    ) { color ->
                        TileLabel(options[index], color, AwnTypography.quizChoiceSize)
                    }
                }
            }
        }
    }
}

/** Once answered, the right option shows as right and the picked wrong option as wrong. */
private fun choiceState(index: Int, correctIndex: Int, chosen: Int?, result: Boolean?): TileState =
    when {
        result == null -> if (index == chosen) TileState.Picked else TileState.Open
        index == correctIndex -> TileState.Right
        index == chosen -> TileState.Wrong
        else -> TileState.Open
    }

/** Listen: a round play button above the same option tiles. */
@Composable
internal fun ListenQuestion(
    question: QuizQuestion.Listen,
    result: Boolean?,
    onAnswer: (Boolean) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        PlayButton(question.clipIds)
        ChoiceQuestion(question.options, question.correctIndex, result, onAnswer)
    }
}

/** Name: the letter shown large above the option tiles that hold its names. */
@Composable
internal fun NameQuestion(
    question: QuizQuestion.Name,
    result: Boolean?,
    onAnswer: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        ArabicText(
            text = fragmentLetters(question.shown),
            size = AwnTypography.quizShownLetterSize,
            color = Awn.field.content,
            modifier = Modifier.fillMaxWidth(),
        )
        ChoiceQuestion(question.options, question.correctIndex, result, onAnswer)
    }
}

@Composable
private fun PlayButton(clipIds: List<String>) {
    val sounds = LocalUnitSounds.current
    val scope = rememberCoroutineScope()
    Box(
        modifier =
            Modifier.size(AwnSpacing.quizPlayButtonSize)
                .clip(CircleShape)
                .background(Awn.field.buttonFill)
                .clickable(
                    role = Role.Button,
                    onClick = { scope.launch { sounds.playClips(clipIds) } },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_learn_play),
            contentDescription = stringResource(R.string.quiz_play_sound),
            tint = Awn.field.onButton,
        )
    }
}
