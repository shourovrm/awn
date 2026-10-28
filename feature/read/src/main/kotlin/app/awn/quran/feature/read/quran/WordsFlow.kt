package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.MyWordMark
import app.awn.quran.core.quranfont.AyahWord
import app.awn.quran.core.quranfont.PageFontWord
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.page.myWordFillColor
import app.awn.quran.feature.read.page.wordCellFill
import app.awn.quran.feature.read.page.wordColor
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.tracker.WordState

/**
 * The Arabic line height in this view, a little over the glyphs so the meaning sits close under.
 */
private const val WORD_LINE_HEIGHT_FACTOR = 1.6f

/** The space between plain Arabic words, about one space of the Quran font. */
private val PLAIN_WORD_GAP = 4.dp

/** How see-through a word outside the dua is, so colour fonts dim as plain ink does. */
private const val DIMMED_WORD_ALPHA = 0.6f

/** A meaning wraps to at most this many lines, so a long gloss cannot push the next row away. */
private const val MEANING_MAX_LINES = 2

/**
 * The words right to left and wrapping. With the word-by-word layer each has its meaning under it
 * and can be tapped; cells are aligned to the top, so every Arabic word of a row shares one
 * baseline and the meanings start on one line, as on Quran.com. Without it the cells are plain
 * Arabic words that wrap like a paragraph, used when each word needs its own page font or its own
 * recite fill; taps and long presses then belong to the whole block. With the Arabic layer hidden a
 * cell holds the meaning alone.
 */
@Composable
fun WordsFlow(
    block: AyahBlock,
    input: WordFlowInput,
    arabicStyle: TextStyle,
    fills: ReaderFills,
    playingWord: Int?,
    onAyahLongPress: () -> Unit,
) {
    val hasMeanings = input.settings.showWordByWord
    val cellStyles = wordCellStyles(input.settings, arabicStyle)
    val wordGap = if (hasMeanings) AwnSpacing.small else PLAIN_WORD_GAP
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(wordGap),
            verticalArrangement = Arrangement.spacedBy(if (hasMeanings) AwnSpacing.small else 0.dp),
            itemVerticalAlignment = Alignment.Top,
        ) {
            block.words.forEach { word ->
                WordCell(
                    ayah = block.ayah,
                    word = word,
                    input = input,
                    styles = cellStyles,
                    fill =
                        wordFill(
                            input.wordStates[word.key],
                            input.myWordMarks[word.key],
                            playingWord,
                            word,
                            fills,
                        ),
                    isFocused = word.key == input.focusedWord,
                    onAyahLongPress = onAyahLongPress,
                )
            }
        }
    }
}

/** The text styles every cell of one ayah shares. */
private class WordCellStyles(val arabic: TextStyle, val meaning: TextStyle)

@Composable
private fun wordCellStyles(settings: ReadSettings, arabicStyle: TextStyle): WordCellStyles {
    val arabic =
        if (settings.showWordByWord) {
            arabicStyle.copy(lineHeight = arabicStyle.fontSize * WORD_LINE_HEIGHT_FACTOR)
        } else {
            arabicStyle
        }
    val meaning =
        scaled(AwnTypography.caption, settings.wordByWordScale)
            .copy(textAlign = TextAlign.Center)
            .ltr()
    return WordCellStyles(arabic, meaning)
}

/**
 * A recite mark wins over the playing-word fill, so a word being checked shows its result; the fill
 * of "Show my words" is the quietest and gives way to both.
 */
@Composable
private fun wordFill(
    state: WordState?,
    myWordMark: MyWordMark?,
    playingWord: Int?,
    word: AyahWord,
    fills: ReaderFills,
): Color {
    val reciteFill = wordCellFill(state ?: WordState.Neutral, Awn.field)
    val myWordFill = myWordMark?.let { myWordFillColor(it, Awn.field) }
    return when {
        reciteFill != null -> reciteFill
        playingWord == word.key.wordPosition -> fills.playingWord
        myWordFill != null -> myWordFill
        else -> Color.Transparent
    }
}

/** One word: its Arabic and, with the word-by-word layer, its meaning under it. */
@Composable
private fun WordCell(
    ayah: AyahRef,
    word: AyahWord,
    input: WordFlowInput,
    styles: WordCellStyles,
    fill: Color,
    isFocused: Boolean,
    onAyahLongPress: () -> Unit,
) {
    val settings = input.settings
    val state = input.wordStates[word.key] ?: WordState.Neutral
    val coordinatesHolder = remember { CoordinatesHolder() }
    val tapped = {
        input.onWordTap(ayah, word, coordinatesHolder.coordinates?.boundsInWindow())
    }
    val tapModifier =
        if (settings.showWordByWord) {
            Modifier.meaningCellTaps(
                stringResource(R.string.word_meaning_action),
                word,
                tapped,
                onAyahLongPress,
            )
        } else {
            Modifier.plainCellTaps(
                isTappable = state.isRecitedBadly || word.key in input.tappableWords,
                word = word,
                onTap = tapped,
                onAyahLongPress = onAyahLongPress,
            )
        }
    val tag = input.wordTag(word.key)
    val taggedModifier = if (tag == null) tapModifier else tapModifier.testTag(tag)
    // The word under review gets a fill and a ring; the ink keeps its colour.
    val focusModifier =
        if (isFocused) {
            Modifier.background(Awn.field.selectedAyahFill, AwnShapes.wordBlock)
                .border(2.dp, Awn.field.buttonFill, AwnShapes.wordBlock)
        } else {
            Modifier
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            taggedModifier
                // Not clip(): tall page-font marks (the dhamma over an Alif) rise above the line
                // box.
                .background(fill, AwnShapes.wordBlock)
                .then(focusModifier)
                .onGloballyPositioned { coordinatesHolder.coordinates = it },
    ) {
        if (settings.showArabic) {
            ArabicWord(word, styles.arabic, state, input)
        }
        val meaning = word.meaning
        if (settings.showWordByWord && meaning != null) {
            Text(
                text = meaning,
                style = styles.meaning,
                maxLines = MEANING_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = AwnSpacing.giant + AwnSpacing.large),
            )
        }
    }
}

/** A word-by-word cell is a touch target of its own: a tap opens the word card. */
private fun Modifier.meaningCellTaps(
    meaningLabel: String,
    word: AyahWord,
    onTap: () -> Unit,
    onAyahLongPress: () -> Unit,
): Modifier =
    heightIn(min = AwnSpacing.touchTarget)
        .semantics(mergeDescendants = true) {
            onClick(label = meaningLabel) {
                onTap()
                true
            }
        }
        .wordTaps(word, onTap, onAyahLongPress)

/**
 * Plain cells belong to the whole ayah, except a word recited badly: its tap opens the card that
 * says what went wrong.
 */
private fun Modifier.plainCellTaps(
    isTappable: Boolean,
    word: AyahWord,
    onTap: () -> Unit,
    onAyahLongPress: () -> Unit,
): Modifier = if (isTappable) wordTaps(word, onTap, onAyahLongPress) else this

private val WordState.isRecitedBadly: Boolean
    get() = this == WordState.Wrong || this == WordState.Mispronounced

private fun Modifier.wordTaps(
    word: AyahWord,
    onTap: () -> Unit,
    onAyahLongPress: () -> Unit,
): Modifier =
    pointerInput(word.key) {
        detectTapGestures(onTap = { onTap() }, onLongPress = { onAyahLongPress() })
    }

/**
 * The Arabic of one word: in its own page font when it has [AyahWord.glyphs] (so tajweed colours
 * show), else as Unicode text. A Hidden word keeps its space but draws no ink; its block is the
 * cell fill.
 */
@Composable
private fun ArabicWord(
    word: AyahWord,
    style: TextStyle,
    state: WordState,
    input: WordFlowInput,
) {
    val field = Awn.field
    // A recite state keeps its own ink, so a dimmed word still shows how it was recited.
    val isDimmed = word.key in input.dimmedWords && state == WordState.Neutral
    val ink = if (isDimmed) field.mutedContent else wordColor(state, field)
    val hintLetter = input.hintLetters[word.key].takeIf { state == WordState.Hidden }
    // The word is still laid out, invisible, so its block keeps its size under the letter.
    // Colour (tajweed) glyph fonts ignore the ink, so a dimmed glyph word is made see-through.
    val dimModifier =
        if (isDimmed && word.glyphs != null) Modifier.alpha(DIMMED_WORD_ALPHA) else Modifier
    Box(contentAlignment = Alignment.Center, modifier = dimModifier) {
        ArabicWordInk(word, style, ink, input)
        if (hintLetter != null) Text(text = hintLetter, style = style, color = field.content)
    }
}

@Composable
private fun ArabicWordInk(
    word: AyahWord,
    style: TextStyle,
    ink: Color,
    input: WordFlowInput,
) {
    val glyphs = word.glyphs
    if (glyphs == null) {
        Text(text = word.arabic, style = style, color = ink)
    } else {
        PageFontWord(
            word = glyphs.word,
            script = input.settings.script,
            page = glyphs.page,
            fontCache = input.fontCache,
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = ink,
            isNight = !Awn.field.isLight,
        )
    }
}

/**
 * True when the Arabic is drawn word by word: for the word-by-word layer, for a glyph print (each
 * word has its own page font) and while reciting (each word has its own fill). Otherwise the Arabic
 * is one paragraph, which is cheaper to lay out.
 */
internal fun usesWordCells(
    block: AyahBlock,
    settings: ReadSettings,
    environment: ListPageEnvironment,
): Boolean =
    settings.showWordByWord ||
        (settings.showArabic &&
            (environment.isReciting || block.words.any { word -> word.glyphs != null }))
