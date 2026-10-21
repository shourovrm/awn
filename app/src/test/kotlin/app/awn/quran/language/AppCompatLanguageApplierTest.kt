package app.awn.quran.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Below Android 13 AppCompat keeps the requested locales itself, so the call works without an
// activity.
@Config(sdk = [32])
class AppCompatLanguageApplierTest {

    @After
    fun clearLanguage() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun applyingBanglaMakesBanglaTheAppLanguageAndEnglishReplacesIt() {
        AppCompatLanguageApplier.apply(AppLanguage.Bangla)
        assertEquals("bn", AppCompatDelegate.getApplicationLocales()[0]?.language)

        AppCompatLanguageApplier.apply(AppLanguage.English)
        assertEquals("en", AppCompatDelegate.getApplicationLocales()[0]?.language)
    }

    @Test
    fun noLanguageIsStoredUntilOneIsApplied() {
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
    }
}
