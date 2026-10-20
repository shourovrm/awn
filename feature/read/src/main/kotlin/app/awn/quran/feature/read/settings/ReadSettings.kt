package app.awn.quran.feature.read.settings

import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId

/** The three views of the same page, cycled by the view button of the Quran screen. */
enum class PageView {
    Mushaf,
    Translation,
    WordByWord;

    /** The view the view button shows next. */
    fun next(): PageView = entries[(ordinal + 1) % entries.size]
}

/** Everything the user can change about reading. */
data class ReadSettings(
    /** The printed Mushaf; the 1421 print unless the user picks another page type. */
    val script: ScriptId = ScriptId.V2,
    val pageView: PageView = PageView.Mushaf,
    /** Enabled translations, in the order they are shown. */
    val translations: List<TranslationId> = DEFAULT_TRANSLATIONS,
    val showTransliteration: Boolean = true,
    val showWordByWord: Boolean = true,
    /** Step 0 to 7 of [TextScale]; the text-size popup moves this and [translationSizeStep]. */
    val arabicSizeStep: Int = TextScale.DEFAULT_STEP,
    val translationSizeStep: Int = TextScale.DEFAULT_STEP,
    /** Fills the ayahs the user saved, in the page and in the other two views. */
    val highlightBookmarks: Boolean = true,
    /** Volume up and down turn the page while the Quran screen is open. */
    val volumeKeysTurnPages: Boolean = false,
    /** The surah, juz and page number around the Mushaf page. */
    val showPageInfo: Boolean = true,
) {
    /** The factor that multiplies the Arabic font size. */
    val arabicScale: Float
        get() = TextScale.scaleOf(arabicSizeStep)

    /** The factor that multiplies the translation font size. */
    val translationScale: Float
        get() = TextScale.scaleOf(translationSizeStep)

    companion object {
        /** Bangla first, then English, as in the design mockups; both ship inside the app. */
        val DEFAULT_TRANSLATIONS = listOf(TranslationId("bn_zakaria"), TranslationId("en_saheeh"))
    }
}
