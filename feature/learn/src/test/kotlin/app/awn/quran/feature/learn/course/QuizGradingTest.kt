package app.awn.quran.feature.learn.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizGradingTest {

    @Test
    fun `an order is right only in the order of the items`() {
        val order = QuizQuestion.Order("Order", listOf("[[ا]]", "[[ب]]", "[[ج]]"), "e")

        assertTrue(order.isRight(listOf("[[ا]]", "[[ب]]", "[[ج]]")))
        assertFalse(order.isRight(listOf("[[ب]]", "[[ا]]", "[[ج]]")))
        assertFalse(order.isRight(listOf("[[ا]]", "[[ب]]")))
    }

    @Test
    fun `a sorted item is right in its own group only`() {
        val sort =
            QuizQuestion.Sort(
                "Sort",
                listOf(QuizGroup("Like [[ب]]", listOf("[[ب]]", "[[ت]]")), QuizGroup("Like [[ح]]", listOf("[[ح]]"))),
                "e",
            )

        assertTrue(sort.isRightGroup("[[ت]]", groupIndex = 0))
        assertFalse(sort.isRightGroup("[[ت]]", groupIndex = 1))
        assertEquals(listOf("[[ب]]", "[[ت]]", "[[ح]]"), sort.allItems)
    }

    @Test
    fun `a match pairs a left item with its own right item only`() {
        val match = QuizQuestion.Match("Match", listOf(QuizPair("[[ب]]", "One below"), QuizPair("[[ت]]", "Two above")), "e")

        assertTrue(match.isPair("[[ب]]", "One below"))
        assertFalse(match.isPair("[[ب]]", "Two above"))
    }

    @Test
    fun `a find is done once every answer is found, and a wrong tap is not an answer`() {
        val find = QuizQuestion.Find("Find", listOf("[[ت]]", "[[ط]]", "[[ص]]"), listOf("[[ط]]", "[[ص]]"), "e")

        assertFalse(find.isAnswer("[[ت]]"))
        assertFalse(find.isComplete(setOf("[[ط]]")))
        assertTrue(find.isComplete(setOf("[[ط]]", "[[ص]]")))
    }

    @Test
    fun `pick, listen and name are right on the right option only`() {
        val pick = QuizQuestion.Pick("Which?", listOf("[[ت]]", "[[ب]]"), correctIndex = 1, explanation = "e")

        assertTrue(pick.isRight(1))
        assertFalse(pick.isRight(0))
    }
}
