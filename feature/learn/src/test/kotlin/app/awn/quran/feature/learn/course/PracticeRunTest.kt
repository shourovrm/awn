package app.awn.quran.feature.learn.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeRunTest {

    private fun item(correctIndex: Int) =
        PracticeItem.Question(
            LessonCheck("q", null, null, listOf("a", "b", "c"), correctIndex, null, "because"),
        )

    @Test
    fun `the same seed draws the same items in the same order`() {
        val pool = (1..30).toList()

        assertEquals(PracticeDraw.draw(pool, 10, seed = 7L), PracticeDraw.draw(pool, 10, seed = 7L))
    }

    @Test
    fun `different seeds draw different sets`() {
        val pool = (1..30).toList()

        assertTrue(PracticeDraw.draw(pool, 10, 1L) != PracticeDraw.draw(pool, 10, 2L))
    }

    @Test
    fun `a draw has the asked count without repeats and only items of the pool`() {
        val pool = (1..30).toList()

        val drawn = PracticeDraw.draw(pool, 10, 3L)

        assertEquals(10, drawn.size)
        assertEquals(10, drawn.toSet().size)
        assertTrue(pool.containsAll(drawn))
    }

    @Test
    fun `a pool smaller than the count gives every item`() {
        val drawn = PracticeDraw.draw(listOf("a", "b", "c"), 10, 5L)

        assertEquals(setOf("a", "b", "c"), drawn.toSet())
        assertEquals(3, drawn.size)
    }

    @Test
    fun `an empty pool draws nothing`() {
        assertTrue(PracticeDraw.draw(emptyList<Int>(), 10, 5L).isEmpty())
    }

    @Test
    fun `the first pick counts and next waits for an answer`() {
        val run = PracticeRun(listOf(item(1), item(0)))

        assertEquals(run, run.next())
        val answered = run.pick(2).pick(1)
        assertEquals(2, answered.currentPick)
        assertEquals(1, answered.next().position)
        assertNull(answered.next().currentPick)
    }

    @Test
    fun `the run counts right picks and finishes after the last item`() {
        val run = PracticeRun(listOf(item(1), item(0))).pick(1).next().pick(2).next()

        assertTrue(run.isFinished)
        assertEquals(1, run.rightCount)
        assertNull(run.current)
        assertEquals(run, run.pick(0))
    }

    @Test
    fun `a read-aloud item is finished without a right or wrong answer`() {
        val run =
            PracticeRun(listOf(PracticeItem.ReadAloud("[[حَ]]", "qaida-04/012"), item(1)))
                .finishReading()
                .next()
                .pick(1)
                .next()

        assertTrue(run.isFinished)
        assertEquals(1, run.questionCount)
        assertEquals(1, run.rightCount)
    }

    @Test
    fun `a new run has not finished`() {
        assertFalse(PracticeRun(listOf(item(0))).isFinished)
    }
}
