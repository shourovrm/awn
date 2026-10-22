package app.awn.quran.feature.read.settings

import java.util.Locale

private const val LABEL_SEPARATOR = " · "

/** The name of [languageCode] in [appLocale], such as "Bangla" or "বাংলা". */
internal fun languageDisplayName(languageCode: String, appLocale: Locale): String =
    Locale.forLanguageTag(languageCode).getDisplayLanguage(appLocale).replaceFirstChar {
        it.titlecase(appLocale)
    }

/** "Word by word · Bangla": the layers menu label, so the language of the meanings is visible. */
internal fun wordByWordMenuLabel(
    baseLabel: String,
    languageCode: String,
    appLocale: Locale,
): String = baseLabel + LABEL_SEPARATOR + languageDisplayName(languageCode, appLocale)

/** The one language the page shows for word by word, named in the app language. */
internal fun wordByWordSummary(
    chosen: String?,
    appLanguage: String,
    installedLanguages: Set<String>,
    appLocale: Locale,
): String =
    languageDisplayName(wordLanguageShown(chosen, appLanguage, installedLanguages), appLocale)
