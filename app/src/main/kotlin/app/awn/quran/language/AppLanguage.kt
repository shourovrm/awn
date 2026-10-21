package app.awn.quran.language

import androidx.annotation.StringRes
import app.awn.quran.R
import java.util.Locale

/** The languages the app's text is written in. */
enum class AppLanguage(
    val languageTag: String,
    /** The language's name in its own script; never translated. */
    @StringRes val ownNameRes: Int,
    /** The language's name in the language the screen is currently in. */
    @StringRes val translatedNameRes: Int,
) {
    English("en", R.string.language_own_english, R.string.language_name_english),
    Bangla("bn", R.string.language_own_bangla, R.string.language_name_bangla);

    val locale: Locale
        get() = Locale.forLanguageTag(languageTag)

    companion object {
        /**
         * The language to start with on a phone set to [phoneLocale]: Bangla for a Bangla phone,
         * English for every other language, because English is the only other language the app has.
         */
        fun preselectedFor(phoneLocale: Locale): AppLanguage =
            if (phoneLocale.language == Bangla.languageTag) Bangla else English
    }
}
