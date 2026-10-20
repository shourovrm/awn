package app.awn.quran.player

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReciterTest {

    @Test
    fun keepsWordByWordFlag() {
        assertTrue(Reciter("husary", "Mahmoud Khalil Al-Husary", wordByWord = true).wordByWord)
    }

    @Test
    fun rejectsBlankId() {
        assertThrows(IllegalArgumentException::class.java) { Reciter(" ", "Name", false) }
    }
}
