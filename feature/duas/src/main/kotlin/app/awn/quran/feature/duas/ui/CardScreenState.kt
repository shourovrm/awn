package app.awn.quran.feature.duas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.quranfont.AyahWord

/**
 * The choices that belong to the card screen as a whole; all but [reviewedWord] survive rotation.
 */
internal class CardScreenState(
    mode: DuaCardMode,
    showDuaOnly: Boolean,
    isHidingWords: Boolean,
) {
    var mode by mutableStateOf(mode)
    var showDuaOnly by mutableStateOf(showDuaOnly)
    var isHidingWords by mutableStateOf(isHidingWords)

    /** The word the review card is about. */
    var reviewedWord by mutableStateOf<WordKey?>(null)

    /** The word whose card is open, or null when none is. */
    var wordCard by mutableStateOf<WordCardTarget?>(null)
}

/** The tapped word and where it lies in the window, so its card can sit beside it. */
internal class WordCardTarget(val ayah: AyahRef, val word: AyahWord, val anchor: Rect?)

private val CardScreenStateSaver =
    listSaver<CardScreenState, Any>(
        save = { listOf(it.mode.name, it.showDuaOnly, it.isHidingWords) },
        restore = {
            CardScreenState(
                mode = modeFromStoredName(it[0] as String),
                showDuaOnly = it[1] as Boolean,
                isHidingWords = it[2] as Boolean,
            )
        },
    )

@Composable
internal fun rememberCardScreenState(): CardScreenState =
    rememberSaveable(saver = CardScreenStateSaver) {
        CardScreenState(DuaCardMode.Listen, showDuaOnly = false, isHidingWords = false)
    }
