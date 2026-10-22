package app.awn.quran.feature.read.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The layers menu said only "Word by word", so the user could not tell which language the meanings
 * were in, and the Settings row read "Word by word, English, Word by word, Bangla". Both now name
 * the one language the page shows, in the app's language.
 */
class WordByWordLabelTest {

    @Test
    fun theMenuLabelNamesTheLanguageOfTheMeanings() {
        assertEquals(
            "Word by word · Bangla",
            wordByWordMenuLabel("Word by word", languageCode = "bn", appLocale = Locale.ENGLISH),
        )
    }

    @Test
    fun theLanguageNameIsWrittenInTheAppLanguage() {
        assertEquals(
            "শব্দে শব্দে · ইংরেজি",
            wordByWordMenuLabel(
                "শব্দে শব্দে",
                languageCode = "en",
                appLocale = Locale.forLanguageTag("bn"),
            ),
        )
    }

    @Test
    fun theSettingsSummaryIsTheLanguageShownNotEveryPackName() {
        assertEquals(
            "Bangla",
            wordByWordSummary(
                chosen = "bn",
                appLanguage = "en",
                installedLanguages = setOf("en", "bn"),
                appLocale = Locale.ENGLISH,
            ),
        )
    }

    @Test
    fun theSettingsSummaryFallsBackToEnglishLikeThePage() {
        assertEquals(
            "English",
            wordByWordSummary(
                chosen = null,
                appLanguage = "bn",
                installedLanguages = setOf("en"),
                appLocale = Locale.ENGLISH,
            ),
        )
    }
}
