package app.awn.quran.core.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class EditDistanceTest {

    @Test
    fun equalWordsAreZeroApart() {
        assertEquals(0, editDistance("mercy", "mercy", limit = 2))
    }

    @Test
    fun anInsertionADeletionAndASubstitutionEachCostOne() {
        assertEquals(1, editDistance("mercy", "mercyy", limit = 2))
        assertEquals(1, editDistance("iklas", "ikhlas", limit = 2))
        assertEquals(1, editDistance("mulq", "mulk", limit = 2))
    }

    @Test
    fun aDistanceOverTheLimitIsReportedAsLimitPlusOne() {
        assertEquals(2, editDistance("mercy", "mercuu", limit = 1))
        assertEquals(3, editDistance("abcdef", "uvwxyz", limit = 2))
        assertEquals(2, editDistance("a", "abcd", limit = 1))
    }
}
