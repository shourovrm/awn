package app.awn.quran.feature.learn.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicAnswerTest {

    @Test
    fun aLetterOnItsOwnIsDrawnAsArabic() {
        assertTrue(isArabicAnswer("[[خ]]"))
        assertTrue(isArabicAnswer(" [[بـ]] "))
    }

    @Test
    fun aQuranWordIsDrawnAsArabic() {
        assertTrue(isArabicAnswer("{{2:28:7}}"))
    }

    @Test
    fun anAnswerInWordsStaysText() {
        assertFalse(isArabicAnswer("At the right edge of the page"))
        assertFalse(isArabicAnswer("Fatha then [[ا]]"))
    }
}
