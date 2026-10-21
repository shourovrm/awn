package app.awn.quran.feature.read

import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationInfo
import app.awn.quran.feature.read.quran.layerTranslations
import app.awn.quran.feature.read.quran.shouldMoveSettingsToOverflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarLogicTest {
    private fun info(id: String, name: String, language: String) =
        TranslationInfo(TranslationId(id), name, language, installed = true)

    @Test
    fun theLayersMenuListsTranslationsByLanguageThenNameWithoutTheTransliteration() {
        val installed =
            listOf(
                info("en_saheeh", "Saheeh International", "en"),
                info("en_transliteration", "English transliteration", "en"),
                info("bn_zakaria", "Zakaria", "bn"),
                info("en_abdul_haleem", "Abdel Haleem", "en"),
            )

        val names = layerTranslations(installed).map { it.name }

        assertEquals(listOf("Zakaria", "Abdel Haleem", "Saheeh International"), names)
    }

    @Test
    fun settingsMovesIntoTheOverflowOnlyWhenTheTitleWouldBeSqueezed() {
        // Line by line has six buttons on the right; a 412 dp phone cannot keep the title wide.
        assertTrue(shouldMoveSettingsToOverflow(412.dp, buttonCount = 6))
        assertFalse(shouldMoveSettingsToOverflow(412.dp, buttonCount = 5))
        assertFalse(shouldMoveSettingsToOverflow(600.dp, buttonCount = 6))
    }
}
