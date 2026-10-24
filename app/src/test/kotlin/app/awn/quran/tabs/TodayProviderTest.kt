package app.awn.quran.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayProviderTest {

    @Test
    fun theLearnRowIsAbsentWhenNothingIsDue() {
        assertNull(learnTodayCount(0))
    }

    @Test
    fun theLearnRowShowsTodaysCountAndNeverAPileOfOverdueAyahs() {
        assertEquals(7, learnTodayCount(7))
        assertEquals(LEARN_TODAY_CAP, learnTodayCount(400))
    }
}
