package app.awn.quran.core.data.translations

import app.awn.quran.core.data.AyahRef
import org.junit.Assert.assertEquals
import org.junit.Test

class PhraseRepeatsTest {

    @Test
    fun keepsAPhraseUnderItsFirstWordOnly() {
        val meanings =
            listOf("And an announcement", "from Allah", "from Allah", "and His Messenger")

        assertEquals(
            listOf("And an announcement", "from Allah", "", "and His Messenger"),
            PhraseRepeats.collapse(AyahRef(9, 3), meanings),
        )
    }

    @Test
    fun emptiesEveryContinuationOfALongPhrase() {
        val meanings = listOf("(of) the greater Pilgrimage", "(of) the greater Pilgrimage")
        val meaningsThree = meanings + "(of) the greater Pilgrimage"

        assertEquals(
            listOf("(of) the greater Pilgrimage", ""),
            PhraseRepeats.collapse(AyahRef(9, 3), meanings),
        )
        assertEquals(
            listOf("(of) the greater Pilgrimage", "", ""),
            PhraseRepeats.collapse(AyahRef(9, 3), meaningsThree),
        )
    }

    @Test
    fun keepsTheOneGenuineRepeatOfTwoDifferentWords() {
        val meanings = List(17) { "word $it" }.toMutableList()
        meanings[14] = "that"
        meanings[15] = "that"

        assertEquals(meanings, PhraseRepeats.collapse(AyahRef(4, 24), meanings))
    }

    @Test
    fun leavesDistinctMeaningsAndEmptyListsAlone() {
        assertEquals(
            listOf("a", "b", "a"),
            PhraseRepeats.collapse(AyahRef(1, 1), listOf("a", "b", "a")),
        )
        assertEquals(emptyList<String>(), PhraseRepeats.collapse(AyahRef(1, 1), emptyList()))
    }
}
