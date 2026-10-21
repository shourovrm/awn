package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationText
import app.awn.quran.feature.read.quran.PageContentLoader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The transliteration of a word comes from its own pack, not from the Quran text pack. */
class WordTransliterationTest {
    private val fixture = ReadFixture()
    private val loader = PageContentLoader(fixture.text, fixture.translations, wordLanguage = "en")
    private val ayah = AyahRef(1, 1)

    private fun twoWordsOnTheFixture() {
        fixture.text.wordsByAyah[ayah] =
            listOf(ReadFixture.word(1, 1, 1, "بِسْمِ"), ReadFixture.word(1, 1, 2, "ٱللَّهِ"))
    }

    @Test
    fun eachWordTakesTheTransliterationOfItsPosition() = runTest {
        twoWordsOnTheFixture()
        fixture.translations.wordTransliterations[ayah] = listOf("bis'mi", "l-lahi")

        val block = loader.loadBlock(ayah, emptyList())

        assertEquals(listOf("bis'mi", "l-lahi"), block.words.map { it.transliteration })
        assertEquals("bis'mi l-lahi", block.transliteration)
    }

    @Test
    fun withoutTheWordPackTheWholeAyahPackFillsTheLineAndTheWordsStayEmpty() = runTest {
        twoWordsOnTheFixture()
        fixture.translations.texts[TranslationId("en_transliteration")] =
            mutableMapOf(ayah to TranslationText(ayah, 1, 1, "Bismi Allahi"))

        val block = loader.loadBlock(ayah, emptyList())

        assertEquals(listOf(null, null), block.words.map { it.transliteration })
        assertEquals("Bismi Allahi", block.transliteration)
    }

    @Test
    fun withNeitherPackThereIsNoTransliteration() = runTest {
        twoWordsOnTheFixture()

        val block = loader.loadBlock(ayah, emptyList())

        assertNull(block.transliteration)
        assertEquals(listOf(null, null), block.words.map { it.transliteration })
    }
}
