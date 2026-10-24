package app.awn.quran.core.quranfont

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnTypography

private val WORD_GAP = 4.dp

/**
 * The ayah [ayah], or only its words whose position (counted from 1) is in [wordRange], drawn word
 * by word in the user's Mushaf print as the Mushaf's line mode draws it: right to left, wrapping
 * onto more lines, each word in the font of its own page. [showEndMarker] adds the ayah-end sign
 * after the last word when it is shown. [maxLines] keeps whole words only, so one line ends at a
 * word.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuranAyahText(
    ayah: AyahRef,
    modifier: Modifier = Modifier,
    style: TextStyle = AwnTypography.quran,
    color: Color = Awn.field.content,
    wordRange: IntRange? = null,
    showEndMarker: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    val print = LocalQuranPrint.current
    val printedWords by
        produceState(emptyList(), print, ayah, wordRange, showEndMarker) {
            value = printedWordsOf(print, ayah, wordRange, showEndMarker)
        }
    // The words are placed from the right, as an Arabic line is read.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(WORD_GAP),
            maxLines = maxLines,
        ) {
            printedWords.forEach { printedWord ->
                PrintedWordCell(printedWord, print, style, color)
            }
        }
    }
}

/** One word, drawn in the user's Mushaf print with the font of its own page. */
@Composable
fun QuranWordText(
    key: WordKey,
    modifier: Modifier = Modifier,
    style: TextStyle = AwnTypography.quran,
    color: Color = Awn.field.content,
) {
    val print = LocalQuranPrint.current
    val printedWord by
        produceState<PrintedWord?>(null, print, key) {
            val ayah = AyahRef(key.surah, key.ayah)
            val position = key.wordPosition
            value =
                printedWordsOf(print, ayah, position..position, showEndMarker = false).firstOrNull()
        }
    // The box is there before the word is, so the modifier's size and tap target do not jump.
    Box(modifier) { printedWord?.let { PrintedWordCell(it, print, style, color) } }
}

@Composable
private fun PrintedWordCell(
    printedWord: PrintedWord,
    print: QuranPrint,
    style: TextStyle,
    color: Color,
) {
    val fontCache = print.fontCache
    if (fontCache == null) {
        Text(
            text = textToDraw(printedWord.word, ScriptId.QpcHafs, hasGlyphFont = false),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    } else {
        PageFontWord(
            word = printedWord.word,
            script = printedWord.script,
            page = printedWord.page,
            fontCache = fontCache,
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = color,
        )
    }
}
