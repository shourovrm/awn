package app.awn.quran.feature.duas.ui

import app.awn.quran.core.data.WordKey
import app.awn.quran.tracker.WordState

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
