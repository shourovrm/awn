package app.awn.quran.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayProviderTest {

    private fun item(title: String) = TodayItem(title, "detail", icon = 0, onOpen = {})

    @Test
    fun noProviderRowsMeansNoSection() {
        assertTrue(visibleTodayItems(listOf(null, null)).isEmpty())
        assertTrue(visibleTodayItems(emptyList()).isEmpty())
    }

    @Test
    fun aProviderWithNothingToDoLeavesOnlyItsOwnRowOut() {
        val shown = visibleTodayItems(listOf(null, item("Learn")))

        assertEquals(listOf("Learn"), shown.map { it.title })
    }

    @Test
    fun atMostTwoRowsShowInTheOrderOfTheProviders() {
        val shown = visibleTodayItems(listOf(item("Hifz"), item("Learn"), item("Extra")))

        assertEquals(listOf("Hifz", "Learn"), shown.map { it.title })
    }

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
