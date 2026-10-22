package app.awn.quran.feature.learn.words

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.WordKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WordFormsTest {

    /**
     * Writes the format of tools/build_learn_words.py: surah 1 has two ayahs and surah 2 one, each
     * with two words; form 1 has four words and form 2 has two.
     */
    private fun fixture(version: Int = 1, trailing: Int? = null): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeBytes("AWLW")
            out.writeByte(version)
            out.writeInt(6)
            out.writeShort(2)
            repeat(114) { surah -> out.writeShort(if (surah == 0) 2 else if (surah == 1) 1 else 0) }
            repeat(3) { out.writeByte(2) }
            listOf(2, 1, 1, 1, 1, 2).forEach { out.writeShort(it) }
            listOf(3, 0).forEach { out.writeInt(it) }
            trailing?.let { out.writeByte(it) }
        }
        return bytes.toByteArray()
    }

    private val forms = WordForms.read(ByteArrayInputStream(fixture()))

    @Test
    fun countsComeFromTheFile() {
        assertEquals(2, forms.formCount)
        assertEquals(6, forms.wordCount)
        assertEquals(4, forms.occurrenceCount(1))
        assertEquals(2, forms.occurrenceCount(2))
    }

    @Test
    fun aWordKeyFindsItsRank() {
        assertEquals(2, forms.rankOf(WordKey(1, 1, 1)))
        assertEquals(1, forms.rankOf(WordKey(1, 2, 1)))
        assertEquals(2, forms.rankOf(WordKey(2, 1, 2)))
    }

    @Test
    fun aRepresentativeIsReadAsAWordKey() {
        assertEquals(WordKey(1, 2, 2), forms.representative(1))
        assertEquals(WordKey(1, 1, 1), forms.representative(2))
    }

    @Test
    fun occurrencesAreListedInReadingOrder() {
        assertEquals(
            listOf(WordKey(1, 1, 2), WordKey(1, 2, 1), WordKey(1, 2, 2), WordKey(2, 1, 1)),
            forms.occurrences(1),
        )
        assertEquals(listOf(WordKey(1, 1, 1), WordKey(2, 1, 2)), forms.occurrences(2))
    }

    @Test
    fun aRankOutsideTheRankingFailsWithTheRange() {
        val error = assertThrows(IllegalArgumentException::class.java) { forms.occurrences(3) }

        assertTrue(error.message!!.contains("1..2"))
    }

    @Test
    fun aKeyThatDoesNotExistFails() {
        assertThrows(IllegalArgumentException::class.java) { forms.rankOf(WordKey(1, 3, 1)) }
        assertThrows(IllegalArgumentException::class.java) { forms.rankOf(WordKey(1, 1, 3)) }
    }

    @Test
    fun aFileOfAnotherVersionIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            WordForms.read(ByteArrayInputStream(fixture(version = 2)))
        }
    }

    @Test
    fun trailingBytesAreRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            WordForms.read(ByteArrayInputStream(fixture(trailing = 7)))
        }
    }

    @Test
    fun theBuiltFileCoversEveryWordOfTheQuran() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val real = WordForms.read(assets.open(WordFormsAsset.PATH))

        assertEquals(77_429, real.wordCount)
        assertEquals(77_429, (1..real.formCount).sumOf { real.occurrenceCount(it) })
        assertTrue(WordKey(1, 1, 1) in real.occurrences(real.rankOf(WordKey(1, 1, 1))))
    }

    @Test
    fun everyRepresentativeBelongsToItsOwnForm() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val real = WordForms.read(assets.open(WordFormsAsset.PATH))

        for (rank in 1..real.formCount) {
            assertEquals(rank, real.rankOf(real.representative(rank)))
        }
    }

    @Test
    fun theMostFrequentFormsAreTheOnesThatOccurMost() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val real = WordForms.read(assets.open(WordFormsAsset.PATH))

        assertEquals(2763, real.occurrenceCount(1))
        for (rank in 2..real.formCount) {
            assertTrue(real.occurrenceCount(rank - 1) >= real.occurrenceCount(rank))
        }
    }
}
