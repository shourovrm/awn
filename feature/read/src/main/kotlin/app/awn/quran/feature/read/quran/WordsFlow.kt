package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.page.PageFontWord
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
internal fun WordsFlow(
    block: AyahBlock,
    environment: ListPageEnvironment,
    arabicStyle: TextStyle,
    fills: ReaderFills,
    playingWord: Int?,
    onAyahLongPress: () -> Unit,
) {
    val hasMeanings = environment.settings.showWordByWord
    val cellStyles = wordCellStyles(environment.settings, arabicStyle)
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
                    environment = environment,
                    styles = cellStyles,
                    fill = wordFill(environment.wordStates[word.key], playingWord, word, fills),
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

/** A recite mark wins over the playing-word fill, so a word being checked shows its result. */
@Composable
private fun wordFill(
    state: WordState?,
    playingWord: Int?,
    word: AyahWord,
    fills: ReaderFills,
): Color {
    val markFill = wordCellFill(state ?: WordState.Neutral, Awn.field)
    return when {
        markFill != null -> markFill
        playingWord == word.key.wordPosition -> fills.playingWord
        else -> Color.Transparent
    }
}

/** One word: its Arabic and, with the word-by-word layer, its meaning under it. */
@Composable
private fun WordCell(
    ayah: AyahRef,
    word: AyahWord,
    environment: ListPageEnvironment,
    styles: WordCellStyles,
    fill: Color,
    onAyahLongPress: () -> Unit,
) {
    val settings = environment.settings
    val state = environment.wordStates[word.key] ?: WordState.Neutral
    val coordinatesHolder = remember { CoordinatesHolder() }
    val tapped = {
        environment.onWordTap(ayah, word, coordinatesHolder.coordinates?.boundsInWindow())
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
            Modifier.plainCellTaps(state, word, tapped, onAyahLongPress)
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            tapModifier.clip(AwnShapes.wordBlock).background(fill).onGloballyPositioned {
                coordinatesHolder.coordinates = it
            },
    ) {
        if (settings.showArabic) {
            ArabicWord(word, styles.arabic, state, environment)
        }
        if (settings.showWordByWord && word.meaning != null) {
            Text(
                text = word.meaning,
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
    state: WordState,
    word: AyahWord,
    onTap: () -> Unit,
    onAyahLongPress: () -> Unit,
): Modifier {
    val isFlagged = state == WordState.Wrong || state == WordState.Tentative
    return if (isFlagged) wordTaps(word, onTap, onAyahLongPress) else this
}

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
    environment: ListPageEnvironment,
) {
    val field = Awn.field
    val ink = wordColor(state, field)
    val glyphs = word.glyphs
    if (glyphs == null) {
        Text(text = word.arabic, style = style, color = ink)
    } else {
        PageFontWord(
            word = glyphs.word,
            script = environment.settings.script,
            page = glyphs.page,
            fontCache = environment.fontCache,
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = ink,
            isNight = !field.isLight,
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
