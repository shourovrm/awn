package app.awn.quran.feature.learn.ui

import androidx.compose.runtime.compositionLocalOf
import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.learn.words.PageWordsSource

/**
 * Where the Learn tab meets the Quran and Hifz tabs. [lastReadPage] is the page the Quran tab
 * continues from and [hifzTodayPage] is the page of today's Hifz lesson; each is null when the user
 * has none. [script] is the print the user reads, which decides what is on a page, and [onOpenPage]
 * shows a page in the Quran screen.
 */
class LearnPageLinks(
    val lastReadPage: Int? = null,
    val hifzTodayPage: Int? = null,
    val script: ScriptId = PageWordsSource.DEFAULT_PRINT,
    val onOpenPage: ((Int) -> Unit)? = null,
)

/** The page links of the screen on show; none until :app hands them to the Learn tab. */
val LocalLearnPageLinks = compositionLocalOf { LearnPageLinks() }
