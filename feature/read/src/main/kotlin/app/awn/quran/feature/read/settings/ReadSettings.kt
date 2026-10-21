package app.awn.quran.feature.read.settings

import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId

/** The two views of the same page, switched by the view button of the Quran screen. */
enum class PageView {
    /** The printed page. */
    Mushaf,
    /** A list of ayahs; the layers menu decides which texts each ayah shows. */
    LineByLine;

    /** The view the view button shows next. */
    fun other(): PageView = if (this == Mushaf) LineByLine else Mushaf
}

/** One kind of text in the line-by-line view; each has its own size and tickbox. */
enum class TextLayer {
    Arabic,
    Translation,
    Transliteration,
    WordByWord,
}

/** How the screen may turn: follow the sensor, or stay upright or sideways. */
enum class ScreenOrientation {
    Auto,
    Portrait,
    Landscape,
}

/** Everything the user can change about reading. */
data class ReadSettings(
    /** The printed Mushaf; the 1421 print unless the user picks another page type. */
    val script: ScriptId = ScriptId.V2,
    val pageView: PageView = PageView.Mushaf,
    /** Layer tickbox: the Arabic text in the line-by-line view. */
    val showArabic: Boolean = true,
    /** Enabled translations, in the order they are shown; each one is a ticked layer. */
    val translations: List<TranslationId> = DEFAULT_TRANSLATIONS,
    val showTransliteration: Boolean = false,
    /**
     * Layer tickbox: the meaning under each word. Which language it is in is [wordByWordLanguage].
     */
    val showWordByWord: Boolean = false,
    /**
     * The language of the word-by-word meanings, such as "en" or "bn". Null follows the app
     * language; a language with no pack on the phone falls back to English where it is read.
     */
    val wordByWordLanguage: String? = null,
    /** Step 0 to 7 of [TextScale], one per layer; the text-size popup moves them one by one. */
    val arabicSizeStep: Int = TextScale.DEFAULT_STEP,
    val translationSizeStep: Int = TextScale.DEFAULT_STEP,
    val transliterationSizeStep: Int = TextScale.DEFAULT_STEP,
    val wordByWordSizeStep: Int = TextScale.DEFAULT_STEP,
    /** Step 0 to 4 of the printed page: 0 fits the width, higher steps print it larger. */
    val mushafSizeStep: Int = 0,
    /** How the Quran pages may turn. */
    val quranOrientation: ScreenOrientation = ScreenOrientation.Auto,
    /** How every other screen of the app may turn. */
    val appOrientation: ScreenOrientation = ScreenOrientation.Auto,
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

    /** The factor that multiplies the transliteration font size. */
    val transliterationScale: Float
        get() = TextScale.scaleOf(transliterationSizeStep)

    /** The factor that multiplies the word-by-word meaning font size. */
    val wordByWordScale: Float
        get() = TextScale.scaleOf(wordByWordSizeStep)

    /** The factor that makes the printed page larger than the screen width; 1 fits it. */
    val mushafScale: Float
        get() = TextScale.mushafScaleOf(mushafSizeStep)

    /** The layers the line-by-line view shows now, in the order the text-size popup lists them. */
    val visibleLayers: List<TextLayer>
        get() = buildList {
            if (showArabic) add(TextLayer.Arabic)
            if (translations.isNotEmpty()) add(TextLayer.Translation)
            if (showTransliteration) add(TextLayer.Transliteration)
            if (showWordByWord) add(TextLayer.WordByWord)
        }

    /** The size step of [layer]. */
    fun stepOf(layer: TextLayer): Int =
        when (layer) {
            TextLayer.Arabic -> arabicSizeStep
            TextLayer.Translation -> translationSizeStep
            TextLayer.Transliteration -> transliterationSizeStep
            TextLayer.WordByWord -> wordByWordSizeStep
        }

    /** A copy with the size step of [layer] moved by [delta] and kept inside the range. */
    fun withStepMoved(layer: TextLayer, delta: Int): ReadSettings {
        val step = TextScale.clampStep(stepOf(layer) + delta)
        return when (layer) {
            TextLayer.Arabic -> copy(arabicSizeStep = step)
            TextLayer.Translation -> copy(translationSizeStep = step)
            TextLayer.Transliteration -> copy(transliterationSizeStep = step)
            TextLayer.WordByWord -> copy(wordByWordSizeStep = step)
        }
    }

    /** A copy with the printed-page size moved by [delta] steps and kept inside the range. */
    fun withMushafStepMoved(delta: Int): ReadSettings =
        copy(mushafSizeStep = TextScale.clampMushafStep(mushafSizeStep + delta))

    companion object {
        /**
         * No translation, transliteration or word meanings on a new installation: none of them
         * ships inside the app, so a layer ticked by default would show an empty cell. The first
         * launch offers a download that ticks them (StarterOfferCard).
         */
        val DEFAULT_TRANSLATIONS = emptyList<TranslationId>()
    }
}
