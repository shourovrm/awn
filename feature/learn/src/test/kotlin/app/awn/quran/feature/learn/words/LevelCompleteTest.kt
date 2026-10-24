package app.awn.quran.feature.learn.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelCompleteTest {

    private val almostLevelOne = (1..95).toSet()
    private val wholeLevelOne = (1..100).toSet()

    private fun toCelebrate(
        before: Set<Int>,
        after: Set<Int>,
        celebrated: Set<Int> = emptySet(),
        formCount: Int = 5_000,
    ) = LevelComplete.toCelebrate(before, after, formCount, celebrated)

    @Test
    fun theSessionThatMeetsTheLastWordsOfALevelFinishesIt() {
        assertEquals(1, toCelebrate(almostLevelOne, wholeLevelOne)?.number)
    }

    @Test
    fun aSessionInTheMiddleOfALevelFinishesNothing() {
        assertNull(toCelebrate((1..40).toSet(), (1..50).toSet()))
    }

    @Test
    fun aLevelCelebratedBeforeNeverComesAgain() {
        assertNull(toCelebrate(almostLevelOne, wholeLevelOne, celebrated = setOf(1)))
    }

    @Test
    fun aLevelThatWasWholeBeforeTheSessionIsNotNew() {
        assertNull(toCelebrate(wholeLevelOne, wholeLevelOne + 101))
    }

    @Test
    fun aLaterLevelNeedsAllItsWordsNotJustTheLastSet() {
        val after = wholeLevelOne + (191..200)

        assertNull(toCelebrate(wholeLevelOne + (181..190), after))
        assertEquals(2, toCelebrate(wholeLevelOne + (101..190), wholeLevelOne + (101..200))?.number)
    }

    @Test
    fun aShortWordListFinishesTheLevelAtItsLastForm() {
        assertEquals(1, toCelebrate((1..90).toSet(), (1..95).toSet(), formCount = 95)?.number)
    }
}
