package app.awn.quran.feature.recite

import org.junit.Assert.assertEquals
import org.junit.Test

class ReciteRangeTest {

    @Test
    fun movingTheStartPastTheEndPullsTheEndAlong() {
        assertEquals(ReciteRange(2, 9, 9), ReciteRange(2, 3, 5).withFirstAyah(9))
    }

    @Test
    fun movingTheEndBeforeTheStartPullsTheStartAlong() {
        assertEquals(ReciteRange(2, 1, 1), ReciteRange(2, 3, 5).withLastAyah(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun anEmptyRangeIsRejected() {
        ReciteRange(1, 5, 4)
    }
}
