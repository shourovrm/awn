package app.awn.quran.language

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun aBanglaPhoneStartsWithBangla() {
        assertEquals(AppLanguage.Bangla, AppLanguage.preselectedFor(Locale.forLanguageTag("bn")))
        assertEquals(AppLanguage.Bangla, AppLanguage.preselectedFor(Locale.forLanguageTag("bn-BD")))
        assertEquals(AppLanguage.Bangla, AppLanguage.preselectedFor(Locale.forLanguageTag("bn-IN")))
    }

    @Test
    fun everyOtherPhoneStartsWithEnglish() {
        listOf("en-US", "en-GB", "ar-SA", "ur-PK", "hi-IN", "fr-FR").forEach { tag ->
            assertEquals(
                tag,
                AppLanguage.English,
                AppLanguage.preselectedFor(Locale.forLanguageTag(tag)),
            )
        }
    }

    @Test
    fun theTagsMatchTheResourceFolders() {
        // values-bn holds the Bangla strings; a changed tag would stop the app finding them.
        assertEquals(listOf("en", "bn"), AppLanguage.entries.map { it.languageTag })
    }
}
