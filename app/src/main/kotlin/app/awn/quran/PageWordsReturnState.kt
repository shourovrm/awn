package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import app.awn.quran.tabs.AwnTab

/** Where Back from the page words leads; it survives rotation and is forgotten on restart. */
@Composable
internal fun rememberPageWordsReturn(): MutableState<PageWordsReturn?> =
    rememberSaveable(stateSaver = PageWordsReturnSaver) { mutableStateOf(null) }

private val PageWordsReturnSaver: Saver<PageWordsReturn?, Any> =
    listSaver(
        save = { destination -> listOfNotNull(destination?.quranPage, destination?.tab?.name) },
        restore = { saved ->
            if (saved.size == 2) {
                PageWordsReturn(saved[0] as Int, AwnTab.valueOf(saved[1] as String))
            } else {
                null
            }
        },
    )
