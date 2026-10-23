package app.awn.quran.feature.learn.words

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey

/** The words of one Quran page in reading order, and the ayahs that have a word on it. */
data class PageWordKeys(val page: Int, val ayahs: List<AyahRef>, val keys: List<WordKey>)

/**
 * Finds the words on a page of the user's print. A print whose layouts are not installed (the
 * IndoPak pages before their pack is) falls back to the default print, and a page the default print
 * does not have gives an empty page, so the screen shows no words instead of failing.
 */
class PageWordsSource(private val quranText: QuranTextRepository) {

    suspend fun wordsOnPage(page: Int, script: ScriptId): PageWordKeys {
        val ayahs = ayahsOrNull(page, script) ?: ayahsOrNull(page, DEFAULT_PRINT).orEmpty()
        val keys = ayahs.flatMap { ayah -> quranText.wordsOfAyah(ayah).map { it.key } }
        return PageWordKeys(page, ayahs, keys)
    }

    // These are what the text repository throws for a print that is not on the phone and for a
    // page number the print does not have.
    private suspend fun ayahsOrNull(page: Int, script: ScriptId): List<AyahRef>? =
        try {
            quranText.ayahsOnPage(page, script)
        } catch (missingLayouts: UnsupportedOperationException) {
            null
        } catch (outOfRange: IllegalArgumentException) {
            null
        }

    companion object {
        val DEFAULT_PRINT = ScriptId.V2
    }
}
