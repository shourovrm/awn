package app.awn.quran.feature.duas.card

import app.awn.quran.core.data.TranslationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardTranslationTest {

    private val english = TranslationId("en_saheeh")
    private val bangla = TranslationId("bn_zakaria")

    @Test
    fun theAppLanguageTranslationWinsOverAnEarlierEnglishOne() {
        assertEquals(bangla, chooseCardTranslation(listOf(english, bangla), appLanguage = "bn"))
    }

    @Test
    fun theAppLanguageTranslationWinsOverAnEarlierBanglaOne() {
        assertEquals(english, chooseCardTranslation(listOf(bangla, english), appLanguage = "en"))
    }

    @Test
    fun whenNoTickedTranslationMatchesTheFirstTickedOneIsChosen() {
        assertEquals(english, chooseCardTranslation(listOf(english), appLanguage = "bn"))
    }

    @Test
    fun withNothingTickedNoTranslationIsChosen() {
        assertNull(chooseCardTranslation(emptyList(), appLanguage = "bn"))
    }
}
