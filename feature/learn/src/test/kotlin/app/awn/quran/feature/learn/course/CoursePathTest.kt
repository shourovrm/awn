package app.awn.quran.feature.learn.course

import app.awn.quran.feature.learn.session.FakeFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoursePathTest {

    private fun path(
        met: Set<Int> = emptySet(),
        known: Set<Int> = emptySet(),
        done: Set<String> = emptySet(),
        letters: Boolean = false,
    ) =
        CoursePath.build(
            PathInput(
                content = fixtureContent(),
                frequency = FakeFrequency(formCount = 200),
                metRanks = met,
                knownRanks = known,
                doneLessonIds = done,
                includeLetters = letters,
                plan = null,
            ),
        )

    @Test
    fun aNewUserSeesTheBasicsLessonsFirstThenSetsAndLater() {
        val rows = path()

        assertEquals("basics-01", (rows[0] as PathRow.LessonStep).lessonId)
        assertEquals(PathState.Next, (rows[0] as PathRow.LessonStep).state)
        assertEquals("basics-02", (rows[1] as PathRow.LessonStep).lessonId)
        assertEquals(1, (rows[2] as PathRow.SetStep).number)
        assertTrue(rows.last() is PathRow.Later)
    }

    @Test
    fun theGrammarLessonSitsRightAfterTheSetThatBringsIt() {
        val rows = path(met = (1..20).toSet(), done = setOf("basics-01", "basics-02"))

        val setThree = rows.indexOfFirst { it is PathRow.SetStep && it.number == 3 }
        val lesson = rows[setThree + 1] as PathRow.LessonStep
        assertEquals("grammar-03", lesson.lessonId)
        assertEquals(25, lesson.triggerRank)
        assertEquals(3, lesson.triggerSet)
    }

    @Test
    fun longDoneStepsFoldIntoOneRowAndTheLastTwoStayVisible() {
        val rows = path(met = (1..50).toSet(), done = setOf("basics-01", "basics-02", "grammar-03"))

        val folded = rows.first() as PathRow.Folded
        assertEquals(1..3, folded.sets)
        assertEquals(listOf("basics-01", "basics-02", "grammar-03"), folded.lessonIds)
        val visibleDone = rows.filter { it is PathRow.SetStep && it.state == PathState.Done }
        assertEquals(listOf(4, 5), visibleDone.map { (it as PathRow.SetStep).number })
    }

    @Test
    fun exactlyOneStepIsMarkedNext() {
        val rows = path(met = (1..12).toSet(), done = setOf("basics-01", "basics-02"))

        val next = rows.filter {
            (it as? PathRow.SetStep)?.state == PathState.Next ||
                (it as? PathRow.LessonStep)?.state == PathState.Next
        }
        assertEquals(1, next.size)
        assertEquals(2, (next.single() as PathRow.SetStep).number)
    }

    @Test
    fun aSetShowsHowManyOfItsMetWordsAreKnown() {
        val rows =
            path(
                met = (1..10).toSet(),
                known = (1..7).toSet(),
                done = setOf("basics-01", "basics-02"),
            )

        val setOne = rows.filterIsInstance<PathRow.SetStep>().first { it.number == 1 }
        assertEquals(10, setOne.metCount)
        assertEquals(7, setOne.knownCount)
    }

    @Test
    fun laterStartsAfterTheLastListedSet() {
        val rows = path(met = (1..10).toSet(), done = setOf("basics-01", "basics-02"))

        val lastSet = rows.filterIsInstance<PathRow.SetStep>().maxOf { it.number }
        assertEquals(lastSet + 1, (rows.last() as PathRow.Later).fromSet)
    }

    private fun allSets(
        met: Set<Int> = emptySet(),
        formCount: Int = 200,
        done: Set<String> = emptySet(),
    ) =
        CoursePath.buildAllSets(
            PathInput(
                content = fixtureContent(),
                frequency = FakeFrequency(formCount = formCount),
                metRanks = met,
                knownRanks = emptySet(),
                doneLessonIds = done,
                includeLetters = false,
                plan = null,
                includeStartLessons = false,
            ),
        )

    @Test
    fun theWholeCourseListsEverySetWithoutFoldingOrLater() {
        val rows = allSets(met = (1..50).toSet(), done = setOf("grammar-03"))

        val sets = rows.filterIsInstance<PathRow.SetStep>()
        assertEquals((1..20).toList(), sets.map { it.number })
        assertTrue(rows.none { it is PathRow.Folded || it is PathRow.Later })
        assertEquals(PathState.Done, sets.first().state)
        assertEquals(PathState.Next, sets.first { it.number == 6 }.state)
        assertEquals(PathState.Upcoming, sets.first { it.number == 7 }.state)
    }

    @Test
    fun theWholeCourseStopsAtTheLastSetOfTheLastLevel() {
        val rows = allSets(formCount = 9_000)

        assertEquals(500, rows.filterIsInstance<PathRow.SetStep>().maxOf { it.number })
    }

    @Test
    fun theWholeCourseKeepsTheGrammarLessonAfterItsSet() {
        val rows = allSets(met = (1..20).toSet())

        val setThree = rows.indexOfFirst { it is PathRow.SetStep && it.number == 3 }
        assertEquals("grammar-03", (rows[setThree + 1] as PathRow.LessonStep).lessonId)
    }
}
