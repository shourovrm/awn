package app.awn.quran.feature.learn.course

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelSectionsTest {

    private fun setRow(number: Int) =
        PathRow.SetStep(number, metCount = 0, knownCount = 0, PathState.Upcoming, isToday = false)

    private fun grammarRow(lessonId: String, triggerSet: Int) =
        PathRow.LessonStep(lessonId, triggerRank = null, triggerSet, PathState.Upcoming, false)

    private val rows: List<PathRow> =
        (1..25).flatMap { number ->
            if (number == 10) listOf(setRow(number), grammarRow("grammar-a", 10))
            else listOf(setRow(number))
        }

    @Test
    fun rowsGoUnderTheLevelOfTheirSet() {
        val sections = LevelSections.group(rows, currentSet = 1)

        assertEquals(listOf(1, 2, 3), sections.map { it.level.number })
        assertEquals(11, sections[0].rows.size)
        assertEquals(10, sections[1].rows.size)
        assertEquals(5, sections[2].rows.size)
    }

    @Test
    fun aGrammarLineStaysRightAfterItsSetInsideTheLevel() {
        val firstLevel = LevelSections.group(rows, currentSet = 1).first()

        assertEquals("grammar-a", (firstLevel.rows.last() as PathRow.LessonStep).lessonId)
        assertEquals(10, (firstLevel.rows[firstLevel.rows.size - 2] as PathRow.SetStep).number)
    }

    @Test
    fun theLevelOfTheCurrentSetIsOpenAndTheOthersAreFolded() {
        val sections = LevelSections.group(rows, currentSet = 12)

        assertEquals(
            listOf(LevelState.Finished, LevelState.Current, LevelState.Later),
            sections.map { it.state },
        )
    }

    @Test
    fun theLastSetOfALevelStillBelongsToThatLevel() {
        val sections = LevelSections.group(rows, currentSet = 10)

        assertEquals(LevelState.Current, sections[0].state)
        assertEquals(LevelState.Later, sections[1].state)
    }

    @Test
    fun aCurrentSetPastTheLastLevelFinishesEveryLevel() {
        val sections = LevelSections.group(rows, currentSet = 501)

        assertEquals(setOf(LevelState.Finished), sections.map { it.state }.toSet())
    }
}
