package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.session.Question
import app.awn.quran.feature.learn.session.QuestionPrompt
import app.awn.quran.feature.learn.session.QuestionType
import app.awn.quran.feature.learn.session.SessionFrame
import app.awn.quran.feature.learn.session.SessionState
import app.awn.quran.feature.learn.session.SessionSummary
import app.awn.quran.feature.learn.words.WordLevels

/** What a session screen asks of its host. */
internal class SessionActions(
    val onClose: () -> Unit,
    val onNext: () -> Unit,
    /** The user tapped [FsrsRating] on the answer screen of a plan. */
    val onRate: (FsrsRating) -> Unit = {},
    /** The user chose option [Int], or null for "I don't know". */
    val onAnswer: (Int?) -> Unit,
    val onDone: () -> Unit,
    /** The user has seen the completion screen of word level [Int]. */
    val onLevelCelebrated: (Int) -> Unit = {},
)

/** One screen of a session or quiz: the bar above and the frame under it. */
@Composable
internal fun SessionScreen(
    state: SessionState,
    actions: SessionActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SessionBar(state, onClose = actions.onClose)
        when (val frame = state.frame) {
            null -> Unit
            is SessionFrame.NewWord -> NewWordFrame(frame, actions.onNext)
            is SessionFrame.Reading -> ReadingFrame(frame, actions.onNext)
            is SessionFrame.Ask -> AskFrame(frame, actions.onAnswer)
            is SessionFrame.Answer -> AnswerFrame(frame, actions.onNext, actions.onRate)
            is SessionFrame.Finished -> FinishedFrame(frame.summary, actions)
        }
    }
}

/** A scrolling body with one primary button pinned under it. */
@Composable
internal fun BodyWithButton(
    buttonLabel: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BodyWithBottom(
        bottom = {
            AwnButton(
                label = buttonLabel,
                onClick = onButtonClick,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = modifier,
        content = content,
    )
}

/** A scrolling body with [bottom] pinned under it, at the screen margin. */
@Composable
internal fun BodyWithBottom(
    bottom: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        ) {
            content()
        }
        Box(Modifier.fillMaxWidth().padding(AwnSpacing.screenMargin)) { bottom() }
    }
}

@Composable
private fun NewWordFrame(frame: SessionFrame.NewWord, onNext: () -> Unit) {
    BodyWithButton(stringResource(R.string.answer_next), onNext) {
        ArabicWord(frame.info.representative, style = AwnTypography.poster)
        Text(
            text = frame.info.meaning,
            style = AwnTypography.lead,
            color = Awn.field.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.new_word_in_quran),
            style = AwnTypography.bodyStrong,
            color = Awn.field.content,
        )
        frame.ayahs.forEach { AyahCard(it, highlights = frame.occurrences.toSet()) }
    }
}

@Composable
private fun ReadingFrame(frame: SessionFrame.Reading, onNext: () -> Unit) {
    BodyWithButton(stringResource(R.string.answer_next), onNext) {
        RichText(frame.screen.title, style = AwnTypography.title)
        LessonBlocks(frame.screen.blocks)
    }
}

@Composable
private fun AskFrame(frame: SessionFrame.Ask, onAnswer: (Int?) -> Unit) {
    val question = frame.question
    var selected by remember(question) { mutableStateOf<Int?>(null) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = AwnSpacing.screenMargin)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        ) {
            QuestionHeading(question)
            QuestionPromptView(question.prompt)
        }
        Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            question.options.forEachIndexed { index, markup ->
                AnswerRow(
                    markup = markup,
                    state = if (selected == index) AnswerRowState.Selected else AnswerRowState.Open,
                    showRadio = frame.confirmWithButton,
                    onClick = {
                        if (frame.confirmWithButton) selected = index else onAnswer(index)
                    },
                )
            }
            if (frame.confirmWithButton) {
                AwnButton(
                    label = stringResource(R.string.question_check),
                    onClick = { selected?.let(onAnswer) },
                    enabled = selected != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            QuietButton(stringResource(R.string.question_dont_know), onClick = { onAnswer(null) })
        }
    }
}

@Composable
private fun QuestionHeading(question: Question) {
    val resource =
        when (question.type) {
            QuestionType.Meaning -> R.string.question_meaning
            QuestionType.Reverse -> R.string.question_reverse
            QuestionType.FillAyah -> R.string.question_fill
            QuestionType.Grammar -> null
        }
    val text =
        resource?.let { stringResource(it) }
            ?: (question.prompt as? QuestionPrompt.LessonCheck)?.question.orEmpty()
    RichText(
        text = text,
        style = AwnTypography.small,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.small),
    )
}

@Composable
private fun QuestionPromptView(prompt: QuestionPrompt) {
    when (prompt) {
        is QuestionPrompt.Markup ->
            RichText(
                text = prompt.text,
                style =
                    if (prompt.text.contains("{{")) AwnTypography.poster else AwnTypography.lead,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = AwnSpacing.large),
            )
        is QuestionPrompt.AyahWithBlank ->
            AyahCard(prompt.ayah, blankPosition = prompt.blankPosition, showTranslation = true)
        is QuestionPrompt.LessonCheck ->
            prompt.ayah?.let { AyahCard(it, highlight = prompt.highlight, showTranslation = false) }
    }
}

@Composable
private fun AnswerFrame(
    frame: SessionFrame.Answer,
    onNext: () -> Unit,
    onRate: (FsrsRating) -> Unit,
) {
    val ratingChoices = frame.ratingChoices
    if (ratingChoices == null) {
        BodyWithButton(stringResource(R.string.answer_next), onNext) { AnswerBody(frame) }
    } else {
        BodyWithBottom(bottom = { RatingButtons(ratingChoices, onRate) }) { AnswerBody(frame) }
    }
}

@Composable
private fun AnswerBody(frame: SessionFrame.Answer) {
    val question = frame.question
    question.answerWord?.let { ArabicWord(it, style = AwnTypography.title) }
    AnswerRow(
        markup = question.options[question.correctIndex],
        state = AnswerRowState.Right,
        onClick = null,
    )
    val chosen = frame.chosenIndex
    if (chosen != null && !frame.isCorrect) {
        MutedLine(stringResource(R.string.answer_your_choice))
        AnswerRow(markup = question.options[chosen], state = AnswerRowState.Wrong, onClick = null)
    }
    if (question.explanation.isNotEmpty()) LessonBlocks(question.explanation)
    question.answerWord?.let { word ->
        AyahCard(
            AyahRef(word.surah, word.ayah),
            highlights = question.answerOccurrences.toSet() + word,
        )
    }
}

/** The level-complete screen first when the session finished a level, then the usual end. */
@Composable
private fun FinishedFrame(summary: SessionSummary, actions: SessionActions) {
    var hasSeenLevel by remember(summary) { mutableStateOf(false) }
    val level =
        summary.finishedLevel?.let { number -> WordLevels.all.firstOrNull { it.number == number } }
    if (level == null || hasSeenLevel) {
        EndFrame(summary, actions.onDone)
    } else {
        // The next level's first set is the next session, which the words page starts.
        LevelCompleteFrame(
            level = level,
            onNextLevel = {
                actions.onLevelCelebrated(level.number)
                actions.onDone()
            },
            onLater = {
                actions.onLevelCelebrated(level.number)
                hasSeenLevel = true
            },
        )
    }
}
