package app.awn.quran.feature.read.settings

import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId

/** How the Read screen shows the text: the printed page, or one block per ayah. */
enum class PageView {
    Mushaf,
    AyahList,
}

/**
 * The three text sizes the user can pick. [zoom] multiplies the Quran text token only; the
 * interface text keeps its token sizes.
 */
enum class TextSize(val zoom: Float) {
    Small(SMALL_ZOOM),
    Medium(MEDIUM_ZOOM),
    Large(LARGE_ZOOM),
}

private const val SMALL_ZOOM = 1.0f
private const val MEDIUM_ZOOM = 1.15f
private const val LARGE_ZOOM = 1.3f

/** Everything the user can change about reading. */
data class ReadSettings(
    val script: ScriptId = ScriptId.QpcHafs,
    val pageView: PageView = PageView.Mushaf,
    /** Enabled translations, in the order they are shown. */
    val translations: List<TranslationId> = DEFAULT_TRANSLATIONS,
    val showTransliteration: Boolean = true,
    val showWordByWord: Boolean = true,
    val textSize: TextSize = TextSize.Medium,
) {
    companion object {
        /** Bangla first, then English, as in the design mockups; both ship inside the app. */
        val DEFAULT_TRANSLATIONS = listOf(TranslationId("bn_zakaria"), TranslationId("en_saheeh"))
    }
}
