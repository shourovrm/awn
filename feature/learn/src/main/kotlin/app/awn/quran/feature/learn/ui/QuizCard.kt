package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.QuizQuestion

/**
 * A quiz screen, one question at a time. A step bar counts the questions, the question is the
 * title, and "I don't know" stays until an answer. After an answer a feedback box shows the
 * explanation and the button goes on: to the next question, or to the next page after the last.
 */
@Composable
internal fun QuizPage(
    questions: List<QuizQuestion>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var position by rememberSaveable { mutableStateOf(0) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        QuizStepBar(current = position, total = questions.size)
        key(position) {
            QuizQuestionView(
                question = questions[position],
                position = position,
                total = questions.size,
                onContinue = {
                    if (position + 1 < questions.size) {
                        position += 1
                    } else {
                        onContinue()
                    }
                },
            )
        }
    }
}

/** One question with its answer state; the state is remembered for this question only. */
@Composable
private fun QuizQuestionView(
    question: QuizQuestion,
    position: Int,
    total: Int,
    onContinue: () -> Unit,
) {
    var result by remember { mutableStateOf<Boolean?>(null) }
    RichText(questionTitle(question), style = AwnTypography.title)
    QuestionBody(question, result, onAnswer = { result = it })
    if (result == null) {
        AwnButton(
            label = stringResource(R.string.quiz_dont_know),
            onClick = { result = false },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        FeedbackBox(
            heading = if (result == true) stringResource(R.string.quiz_right) else null,
            explanation = question.explanation,
        )
        AwnButton(
            label = nextLabel(position, total),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuestionBody(
    question: QuizQuestion,
    result: Boolean?,
    onAnswer: (Boolean) -> Unit,
) {
    when (question) {
        is QuizQuestion.Pick ->
            ChoiceQuestion(question.options, question.correctIndex, result, onAnswer)
        is QuizQuestion.Listen -> ListenQuestion(question, result, onAnswer)
        is QuizQuestion.Name -> NameQuestion(question, result, onAnswer)
        is QuizQuestion.Order -> OrderQuestion(question, result, onAnswer)
        is QuizQuestion.Sort -> SortQuestion(question, result, onAnswer)
        is QuizQuestion.Match -> MatchQuestion(question, result, onAnswer)
        is QuizQuestion.Find -> FindQuestion(question, result, onAnswer)
    }
}

@Composable
private fun questionTitle(question: QuizQuestion): String =
    when (question) {
        is QuizQuestion.Pick -> question.question
        is QuizQuestion.Listen -> stringResource(R.string.quiz_listen_title)
        is QuizQuestion.Name -> stringResource(R.string.quiz_name_title)
        is QuizQuestion.Order -> question.question
        is QuizQuestion.Sort -> question.question
        is QuizQuestion.Match -> question.question
        is QuizQuestion.Find -> question.question
    }

/** The last question leads on to the next page, so its button says Continue. */
@Composable
private fun nextLabel(position: Int, total: Int): String =
    if (position + 1 < total) {
        stringResource(R.string.quiz_next_question, position + 2, total)
    } else {
        stringResource(R.string.quiz_continue)
    }

@Composable
private fun QuizStepBar(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        repeat(total) { index ->
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(AwnSpacing.tabIndicatorHeight)
                        .clip(RoundedCornerShape(AwnSpacing.tabIndicatorHeight))
                        .background(
                            if (index <= current) Awn.field.buttonFill else Awn.field.softFill,
                        ),
            )
        }
    }
}

/** The explanation after an answer, under a heading when the answer was right. */
@Composable
private fun FeedbackBox(heading: String?, explanation: String) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(AwnSpacing.small))
                .background(Awn.field.softFill)
                .padding(AwnSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        if (heading != null) {
            Text(text = heading, style = AwnTypography.bodyStrong, color = Awn.field.content)
        }
        RichText(explanation, style = AwnTypography.body)
    }
}
