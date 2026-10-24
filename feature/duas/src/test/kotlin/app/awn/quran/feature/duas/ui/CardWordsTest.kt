package app.awn.quran.feature.duas.ui

import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.content.DuaOpening
import org.junit.Assert.assertEquals
import org.junit.Test

class CardWordsTest {

    private fun words(surah: Int, ayah: Int, count: Int) =
        (1..count).map { position ->
            Word(WordKey(surah, ayah, position), "w$ayah-$position", codeV1 = null, codeV2 = null)
        }

    private fun dua(start: WordKey, end: WordKey) =
        Dua("d", start, end, "g", DuaOpening.Rabbana, prophetKey = null, isStarter = false)

    @Test
    fun wordsBeforeTheDuaInsideItsAyahAreNotDuaWords() {
        val marked = cardWords(words(2, 201, 14), dua(WordKey(2, 201, 4), WordKey(2, 201, 14)))

        assertEquals(List(3) { false } + List(11) { true }, marked.map { it.isDuaWord })
    }

    @Test
    fun aDuaOverSeveralAyahsMarksTheWordsBetweenItsEnds() {
        val all = words(20, 25, 3) + words(20, 26, 3) + words(20, 27, 4) + words(20, 28, 2)

        val marked = cardWords(all, dua(WordKey(20, 25, 2), WordKey(20, 27, 4)))

        val expected = listOf(false) + List(2 + 3 + 4) { true } + List(2) { false }
        assertEquals(expected, marked.map { it.isDuaWord })
        assertEquals(all, marked.map { it.word })
    }
}
