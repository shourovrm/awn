package app.awn.quran.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Switches the whole app to one language. */
fun interface AppLanguageApplier {
    fun apply(language: AppLanguage)
}

/**
 * Applies the language with AppCompat's per-app locale API. On Android 13 and later AppCompat hands
 * the locale to the system's LocaleManager; before that it stores the locale itself (the
 * AppLocalesMetadataHolderService entry in the manifest) and applies it in MainActivity. In both
 * cases the running activity is recreated, which makes Compose read the new strings.
 */
object AppCompatLanguageApplier : AppLanguageApplier {
    override fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.languageTag),
        )
    }
}
