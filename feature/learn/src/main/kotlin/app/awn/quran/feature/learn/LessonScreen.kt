package app.awn.quran.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.Lesson

/** One lesson: title, a Draft label until it is reviewed, the text and its examples. */
@Composable
fun LessonScreen(
    lesson: Lesson,
    language: LearnLanguage,
    examples: List<ExampleLine>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AwnTheme(field = LearnField) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            LearnBar(
                title = lesson.title(language),
                startLinkLabel = stringResource(R.string.back),
                onStartLinkClick = onBackClick,
            )
            Column(
                modifier = Modifier.padding(AwnSpacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
            ) {
                Text(text = lesson.title(language), style = AwnTypography.title)
                if (lesson.isDraft) DraftLabel()
                lesson.paragraphs(language).forEach { paragraph ->
                    Text(text = paragraph, style = AwnTypography.body)
                }
                Text(
                    text = stringResource(R.string.lesson_examples),
                    style = AwnTypography.lead,
                    modifier = Modifier.padding(top = AwnSpacing.medium),
                )
                examples.forEach { ExampleBlock(it) }
            }
        }
    }
}

@Composable
private fun ExampleBlock(example: ExampleLine) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        Text(text = example.reference, style = AwnTypography.bodyStrong)
        if (example.words.isNotEmpty()) HighlightedAyah(example.words)
        Text(text = example.note, style = AwnTypography.body)
    }
}

/** The words that carry the rule are drawn in the field's accent ink; the others in plain ink. */
@Composable
private fun HighlightedAyah(words: List<ExampleWord>) {
    val highlightColor = Awn.field.confirmedWord
    val text =
        remember(words, highlightColor) {
            buildAnnotatedString {
                words.forEachIndexed { index, word ->
                    if (index > 0) append(" ")
                    if (word.isHighlighted) {
                        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                            append(word.text)
                        }
                    } else {
                        append(word.text)
                    }
                }
            }
        }
    Text(
        text = text,
        style = AwnTypography.quran.copy(textDirection = TextDirection.Rtl),
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}
