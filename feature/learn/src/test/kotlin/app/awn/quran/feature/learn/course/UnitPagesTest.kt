package app.awn.quran.feature.learn.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The lesson reader parses with org.json, which only Robolectric provides on the JVM.
@RunWith(RobolectricTestRunner::class)
class UnitPagesTest {

    private val text =
        CourseContentReader.parse(
                """{"formatVersion":1,"references":[],"lessons":[{"id":"letters-01",
            "course":"letters","order":1,"introducedBy":[],"licence":"x","reviewed":false,
            "languages":{"en":{"title":"T","screens":[
            {"kind":"screen","title":"One","blocks":[{"type":"letters","items":[
              {"letter":"ا","name":"alif"},{"letter":"ب","name":"baa"}]}]},
            {"kind":"screen","title":"Two","blocks":[]},
            {"kind":"check","title":"C1","check":{"question":"q1","options":["a","b"],"answer":1}},
            {"kind":"answer","title":"A1","blocks":[{"type":"paragraph","text":"y1"}]},
            {"kind":"check","title":"C2","check":{"question":"q2","options":["a","b","c"],"answer":0}},
            {"kind":"answer","title":"A2","blocks":[]}]}}}]}""",
            )
            .lessons
            .single()
            .text("en")

    @Test
    fun `pages are the reading screens, every check, then the closing page`() {
        val pages = unitPages(text)

        assertEquals(5, pages.size)
        assertTrue(pages[0] is UnitPage.Reading)
        assertTrue(pages[1] is UnitPage.Reading)
        assertEquals(listOf(0, 1), pages.filterIsInstance<UnitPage.Check>().map { it.index })
        assertEquals(UnitPage.Done, pages.last())
    }

    @Test
    fun `each check keeps the explanation that follows it`() {
        val checks = text.checks

        assertEquals(listOf("A1", "A2"), checks.map { it.answerTitle })
        assertEquals(1, checks[0].answerBlocks.size)
        assertEquals(0, checks[1].answerBlocks.size)
    }

    @Test
    fun `the letters block keeps each letter with its name`() {
        val block = text.readingScreens.first().blocks.single() as LessonBlock.Letters

        assertEquals(listOf("alif", "baa"), block.items.map { it.name })
        assertEquals("ب", block.items[1].letter)
    }

    @Test
    fun `only the first answer to a check counts`() {
        val answers = UnitAnswers().choose(0, 1).choose(0, 0).choose(1, 2)

        assertEquals(1, answers.rightCount(text.checks))
        assertEquals(1, answers.chosen[0])
    }

    @Test
    fun `an unanswered check is not right`() {
        assertEquals(0, UnitAnswers().rightCount(text.checks))
    }
}
