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
        assertTrue(pages[0] is UnitPage.Teaching)
        assertTrue(pages[1] is UnitPage.Teaching)
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

    private val unitWithGridsAndPractice =
        CourseContentReader.parse(
                """{"formatVersion":1,"references":[],"lessons":[{"id":"letters-04",
            "course":"letters","order":4,"introducedBy":[],"licence":"x","reviewed":false,
            "languages":{"en":{"title":"T","screens":[
            {"kind":"screen","title":"Fatha","blocks":[]},
            {"kind":"grid","title":"Fatha","cells":[
              {"arabic":"بَ","transliteration":"ba","clip":"qaida-04/012"},
              {"arabic":"بلب","transliteration":"bā lām bā","clip":"qaida-01/002+qaida-01/023"}]},
            {"kind":"grid","title":"More","cells":[
              {"arabic":"تَ","transliteration":"ta","clip":"qaida-04/013"}]},
            {"kind":"grid","title":"Alone","cells":[
              {"arabic":"ثَ","transliteration":"tha","clip":"qaida-04/014"}]},
            {"kind":"practice","title":"P","check":{"question":"q","options":["a","b"],
              "answer":0,"clip":"qaida-04/012+qaida-04/013","explain":"why"}}]}}}]}""",
            )
            .lessons
            .single()
            .text("en")

    @Test
    fun `a grid joins the reading screen before it and the practice page comes after the checks`() {
        val pages = unitPages(unitWithGridsAndPractice)

        val teaching = pages.filterIsInstance<UnitPage.Teaching>().single()
        assertEquals(listOf("Fatha", "Fatha", "More", "Alone"), teaching.screens.map { it.title })
        assertEquals(listOf(UnitPage.Practice, UnitPage.Done), pages.takeLast(2))
    }

    @Test
    fun `a grid with no reading screen before it is a page of its own`() {
        val gridFirst =
            CourseContentReader.parse(
                    """{"formatVersion":1,"references":[],"lessons":[{"id":"letters-04",
            "course":"letters","order":4,"introducedBy":[],"licence":"x","reviewed":false,
            "languages":{"en":{"title":"T","screens":[
            {"kind":"grid","title":"G","cells":[
              {"arabic":"بَ","transliteration":"ba","clip":"qaida-04/012"}]}]}}}]}""",
                )
                .lessons
                .single()
                .text("en")

        assertEquals(2, unitPages(gridFirst).size)
        assertTrue(unitPages(gridFirst)[0] is UnitPage.Teaching)
    }

    @Test
    fun `cells and practice items split their joined sound ids`() {
        val cell = unitWithGridsAndPractice.screens[1].cells[1]

        assertEquals(listOf("qaida-01/002", "qaida-01/023"), cell.clipIds)
        assertEquals(
            listOf("qaida-04/012", "qaida-04/013"),
            unitWithGridsAndPractice.practiceItems.single().clipIds,
        )
        val question = unitWithGridsAndPractice.practiceItems.single() as PracticeItem.Question
        assertEquals("why", question.check.explanation)
    }

    @Test
    fun `a practice screen with a read line is a read-aloud item`() {
        val text =
            CourseContentReader.parse(
                    """{"formatVersion":1,"references":[],"lessons":[{"id":"letters-04",
            "course":"letters","order":4,"introducedBy":[],"licence":"x","reviewed":false,
            "languages":{"en":{"title":"T","screens":[
            {"kind":"practice","title":"R","read_aloud":{"read":"[[حَ]] [[خَ]]",
              "clip":"qaida-04/012+qaida-04/013"}}]}}}]}""",
                )
                .lessons
                .single()
                .text("en")

        val item = text.practiceItems.single() as PracticeItem.ReadAloud

        assertEquals("[[حَ]] [[خَ]]", item.read)
        assertEquals(listOf("qaida-04/012", "qaida-04/013"), item.clipIds)
        assertTrue(text.hasClips)
    }

    @Test
    fun `practice items are not checks`() {
        assertTrue(unitWithGridsAndPractice.checks.isEmpty())
        assertEquals(null, unitWithGridsAndPractice.check)
    }
}
