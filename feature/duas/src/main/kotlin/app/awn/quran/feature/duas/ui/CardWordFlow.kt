package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnReciteFills
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.AwnFieldColors
import app.awn.quran.tracker.WordState

private const val OUTSIDE_WORD_ALPHA = 0.45f
private const val HIDDEN_BLOCK_ALPHA = 0.3f

/** What is drawn on top of the words of the card that is on screen. */
internal data class CardHighlights(
    /** The word the reciter is saying now. */
    val playingWord: WordKey? = null,
    /** The recite state of each word; empty outside Recite. */
    val wordStates: Map<WordKey, WordState> = emptyMap(),
    /** Words that can be tapped to review them. */
    val flaggedWords: Set<WordKey> = emptySet(),
    /** The word the review card is about. */
    val focusedWord: WordKey? = null,
)

/** Test tag of a word; the key is the word's `surah:ayah:position`. */
internal fun cardWordTag(key: WordKey): String = "dua-word-$key"

/**
 * The Arabic of the dua's ayahs, right to left, one block per word so that each word can carry its
 * own fill behind the text. Words outside the dua are in a softer ink, or left out when
 * [showDuaOnly] is set.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardWordFlow(
    words: List<CardWord>,
    showDuaOnly: Boolean,
    highlights: CardHighlights,
    onFlaggedWordClick: (WordKey) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val shown = if (showDuaOnly) words.filter { it.isDuaWord } else words
    val lastWordOfAyah = shown.groupBy { it.word.key.ayah }.mapValues { it.value.last().word.key }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            shown.forEach { cardWord ->
                CardWordBlock(cardWord, highlights, onFlaggedWordClick, textStyle)
                if (cardWord.word.key == lastWordOfAyah[cardWord.word.key.ayah]) {
                    AyahNumber(cardWord.word.key.ayah, textStyle)
                }
            }
        }
    }
}

@Composable
private fun CardWordBlock(
    cardWord: CardWord,
    highlights: CardHighlights,
    onFlaggedWordClick: (WordKey) -> Unit,
    textStyle: TextStyle,
) {
    val field = Awn.field
    val key = cardWord.word.key
    val state = highlights.wordStates[key] ?: WordState.Neutral
    val isFlagged = key in highlights.flaggedWords
    val ring =
        if (key == highlights.focusedWord) {
            Modifier.border(2.dp, field.buttonFill, AwnShapes.wordBlock)
        } else {
            Modifier
        }
    val tap = if (isFlagged) Modifier.clickable { onFlaggedWordClick(key) } else Modifier
    Box(
        modifier =
            Modifier.testTag(cardWordTag(key))
                .clip(AwnShapes.wordBlock)
                .background(wordFill(key, state, highlights, field))
                .then(ring)
                .then(tap)
                .padding(horizontal = AwnSpacing.small / 2)
    ) {
        Text(
            text = cardWord.word.textUthmani,
            style = textStyle,
            color = wordInk(cardWord.isDuaWord, state, field),
        )
    }
}

@Composable
private fun AyahNumber(ayah: Int, textStyle: TextStyle) {
    Text(
        text = arabicIndicDigits(ayah),
        style = textStyle,
        color = Awn.field.content.copy(alpha = OUTSIDE_WORD_ALPHA),
    )
}

private fun wordInk(isDuaWord: Boolean, state: WordState, field: AwnFieldColors): Color =
    when {
        state == WordState.Hidden -> Color.Transparent
        isDuaWord -> field.content
        else -> field.content.copy(alpha = OUTSIDE_WORD_ALPHA)
    }

// The playing word is ochre on top of whatever recite state it has; the recite fills are the ones
// the Quran page uses.
private fun wordFill(
    key: WordKey,
    state: WordState,
    highlights: CardHighlights,
    field: AwnFieldColors,
): Color =
    when {
        key == highlights.playingWord -> field.playingWordFill
        state == WordState.Hidden -> field.content.copy(alpha = HIDDEN_BLOCK_ALPHA)
        else -> reciteFill(state, field.isLight)
    }

/** The fill behind a word that is being recited; transparent for a word with no mark. */
internal fun reciteFill(state: WordState, isLightField: Boolean): Color =
    when (state) {
        WordState.Confirmed ->
            if (isLightField) AwnReciteFills.dayConfirmed else AwnReciteFills.nightConfirmed
        WordState.Tentative,
        WordState.Mispronounced ->
            if (isLightField) AwnReciteFills.dayTentative else AwnReciteFills.nightTentative
        WordState.Wrong ->
            if (isLightField) AwnReciteFills.dayWrong else AwnReciteFills.nightWrong
        WordState.Neutral,
        WordState.Hidden -> Color.Transparent
    }

/** The digits of [number] as Arabic-Indic digits, which the Hafs font draws for ayah numbers. */
internal fun arabicIndicDigits(number: Int): String =
    number.toString().map { digit -> ARABIC_INDIC_ZERO + (digit - '0') }.joinToString(separator = "")

private const val ARABIC_INDIC_ZERO = '\u0660'
