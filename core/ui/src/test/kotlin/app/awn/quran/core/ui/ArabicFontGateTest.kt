package app.awn.quran.core.ui

import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.ui.theme.AwnTypography
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arabic outside the Mushaf page (word pop-ups, menus, the snippet, the index, search results,
 * Learn, the recite mistake card) used the phone's generic serif or sans-serif face, which looks
 * plain and draws Uthmani marks badly. Every Arabic text style now uses the Uthmanic Hafs font that
 * ships with the app, the same one the page falls back to.
 */
class ArabicFontGateTest {

    private val genericFamilies =
        listOf(FontFamily.Serif, FontFamily.SansSerif, FontFamily.Default, FontFamily.Monospace)

    @Test
    fun quranTextUsesTheBundledUthmanicFont() {
        genericFamilies.forEach { generic ->
            assertNotEquals(generic, AwnTypography.quran.fontFamily)
        }
        assertTrue(AwnTypography.quran.fontFamily != null)
    }

    @Test
    fun theQuranLineOnTheTabsUsesTheSameFontAsQuranText() {
        assertTrue(AwnTypography.quranLead.fontFamily == AwnTypography.quran.fontFamily)
    }

    @Test
    fun noShippedScreenPicksAGenericSerifFace() {
        val offenders =
            RepositorySources.shippedKotlinFiles()
                .filter { it.readText().contains("FontFamily.Serif") }
                .map { RepositorySources.relativePath(it) }

        assertTrue("Use AwnTypography.quran for Arabic instead: $offenders", offenders.isEmpty())
    }
}
