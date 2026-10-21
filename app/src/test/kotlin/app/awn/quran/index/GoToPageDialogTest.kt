package app.awn.quran.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoToPageDialogTest {

    @Test
    fun aPageOfTheBookIsAccepted() {
        assertEquals(294, parsePageNumber(" 294 ", 604))
        assertEquals(1, parsePageNumber("1", 604))
        assertEquals(604, parsePageNumber("604", 604))
    }

    @Test
    fun anythingElseIsNotAPage() {
        assertNull(parsePageNumber("", 604))
        assertNull(parsePageNumber("0", 604))
        assertNull(parsePageNumber("605", 604))
        assertNull(parsePageNumber("abc", 604))
    }
}
