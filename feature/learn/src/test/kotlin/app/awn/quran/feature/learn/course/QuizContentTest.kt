package app.awn.quran.feature.learn.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The lesson reader parses with org.json, which only Robolectric provides on the JVM.
@RunWith(RobolectricTestRunner::class)
class QuizContentTest {

    private val text =
        CourseContentReader.parse(
                """{"formatVersion":1,"references":[],"lessons":[{"id":"letters-01",
            "course":"letters","order":1,"introducedBy":[],"licence":"x","reviewed":false,
            "languages":{"en":{"title":"T","screens":[
            {"kind":"screen","title":"Letters","blocks":[
              {"type":"groups","items":[{"letters":["ب","ت","ث"],"caption":"Group 2"}]},
              {"type":"pairs","items":[{"letters":["ت","ط"],"caption":"tā · ṭā","clip":"qaida-01/003+qaida-01/016"},
                {"letters":["س","ش"],"caption":"sīn · shīn","clip":null}]},
              {"type":"reveal","items":[{"letter":"ث","name":"thā"}]}]},
            {"kind":"quiz","title":"Q1","questions":[
              {"type":"pick","question":"Which?","options":["[[ت]]","[[ب]]"],"answer":1,"explain":"e1"},
              {"type":"listen","clip":"qaida-01/005","options":["[[ج]]","[[ح]]"],"answer":0,"explain":"e2"},
              {"type":"name","show":"[[ع]]","options":["ʿayn","ghayn"],"answer":0,"explain":"e3"}]},
            {"kind":"screen","title":"Shapes","blocks":[]},
            {"kind":"grid","title":"G","cells":[{"arabic":"بَ","transliteration":"ba","clip":"qaida-04/012"}]},
            {"kind":"quiz","title":"Q2","questions":[
              {"type":"order","question":"Order","items":["[[ا]]","[[ب]]","[[ج]]"],"explain":"e4"},
              {"type":"sort","question":"Sort","groups":[{"label":"Like [[ب]]","items":["[[ب]]","[[ت]]"]},
                {"label":"Like [[ح]]","items":["[[ح]]","[[ج]]"]}],"explain":"e5"},
              {"type":"match","question":"Match","pairs":[{"left":"[[ب]]","right":"One below"},
                {"left":"[[ت]]","right":"Two above"}],"explain":"e6"}]},
            {"kind":"quiz","title":"Q3","questions":[
              {"type":"find","question":"Find","items":["[[ت]]","[[ط]]","[[ص]]"],"answers":["[[ط]]","[[ص]]"],"explain":"e7"}]},
            {"kind":"check","title":"C1","check":{"question":"q1","options":["a","b"],"answer":1}},
            {"kind":"answer","title":"A1","blocks":[]}]}}}]}""",
            )
            .lessons
            .single()
            .text("en")

    private val quizzes = text.screens.filter { it.kind == ScreenKind.Quiz }

    @Test
    fun `a quiz screen keeps its questions in order`() {
        assertEquals(3, quizzes.size)
        val kinds = quizzes[0].questions.map { it::class.simpleName }
        assertEquals(listOf("Pick", "Listen", "Name"), kinds)
    }

    @Test
    fun `pick, listen and name keep options, the right option and the explanation`() {
        val (pick, listen, name) = quizzes[0].questions
        pick as QuizQuestion.Pick
        listen as QuizQuestion.Listen
        name as QuizQuestion.Name

        assertEquals("Which?", pick.question)
        assertEquals(listOf("[[ت]]", "[[ب]]"), pick.options)
        assertEquals(1, pick.correctIndex)
        assertEquals("e1", pick.explanation)
        assertEquals(listOf("qaida-01/005"), listen.clipIds)
        assertEquals("[[ع]]", name.shown)
        assertEquals(0, name.correctIndex)
    }

    @Test
    fun `order, sort, match and find keep their items`() {
        val (order, sort, match) = quizzes[1].questions
        order as QuizQuestion.Order
        sort as QuizQuestion.Sort
        match as QuizQuestion.Match
        val find = quizzes[2].questions.single() as QuizQuestion.Find

        assertEquals(listOf("[[ا]]", "[[ب]]", "[[ج]]"), order.items)
        assertEquals(listOf("Like [[ب]]", "Like [[ح]]"), sort.groups.map { it.label })
        assertEquals(listOf("[[ح]]", "[[ج]]"), sort.groups[1].items)
        assertEquals(QuizPair("[[ت]]", "Two above"), match.pairs[1])
        assertEquals(listOf("[[ط]]", "[[ص]]"), find.answers)
        assertEquals("e7", find.explanation)
    }

    @Test
    fun `the letter blocks keep their letters, captions and clips`() {
        val blocks = text.screens.first().blocks
        val groups = blocks[0] as LessonBlock.Groups
        val pairs = blocks[1] as LessonBlock.Pairs
        val reveal = blocks[2] as LessonBlock.Reveal

        assertEquals(listOf("ب", "ت", "ث"), groups.items.single().letters)
        assertEquals("Group 2", groups.items.single().caption)
        assertEquals(listOf("qaida-01/003", "qaida-01/016"), pairs.items[0].clipIds)
        assertNull(pairs.items[1].clip)
        assertEquals(LessonBlock.LetterName("ث", "thā"), reveal.items.single())
    }

    @Test
    fun `each quiz is its own page right after the teaching page before it`() {
        val pages = unitPages(text)

        val shape =
            pages.map { page ->
                when (page) {
                    is UnitPage.Teaching -> "teach:" + page.screens.joinToString("+") { it.title }
                    is UnitPage.Quiz -> "quiz:" + page.screen.title
                    is UnitPage.Check -> "check"
                    UnitPage.Practice -> "practice"
                    UnitPage.Done -> "done"
                }
            }
        assertEquals(
            listOf("teach:Letters", "quiz:Q1", "teach:Shapes+G", "quiz:Q2", "quiz:Q3", "check", "done"),
            shape,
        )
    }

    @Test
    fun `listen questions and pairs count as sounds of the unit`() {
        assertTrue(text.hasClips)
        val silent = text.copy(screens = text.screens.filter { it.kind == ScreenKind.Reading && it.title == "Shapes" })
        assertFalse(silent.hasClips)
    }
}
