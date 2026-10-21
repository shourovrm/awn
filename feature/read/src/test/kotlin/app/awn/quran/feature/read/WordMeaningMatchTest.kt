package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.read.quran.PageContentLoader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Word-by-word meanings belong to word positions, not to places in a list. */
class WordMeaningMatchTest {
    private val fixture = ReadFixture()
    private val loader = PageContentLoader(fixture.text, fixture.translations, wordLanguage = "en")
    private val ayah = AyahRef(2, 255)

    @Test
    fun aMeaningFollowsThePositionOfItsWord() = runTest {
        // The word list starts at position 2 and carries the ayah-end sign as a last token.
        fixture.text.wordsByAyah[ayah] =
            listOf(
                ReadFixture.word(2, 255, 2, "لَآ"),
                ReadFixture.word(2, 255, 3, "إِلَٰهَ"),
                ReadFixture.word(2, 255, 4, "﴿"),
            )
        fixture.translations.wordMeanings["en" to ayah] = listOf("Allah", "no", "god")

        val block = loader.loadBlock(ayah, emptyList())

        assertEquals(listOf("no", "god", null), block.words.map { it.meaning })
    }

    @Test
    fun aBlankMeaningShowsNothing() = runTest {
        fixture.text.wordsByAyah[ayah] =
            listOf(ReadFixture.word(2, 255, 1, "ٱللَّهُ"), ReadFixture.word(2, 255, 2, "لَآ"))
        fixture.translations.wordMeanings["en" to ayah] = listOf("Allah", "")

        val block = loader.loadBlock(ayah, emptyList())

        assertEquals("Allah", block.words[0].meaning)
        assertNull(block.words[1].meaning)
    }
}
