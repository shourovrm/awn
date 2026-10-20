package app.awn.quran.tracker

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class JsonReferencePhonemesTest {

    private val modelsDirectory = File(System.getProperty("awn.models.directory"))
    private val referenceFile = File(modelsDirectory, "ordered_quran_phonemes.json")
    private val tokensFile = File(modelsDirectory, "tokens.txt")

    @Test
    fun parsesSmallDocument() {
        val json = """{"schema": 2, "verses": {"112:1": {"aya_phonemes_list": ["قُل", "هُوَ"]}}}"""

        val reference = JsonReferencePhonemes(json)

        val words = reference.forAyah(112, 1)
        assertEquals(listOf("ق", "ُ", "ل"), words[0].phonemes)
        assertEquals(WordKeyLike(112, 1, 2), words[1].key)
        assertTrue(reference.forAyah(112, 2).isEmpty())
    }

    @Test
    fun pairsMaddRulesWithTheirRunsInTheFirstAyah() {
        assumeTrue("Skipped: model files not found in $modelsDirectory.", referenceFile.isFile)
        val reference = JsonReferencePhonemes.fromFile(referenceFile)

        val madds = reference.forAyah(1, 1).flatMap { it.madds }

        assertEquals(listOf("natural", "natural", "aared"), madds.map { it.rule })
        assertEquals(listOf(2, 2, 4), madds.map { it.harakat })
        assertEquals(listOf("ا", "ا", "ۦ"), madds.map { it.letter })
    }

    @Test
    fun rejectsOtherSchemas() {
        val error = runCatching {
            JsonReferencePhonemes("""{"schema": 3, "verses": {}}""")
        }
            .exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun everyAyahHasWordsAndEveryPhonemeIsAModelSymbol() {
        assumeTrue("Skipped: model files not found in $modelsDirectory.", referenceFile.isFile)
        val reference = JsonReferencePhonemes.fromFile(referenceFile)

        assertEquals(TOTAL_AYAH_COUNT, reference.ayahCount())
        assertEquals(4, reference.forAyah(112, 1).size)
        assertEquals(1, reference.forAyah(2, 1).size)

        val modelSymbols =
            tokensFile.readLines().filter { it.isNotBlank() }.map { it.substringBeforeLast(' ') }
        val unknownPhonemes = reference.allPhonemes() - modelSymbols.toSet()
        assertTrue("Phonemes missing from tokens.txt: $unknownPhonemes", unknownPhonemes.isEmpty())
    }

    private companion object {
        const val TOTAL_AYAH_COUNT = 6236
    }
}
