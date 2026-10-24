package app.awn.quran.feature.learn.course

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenPickTest {

    private fun material(vararg arabicTexts: String) = arabicTexts.mapIndexed { index, arabic ->
        SoundMaterial("[[$arabic]]", "qaida-01/%03d".format(index + 1))
    }

    private val unitMaterial = material("با", "تا", "ثا", "نا", "حا", "دا", "را", "سا")

    @Test
    fun theRightAnswerIsNeverAmongTheWrongOnes() {
        repeat(50) { seed ->
            val correct = unitMaterial[0]

            val wrong = ListenPick.distractors(correct, unitMaterial, 3, Random(seed))

            assertEquals(3, wrong.size)
            assertTrue(wrong.none { it.arabic == correct.arabic })
            assertEquals(wrong.size, wrong.map { it.arabic }.toSet().size)
        }
    }

    @Test
    fun lettersThatLookAlikeAreChosenBeforeOthers() {
        val correct = unitMaterial[0]

        repeat(20) { seed ->
            val wrong = ListenPick.distractors(correct, unitMaterial, 3, Random(seed))

            assertEquals(setOf("[[تا]]", "[[ثا]]", "[[نا]]"), wrong.map { it.arabic }.toSet())
        }
    }

    @Test
    fun otherLettersFillTheGapWhenTooFewLookAlike() {
        val sparse = material("با", "تا", "را", "سا", "حا")

        val wrong = ListenPick.distractors(sparse[0], sparse, 3, Random(1))

        assertEquals(3, wrong.size)
        assertEquals("[[تا]]", wrong.first().arabic)
    }

    @Test
    fun anOptionWithoutAClipIsNeverOffered() {
        val withGap = unitMaterial.mapIndexed { index, item ->
            if (index in 1..4) item.copy(clip = "") else item
        }

        val wrong = ListenPick.distractors(withGap[0], withGap, 3, Random(3))

        assertEquals(setOf("[[دا]]", "[[را]]", "[[سا]]"), wrong.map { it.arabic }.toSet())
        assertTrue(wrong.all { it.clip.isNotBlank() })
    }

    @Test
    fun aUnitWithTooLittleMaterialAsksNoQuestion() {
        val tooFew = material("با", "تا", "ثا")

        assertNull(ListenPick.question(tooFew[0], tooFew, Random(1)))
    }

    @Test
    fun theQuestionPlaysTheRightClipAndHoldsTheRightArabicOnce() {
        val correct = unitMaterial[2]

        val item = ListenPick.question(correct, unitMaterial, Random(7))

        assertNotNull(item)
        val check = item!!.check
        assertEquals(4, check.options.size)
        assertEquals(correct.arabic, check.options[check.correctIndex])
        assertEquals(1, check.options.count { it == correct.arabic })
        assertEquals(listOf(correct.clip), check.clipIds)
    }

    @Test
    fun theSameSeedGivesTheSameQuestion() {
        val first = ListenPick.question(unitMaterial[0], unitMaterial, Random(11))
        val second = ListenPick.question(unitMaterial[0], unitMaterial, Random(11))

        assertEquals(first, second)
    }

    @Test
    fun aListenQuestionCountsAsRightOrWrongInTheRun() {
        val item = ListenPick.question(unitMaterial[0], unitMaterial, Random(5))!!
        val run = PracticeRun(listOf(item)).pick(item.check.correctIndex).next()

        assertEquals(1, run.questionCount)
        assertEquals(1, run.rightCount)
    }

    @Test
    fun iDontKnowIsNotRight() {
        val item = ListenPick.question(unitMaterial[0], unitMaterial, Random(5))!!
        val run = PracticeRun(listOf(item)).pick(ListenPick.NO_ANSWER).next()

        assertEquals(1, run.questionCount)
        assertEquals(0, run.rightCount)
    }
}
