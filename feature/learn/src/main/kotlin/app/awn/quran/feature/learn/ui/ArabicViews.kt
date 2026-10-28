package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.quranfont.GlyphRun
import app.awn.quran.core.quranfont.LocalQuranPrint
import app.awn.quran.core.quranfont.LocalScatteredFontCache
import app.awn.quran.core.quranfont.QuranAyahText
import app.awn.quran.core.quranfont.QuranWordText
import app.awn.quran.core.quranfont.glyphRunsOfKeys
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
    arabicSize: TextUnit = arabicSizeFor(style),
) {
    val environment = LocalLearnEnvironment.current
    val keys = remember(text, highlight) { LessonMarkup.wordKeys(text) }
    val texts = rememberWordTexts(keys)
    val print = LocalQuranPrint.current
    val fontCache = LocalScatteredFontCache.current
    val isNight = !Awn.field.isLight
    // Lesson words are drawn in the user's glyph print once their page fonts are loaded.
    val glyphRuns by
        produceState(emptyMap<WordKey, GlyphRun>(), keys, print, fontCache, isNight) {
            value =
                if (fontCache == null || keys.isEmpty()) {
                    emptyMap()
                } else {
                    glyphRunsOfKeys(keys, print, fontCache, isNight)
                }
        }
    val fill = Awn.field.selectedAyahFill
    val annotated =
        remember(text, texts, highlight, environment, fill, arabicSize, glyphRuns) {
            LessonMarkup.build(
                text = text,
                wordTexts = texts,
                arabicFont = environment?.arabic?.fontFamily,
                arabicSize = arabicSize,
                highlight = highlight,
                highlightStyle = SpanStyle(background = fill),
                glyphRuns = glyphRuns,
            )
        }
    Text(
        text = annotated,
        style = style.copy(textAlign = textAlign),
        color = color,
        modifier = modifier,
    )
}

private fun arabicSizeFor(style: TextStyle): TextUnit {
    val leadSize = AwnTypography.lead.fontSize
    return if (style.fontSize > leadSize) style.fontSize else leadSize
}

/** One Quran word at the size of [style], centred: the word of a question or a new word. */
@Composable
internal fun ArabicWord(
    key: WordKey,
    modifier: Modifier = Modifier,
    style: TextStyle = AwnTypography.title,
    color: Color = Awn.field.content,
) {
    // The Quran style has the tall line height that keeps the marks above and below the letters.
    val wordStyle = AwnTypography.quran.copy(fontSize = style.fontSize)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        QuranWordText(key = key, style = wordStyle, color = color)
    }
}

/**
 * An ayah on a paper card: its words right to left, [highlight] and every word of [highlights]
 * marked, the word at [blankPosition] replaced by a gap, and the translation and reference under it
 * when [showTranslation] is set.
 */
@Composable
internal fun AyahCard(
    ayah: AyahRef,
    modifier: Modifier = Modifier,
    highlight: WordKey? = null,
    highlights: Set<WordKey> = emptySet(),
    blankPosition: Int? = null,
    showTranslation: Boolean = true,
) {
    val environment = LocalLearnEnvironment.current
    val colors = paperColors()
    val translation by rememberTranslation(if (showTranslation) ayah else null)
    val highlightedPositions =
        remember(ayah, highlight, highlights) {
            (highlights + listOfNotNull(highlight))
                .filter { key -> key.surah == ayah.surah && key.ayah == ayah.ayah }
                .mapTo(mutableSetOf()) { key -> key.wordPosition }
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AwnShapes.control)
                .background(colors.background)
                .padding(AwnSpacing.medium),
    ) {
        QuranAyahText(
            ayah = ayah,
            modifier = Modifier.fillMaxWidth(),
            color = colors.content,
            showEndMarker = false,
            highlightedPositions = highlightedPositions,
            highlightColor = colors.playingWordFill,
            blankPosition = blankPosition,
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
