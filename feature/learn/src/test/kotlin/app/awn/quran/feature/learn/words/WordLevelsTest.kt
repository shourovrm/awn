package app.awn.quran.feature.learn.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordLevelsTest {

    @Test
    fun sevenLevelsEndAtTheirWordCounts() {
        assertEquals(
            listOf(100, 200, 300, 500, 1_000, 2_000, 5_000),
            WordLevels.all.map { it.lastRank },
        )
        assertEquals(
            listOf(1, 101, 201, 301, 501, 1_001, 2_001),
            WordLevels.all.map { it.firstRank },
        )
    }

    @Test
    fun theLastRankOfALevelBelongsToIt() {
        assertEquals(1, WordLevels.levelOfRank(100)?.number)
        assertEquals(2, WordLevels.levelOfRank(101)?.number)
        assertEquals(4, WordLevels.levelOfRank(500)?.number)
        assertEquals(5, WordLevels.levelOfRank(501)?.number)
        assertEquals(7, WordLevels.levelOfRank(5_000)?.number)
    }

    @Test
    fun aRankAboveTheLastLevelHasNoLevel() {
        assertNull(WordLevels.levelOfRank(5_001))
        assertNull(WordLevels.levelOfSet(501))
    }

    @Test
    fun aSetBelongsToTheLevelOfItsWords() {
        assertEquals(1, WordLevels.levelOfSet(10)?.number)
        assertEquals(2, WordLevels.levelOfSet(11)?.number)
        assertEquals(4, WordLevels.levelOfSet(50)?.number)
        assertEquals(5, WordLevels.levelOfSet(51)?.number)
        assertEquals(7, WordLevels.levelOfSet(500)?.number)
    }

    @Test
    fun aLevelListsItsSetNumbers() {
        assertEquals(1..10, WordLevels.all[0].sets)
        assertEquals(11..20, WordLevels.all[1].sets)
        assertEquals(201..500, WordLevels.all[6].sets)
    }

    @Test
    fun aLevelCountsTheWordsInItselfNotTheRunningTotal() {
        assertEquals(
            listOf(100, 100, 100, 200, 500, 1_000, 3_000),
            WordLevels.all.map { it.wordCount },
        )
    }
}
