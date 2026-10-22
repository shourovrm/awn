package app.awn.quran.feature.learn.course

import app.awn.quran.feature.learn.session.FakeFrequency
import app.awn.quran.feature.learn.session.ItemRef
import app.awn.quran.feature.learn.session.Pace
import app.awn.quran.feature.learn.session.PlanStep
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoursePlannerTest {

    private val planner = CoursePlanner(fixtureContent(), FakeFrequency(formCount = 100))
    private val now = Instant.parse("2026-10-21T08:00:00Z")
    private val choices = CourseChoices(sessionMinutes = 10, includeLetters = false)

    private fun plan(
        snapshot: app.awn.quran.feature.learn.words.CourseSnapshot,
        choices: CourseChoices = this.choices,
    ) = planner.plan(snapshot, Pace.Default, choices, now, ZoneOffset.UTC)

    @Test
    fun theFirstDayStartsWithABasicsLessonThenTheFirstWords() {
        val plan = plan(snapshotOf())

        assertEquals(PlanStep.Lesson("basics-01"), plan.steps.first())
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
            plan.steps.filterIsInstance<PlanStep.NewWord>().map { it.rank },
        )
    }

    @Test
    fun theLettersCourseComesFirstWhenChosen() {
        val plan = plan(snapshotOf(), choices.copy(includeLetters = true))

        assertEquals(PlanStep.Lesson("letters-02"), plan.steps.first())
    }

    @Test
    fun wordsTheUserAddedAreTaughtBeforeTheNextRanks() {
        val plan = plan(snapshotOf(queued = listOf(50, 60), doneLessons = setOf("basics-01")))

        assertEquals(
            listOf(50, 60, 1),
            plan.steps.filterIsInstance<PlanStep.NewWord>().map { it.rank }.take(3),
        )
    }

    @Test
    fun metWordsAreNotTaughtAgain() {
        val cards = (1..4).map { wordCard(it, due = Long.MAX_VALUE) }

        val plan = plan(snapshotOf(cards, doneLessons = setOf("basics-01")))

        assertEquals(5, plan.steps.filterIsInstance<PlanStep.NewWord>().first().rank)
    }

    @Test
    fun aWordDueEarlierTodayIsReviewedEvenBeforeItsExactTime() {
        val laterToday = Instant.parse("2026-10-21T20:00:00Z").toEpochMilli()
        val tomorrow = Instant.parse("2026-10-22T00:30:00Z").toEpochMilli()
        val cards = listOf(wordCard(1, due = laterToday), wordCard(2, due = tomorrow))

        val plan = plan(snapshotOf(cards, doneLessons = setOf("basics-01")))

        val reviewed = plan.steps.filterIsInstance<PlanStep.Review>().map { it.item }
        assertEquals(listOf<ItemRef>(ItemRef.Word(1)), reviewed)
    }

    @Test
    fun theGrammarLessonComesAfterTheSetThatHoldsItsWord() {
        val cards = (1..20).map { wordCard(it, due = Long.MAX_VALUE) }

        // Word 25 is in set 3; the new words of today are 21 to 30, so the lesson fits after them.
        val plan = plan(snapshotOf(cards, doneLessons = setOf("basics-01")))

        assertTrue(PlanStep.Lesson("grammar-03") in plan.steps)
        assertTrue(
            plan.steps.indexOf(PlanStep.Lesson("grammar-03")) >
                plan.steps.indexOfLast { it is PlanStep.NewWord },
        )
    }

    @Test
    fun aMetWordThatBringsAGrammarLessonKeepsItPending() {
        val cards = (1..25).map { wordCard(it, due = Long.MAX_VALUE) }

        val plan = plan(snapshotOf(cards, doneLessons = setOf("basics-01")))

        assertTrue(PlanStep.Lesson("grammar-03") in plan.steps)
    }

    @Test
    fun aDoneGrammarLessonIsNotPlannedAgain() {
        val cards = (1..25).map { wordCard(it, due = Long.MAX_VALUE) }

        val plan = plan(snapshotOf(cards, doneLessons = setOf("basics-01", "grammar-03")))

        assertTrue(PlanStep.Lesson("grammar-03") !in plan.steps)
    }
}
