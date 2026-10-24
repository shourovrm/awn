package app.awn.quran.feature.read.quran

import androidx.compose.ui.geometry.Rect
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.MyWordMark
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.tracker.WordState

/**
 * What the words of one ayah need from the screen that shows them. The Mushaf line view builds it
 * from its [ListPageEnvironment]; the dua cards build their own, so both draw words the same way.
 *
 * [dimmedWords] are drawn faint: on a dua card they are the words of the ayah that are not part of
 * the dua. [onWordTap] gets the word's bounds in the window, or null when they are not known yet.
 *
 * [tappableWords] take a tap even without the word-by-word layer, as a word recited badly does; the
 * dua cards add the words whose mistake can be reviewed. [wordTag] gives a word its test tag, or
 * null for none.
 */
@Suppress("LongParameterList") // Plain data and callbacks handed from a screen to its words.
class WordFlowInput(
    val settings: ReadSettings,
    val fontCache: PageFontCache,
    val wordStates: Map<WordKey, WordState> = emptyMap(),
    val myWordMarks: Map<WordKey, MyWordMark> = emptyMap(),
    val hintLetters: Map<WordKey, String> = emptyMap(),
    val focusedWord: WordKey? = null,
    val dimmedWords: Set<WordKey> = emptySet(),
    val tappableWords: Set<WordKey> = emptySet(),
    val wordTag: (WordKey) -> String? = { null },
    val onWordTap: (AyahRef, AyahWord, Rect?) -> Unit,
)
