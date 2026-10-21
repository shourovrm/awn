package app.awn.quran.feature.learn.session

import app.awn.quran.core.data.WordKey
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistractorChoiceTest {

    private fun info(rank: Int, meaning: String = "meaning $rank", letters: Int = 4) =
        WordInfo(rank, WordKey(2, rank, 1), meaning, letters, ayahWordCount = 10)

    @Test
    fun candidateRanksWalkOutwardsFromTheTarget() {
        assertEquals(listOf(9, 11, 8, 12, 7), DistractorChoice.candidateRanks(10, 100, limit = 5))
    }

    @Test
    fun candidateRanksStopAtBothEndsOfTheRanking() {
        assertEquals(listOf(2, 3, 4), DistractorChoice.candidateRanks(1, 4, limit = 10))
        assertEquals(listOf(3, 2, 1), DistractorChoice.candidateRanks(4, 4, limit = 10))
    }

    @Test
    fun theTargetItselfIsNeverChosen() {
        val target = info(10)

        val chosen = DistractorChoice.choose(target, listOf(info(10), info(11)), 3, Random(1))

        assertEquals(listOf(11), chosen.map { it.rank })
    }

    @Test
    fun noChosenAnswerSharesAMeaningWithTheTargetOrWithAnotherAnswer() {
        val target = info(10, meaning = "Punishment")
        val candidates =
            listOf(
                info(11, meaning = " punishment "),
                info(12, meaning = "veil"),
                info(13, meaning = "Veil"),
                info(14, meaning = "seal"),
                info(15, meaning = "hearing"),
            )

        repeat(30) { seed ->
            val chosen = DistractorChoice.choose(target, candidates, 3, Random(seed))

            val meanings = chosen.map { it.meaning.trim().lowercase() }
            assertEquals(3, chosen.size)
            assertEquals(meanings.size, meanings.toSet().size)
            assertFalse("punishment" in meanings)
        }
    }

    @Test
    fun aCandidateWithoutAMeaningIsSkipped() {
        val chosen =
            DistractorChoice.choose(info(10), listOf(info(11, meaning = " "), info(12)), 3, Random(1))

        assertEquals(listOf(12), chosen.map { it.rank })
    }

    @Test
    fun closerRanksAndSimilarLengthsAreChosenBeforeFarOnes() {
        val target = info(100, letters = 5)
        val near = listOf(info(99, letters = 5), info(101, letters = 5), info(103, letters = 5))
        val far = listOf(info(900, letters = 5), info(2000, letters = 9), info(95, letters = 11))

        repeat(30) { seed ->
            val chosen = DistractorChoice.choose(target, far + near, 3, Random(seed))

            assertEquals(near.map { it.rank }.toSet(), chosen.map { it.rank }.toSet())
        }
    }

    @Test
    fun fewerCandidatesThanRequestedGivesFewerAnswers() {
        val chosen = DistractorChoice.choose(info(10), listOf(info(11)), 3, Random(1))

        assertTrue(chosen.size == 1)
    }
}
