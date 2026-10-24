package app.awn.quran.core.quranfont

import androidx.compose.runtime.staticCompositionLocalOf
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId

/**
 * The Mushaf print the user chose, as every screen that shows Quran text needs it: the [script],
 * the [fontCache] that holds the page fonts, and the [quranText] that tells which words an ayah has
 * and which page prints each of them. Day or night colours come from the theme, not from here.
 *
 * Without a [fontCache] the words are drawn in the bundled Hafs font. Without [quranText] there is
 * nothing to look words up in, so nothing is drawn.
 */
class QuranPrint(
    val script: ScriptId = ScriptId.QpcHafs,
    val fontCache: PageFontCache? = null,
    val quranText: QuranTextRepository? = null,
)

/**
 * The print of the app, provided once at the root. Without a provider (a test, a preview) it is the
 * Unicode Hafs print in the bundled font.
 */
val LocalQuranPrint = staticCompositionLocalOf { QuranPrint() }
