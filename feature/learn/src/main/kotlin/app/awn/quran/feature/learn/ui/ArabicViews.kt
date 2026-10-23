package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.core.ui.theme.awnFieldColors
import app.awn.quran.feature.learn.course.LessonMarkup

/** The paper card colours that ayahs and answers sit on, in every colour mode. */
@Composable internal fun paperColors() = awnFieldColors(AwnField.Paper, LocalColourMode.current)

/**
 * Lesson text with its Quran words and Arabic runs drawn in the learning font. [highlight] marks
 * one word with the selected-ayah fill. An Arabic run is drawn at [arabicSize]: never smaller than
 * the lead size, and as large as [style] when that is larger, so a title-sized answer tile does not
 * hold a lead-sized letter.
 */
@Composable
internal fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AwnTypography.body,
    color: Color = Awn.field.content,
    highlight: WordKey? = null,
    textAlign: TextAlign = TextAlign.Start,
    arabicSize: TextUnit = maxOf(style.fontSize, AwnTypography.lead.fontSize),
) {
    val environment = LocalLearnEnvironment.current
    val keys = remember(text, highlight) { LessonMarkup.wordKeys(text) }
    val texts = rememberWordTexts(keys)
    val fill = Awn.field.selectedAyahFill
    val annotated =
        remember(text, texts, highlight, environment, fill, arabicSize) {
            LessonMarkup.build(
                text = text,
                wordTexts = texts,
                arabicFont = environment?.arabic?.fontFamily,
                arabicSize = arabicSize,
                highlight = highlight,
                highlightStyle = SpanStyle(background = fill),
            )
        }
    Text(
        text = annotated,
        style = style.copy(textAlign = textAlign),
        color = color,
        modifier = modifier,
    )
}

/** One Quran word at the size of [style], centred: the word of a question or a new word. */
@Composable
internal fun ArabicWord(
    key: WordKey,
    modifier: Modifier = Modifier,
    style: TextStyle = AwnTypography.title,
    color: Color = Awn.field.content,
) {
    val environment = LocalLearnEnvironment.current
    val texts = rememberWordTexts(remember(key) { setOf(key) })
    Text(
        text = texts[key].orEmpty(),
        style =
            style.copy(
                fontFamily = environment?.arabic?.fontFamily ?: style.fontFamily,
                textDirection = TextDirection.Rtl,
                textAlign = TextAlign.Center,
            ),
        color = color,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * An ayah on a paper card: its words right to left, [highlight] marked, the word at [blankPosition]
 * replaced by a gap, and the translation and reference under it when [showTranslation] is set.
 */
@Composable
internal fun AyahCard(
    ayah: AyahRef,
    modifier: Modifier = Modifier,
    highlight: WordKey? = null,
    blankPosition: Int? = null,
    showTranslation: Boolean = true,
) {
    val environment = LocalLearnEnvironment.current
    val colors = paperColors()
    val words by rememberAyah(ayah)
    val translation by rememberTranslation(if (showTranslation) ayah else null)
    val fill = Awn.field.selectedAyahFill
    val line =
        remember(words, highlight, blankPosition, fill) {
            buildAnnotatedString {
                words?.words?.forEachIndexed { index, word ->
                    if (index > 0) append(" ")
                    val text = if (word.key.wordPosition == blankPosition) BLANK else word.text
                    if (word.key == highlight) {
                        withStyle(SpanStyle(background = fill)) { append(text) }
                    } else {
                        append(text)
                    }
                }
            }
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AwnShapes.control)
                .background(colors.background)
                .padding(AwnSpacing.medium),
    ) {
        Text(
            text = line,
            style =
                AwnTypography.quran.copy(
                    fontFamily = environment?.arabic?.fontFamily ?: AwnTypography.quran.fontFamily,
                    textDirection = TextDirection.Rtl,
                    textAlign = TextAlign.Start,
                ),
            color = colors.content,
            modifier = Modifier.fillMaxWidth(),
        )
        translation?.let {
            Text(
                text = it,
                style = AwnTypography.body,
                color = colors.content,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
        if (environment != null) {
            Text(
                text = environment.ayahReference(ayah),
                style = AwnTypography.bodyStrong,
                color = colors.content,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
    }
}

// A dotted gap with the width of a short word, drawn in the Arabic font like the words around it.
private const val BLANK = "……"
