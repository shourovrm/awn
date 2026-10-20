package app.awn.quran.feature.recite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.tracker.WordState

private const val TENTATIVE_ALPHA = 0.6f
// Where the dot and stem of the "i" sit inside the 24 dp glyph, as fractions of its height.
private const val DOT_HEIGHT_FRACTION = 0.3f
private const val STEM_TOP_FRACTION = 0.45f
private const val STEM_BOTTOM_FRACTION = 0.75f
private const val MIN_DASH_LETTERS = 2
private const val MAX_DASH_LETTERS = 6

/** The test tag of the word with [key]; tests read the state description from it. */
fun reciteWordTag(key: WordKey): String = "word-$key"

/** The test tag of the icon button that opens the review of [ayah]. */
fun reviewLabelTag(ayah: Int): String = "review-$ayah"

/**
 * The words of the range, one right-to-left block per ayah with its ochre number after the last
 * word. Flagged words carry a dotted underline and open the review when tapped, and an icon button
 * after each ayah with findings opens the first one; tapping a flagged word calls
 * [onFlaggedWordClick].
 */
@Composable
fun ReciteWords(
    words: List<Word>,
    wordStates: Map<WordKey, WordState>,
    skippedWords: Set<WordKey>,
    hint: ReciteHint?,
    reviews: Map<Int, List<WordReview>>,
    onFlaggedWordClick: (WordKey) -> Unit,
    onReviewClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        words
            .groupBy { it.key.ayah }
            .forEach { (ayah, ayahWords) ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FlowRow(Modifier.fillMaxWidth()) {
                        ayahWords.forEach { word ->
                            val isFlagged = reviews[ayah].orEmpty().any { it.key == word.key }
                            ReciteWord(
                                word = word,
                                state = wordStates[word.key] ?: WordState.Neutral,
                                isSkipped = word.key in skippedWords,
                                hintLetter = hint?.takeIf { it.key == word.key }?.letter,
                                onFlaggedClick =
                                    { onFlaggedWordClick(word.key) }.takeIf { isFlagged },
                            )
                        }
                        AyahNumber(ayah)
                        if (!reviews[ayah].isNullOrEmpty())
                            ReviewButton(ayah) { onReviewClick(ayah) }
                    }
                }
            }
    }
}

@Composable
private fun ReciteWord(
    word: Word,
    state: WordState,
    isSkipped: Boolean,
    hintLetter: String?,
    onFlaggedClick: (() -> Unit)?,
) {
    val field = Awn.field
    val description = stateDescriptionFor(state, isSkipped)
    val tagged = Modifier.testTag(reciteWordTag(word.key))
    val described =
        if (description == null) tagged else tagged.semantics { stateDescription = description }

    if (state == WordState.Hidden && !isSkipped) {
        // The word text is not drawn at all, so a screen reader cannot read out a hidden word.
        HiddenWord(
            letterCount = word.textUthmani.count { Character.isLetter(it) },
            hintLetter = hintLetter,
            modifier = described,
        )
        return
    }
    // Skipped (never heard) is red; a word said with a mistake is ochre, like a tentative one but
    // at full strength.
    val color =
        when {
            isSkipped -> field.wrongWord
            state == WordState.Confirmed -> field.confirmedWord
            state == WordState.Wrong -> field.tentativeWord
            state == WordState.Tentative -> field.tentativeWord.copy(alpha = TENTATIVE_ALPHA)
            else -> field.content
        }
    val clickable =
        if (onFlaggedClick == null) {
            described
        } else {
            described.dottedUnderline(color).clickable(onClick = onFlaggedClick)
        }
    Text(
        text = word.textUthmani,
        style = AwnTypography.quran,
        color = color,
        modifier = clickable.padding(horizontal = AwnSpacing.small / 2),
    )
}

/**
 * A 2 dp dotted line under the word, in the word's own colour, so a flagged word reads as tappable.
 */
private fun Modifier.dottedUnderline(color: Color): Modifier = drawBehind {
    val thickness = 2.dp.toPx()
    val y = size.height - thickness
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = thickness,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(thickness, thickness * 2)),
    )
}

/** An icon-only circled "i" at the end of an ayah that has findings; it opens the first one. */
@Composable
private fun ReviewButton(ayah: Int, onClick: () -> Unit) {
    val label = stringResource(R.string.recite_review)
    Box(
        modifier =
            Modifier.testTag(reviewLabelTag(ayah))
                .size(AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(AwnSpacing.large)) {
            val stroke = 2.dp.toPx()
            drawCircle(AwnColors.ochre, style = Stroke(width = stroke))
            drawCircle(
                AwnColors.ochre,
                radius = stroke,
                center = Offset(size.width / 2, size.height * DOT_HEIGHT_FRACTION),
            )
            drawLine(
                color = AwnColors.ochre,
                start = Offset(size.width / 2, size.height * STEM_TOP_FRACTION),
                end = Offset(size.width / 2, size.height * STEM_BOTTOM_FRACTION),
                strokeWidth = stroke,
            )
        }
    }
}

@Composable
private fun stateDescriptionFor(state: WordState, isSkipped: Boolean): String? =
    if (isSkipped) stringResource(R.string.recite_verdict_skipped)
    else
        when (state) {
            WordState.Confirmed -> stringResource(R.string.recite_word_confirmed)
            WordState.Tentative -> stringResource(R.string.recite_word_tentative)
            WordState.Wrong -> stringResource(R.string.recite_word_wrong)
            WordState.Hidden -> stringResource(R.string.recite_word_hidden)
            WordState.Neutral -> null
        }

/** A short dash in the paper block colour, optionally led by the first letter as a hint. */
@Composable
private fun HiddenWord(letterCount: Int, hintLetter: String?, modifier: Modifier) {
    val field = Awn.field
    val dashWidth = AwnSpacing.small * letterCount.coerceIn(MIN_DASH_LETTERS, MAX_DASH_LETTERS)
    Row(
        modifier = modifier.height(AwnSpacing.huge).padding(horizontal = AwnSpacing.small / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hintLetter != null) {
            Text(
                text = hintLetter,
                style = AwnTypography.quran,
                color = field.tentativeWord,
                modifier = Modifier.padding(end = AwnSpacing.small / 2),
            )
        }
        Box(
            Modifier.width(dashWidth)
                .height(AwnSpacing.small / 2)
                .clip(AwnShapes.wordBlock)
                .background(field.hiddenWordBlock),
        )
    }
}

@Composable
private fun AyahNumber(ayah: Int) {
    Box(Modifier.height(AwnSpacing.huge).padding(horizontal = AwnSpacing.small), Alignment.Center) {
        Text(text = ayah.toString(), style = AwnTypography.small, color = AwnColors.ochre)
    }
}
