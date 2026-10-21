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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R

/**
 * The Arabic line height in this view, a little over the glyphs so the meaning sits close under.
 */
private const val WORD_LINE_HEIGHT_FACTOR = 1.6f

/** A meaning wraps to at most this many lines, so a long gloss cannot push the next row away. */
private const val MEANING_MAX_LINES = 2

/**
 * The words right to left and wrapping, each with its meaning under it. Cells are aligned to the
 * top, so every Arabic word of a row shares one baseline and the meanings start on one line, as on
 * Quran.com. With the Arabic layer hidden a cell holds the meaning alone.
 */
@Suppress("LongMethod") // One flow row with the cell of each word.
@Composable
internal fun WordsFlow(
    block: AyahBlock,
    environment: ListPageEnvironment,
    arabicStyle: TextStyle,
    fills: ReaderFills,
    playingWord: Int?,
    onAyahLongPress: () -> Unit,
) {
    val settings = environment.settings
    val wordArabicStyle =
        arabicStyle.copy(lineHeight = arabicStyle.fontSize * WORD_LINE_HEIGHT_FACTOR)
    val meaningStyle =
        scaled(AwnTypography.caption, settings.wordByWordScale).copy(textAlign = TextAlign.Center)
    val meaningLabel = stringResource(R.string.word_meaning_action)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            itemVerticalAlignment = Alignment.Top,
        ) {
            block.words.forEach { word ->
                val isPlayingWord = playingWord == word.key.wordPosition
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier.heightIn(min = AwnSpacing.touchTarget)
                            .clip(AwnShapes.wordBlock)
                            .semantics(mergeDescendants = true) {
                                onClick(label = meaningLabel) {
                                    environment.onWordTap(block.ayah, word)
                                    true
                                }
                            }
                            .background(if (isPlayingWord) fills.playingWord else Color.Transparent)
                            .pointerInput(word.key) {
                                detectTapGestures(
                                    onTap = { environment.onWordTap(block.ayah, word) },
                                    onLongPress = { onAyahLongPress() },
                                )
                            },
                ) {
                    if (settings.showArabic) Text(text = word.arabic, style = wordArabicStyle)
                    if (word.meaning != null) {
                        Text(
                            text = word.meaning,
                            style = meaningStyle,
                            maxLines = MEANING_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = AwnSpacing.giant + AwnSpacing.large),
                        )
                    }
                }
            }
        }
    }
}
