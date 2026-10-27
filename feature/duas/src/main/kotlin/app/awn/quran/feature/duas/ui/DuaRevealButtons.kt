package app.awn.quran.feature.duas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.feature.duas.recite.DuaRecitation
import app.awn.quran.feature.recite.R as ReciteR
import app.awn.quran.feature.recite.ReciteHint

internal const val DUA_RECITE_REVEAL_WORD_TAG = "dua-recite-reveal-word"
internal const val DUA_RECITE_REVEAL_AYAH_TAG = "dua-recite-reveal-ayah"

/**
 * What the reveal buttons of the Dua recite bar show and do, read from the recitation.
 * [nextTapShowsLetter] is the label of the first button: it says "Show first letter" while a tap
 * would show only the letter, and "Show next word" once the letter of the current word is shown.
 */
internal class DuaRevealState(
    val showsFirstLetterFirst: Boolean,
    val nextTapShowsLetter: Boolean,
)

/**
 * The reveal state from the saved setting and the hint on screen. The letter counts as shown only
 * when the hint belongs to the word the user is on; a hint on another word does not stop the
 * letter-first step for this one.
 */
internal fun duaRevealStateOf(
    showsFirstLetterFirst: Boolean,
    hint: ReciteHint?,
    currentWord: WordKey?,
): DuaRevealState {
    val letterIsShown = hint != null && hint.key == currentWord
    return DuaRevealState(
        showsFirstLetterFirst = showsFirstLetterFirst,
        nextTapShowsLetter = showsFirstLetterFirst && !letterIsShown,
    )
}

/** The letter drawn on a hidden word: the page draws it inside that word's box. */
internal fun hintLettersOf(hint: ReciteHint?): Map<WordKey, String> =
    if (hint == null) emptyMap() else mapOf(hint.key to hint.letter)

/** The reveal state of [recitation], updated as the user taps and recites. */
@Composable
internal fun rememberDuaRevealState(recitation: DuaRecitation): DuaRevealState {
    val showsFirstLetterFirst by recitation.hintEnabled.collectAsState(initial = true)
    val hint by recitation.hint.collectAsState()
    val currentWord by recitation.currentWord.collectAsState()
    return duaRevealStateOf(showsFirstLetterFirst, hint, currentWord)
}

/**
 * The two reveal buttons of the Dua recite bar, the same pair as the Quran recite bar: the next
 * word (or its first letter first) and the rest of the ayah. The caller shows them only while the
 * words are hidden.
 */
@Composable
internal fun DuaRevealButtons(
    state: DuaRevealState,
    onRevealNextWord: () -> Unit,
    onRevealAyah: () -> Unit,
) {
    val field = Awn.field
    AwnIconButton(
        icon = ReciteR.drawable.ic_recite_reveal_word,
        contentDescription =
            stringResource(
                if (state.nextTapShowsLetter) ReciteR.string.recite_reveal_letter
                else ReciteR.string.recite_reveal_word,
            ),
        onClick = onRevealNextWord,
        tint = field.content,
        modifier = Modifier.testTag(DUA_RECITE_REVEAL_WORD_TAG),
    )
    AwnIconButton(
        icon = ReciteR.drawable.ic_recite_reveal_ayah,
        contentDescription = stringResource(ReciteR.string.recite_reveal_ayah),
        onClick = onRevealAyah,
        tint = field.content,
        modifier = Modifier.testTag(DUA_RECITE_REVEAL_AYAH_TAG),
    )
}
