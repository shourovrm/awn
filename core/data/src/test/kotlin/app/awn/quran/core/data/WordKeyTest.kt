package app.awn.quran.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WordKeyTest {

    @Test
    fun roundTripsThroughText() {
        val key = WordKey(surah = 2, ayah = 255, wordPosition = 3)

        assertEquals("2:255:3", key.toString())
        assertEquals(key, WordKey.parse("2:255:3"))
    }

    @Test
    fun ordersBySurahThenAyahThenWord() {
        assertTrue(WordKey(1, 7, 4) < WordKey(2, 1, 1))
        assertTrue(WordKey(2, 1, 9) < WordKey(2, 2, 1))
        assertTrue(WordKey(2, 2, 1) < WordKey(2, 2, 2))
    }

    @Test
    fun rejectsOutOfRangeParts() {
        assertThrows(IllegalArgumentException::class.java) { WordKey(0, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { WordKey(115, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { WordKey(1, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { WordKey(1, 1, 0) }
    }

    @Test
    fun rejectsMalformedText() {
        assertThrows(IllegalArgumentException::class.java) { WordKey.parse("2:255") }
        assertThrows(IllegalArgumentException::class.java) { WordKey.parse("2:x:3") }
    }
}
