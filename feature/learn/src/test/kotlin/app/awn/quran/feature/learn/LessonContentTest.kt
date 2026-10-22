package app.awn.quran.feature.learn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.feature.learn.content.SnippetRepository
import app.awn.quran.feature.learn.course.CourseContentReader
import app.awn.quran.feature.learn.course.LessonBlock
import app.awn.quran.feature.learn.course.LessonCourse
import app.awn.quran.feature.learn.course.LessonText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LessonContentTest {

    private val assets = ApplicationProvider.getApplicationContext<Context>().assets

    /**
     * The live feedback judges skipped words, substituted letters and madd length only, so no
     * tajweed unit may say that the app checks or detects anything else.
     */
    @Test
    fun noTajweedUnitClaimsThatTheAppChecksIt() {
        val forbidden = listOf("app ", "detect", "checks your", "অ্যাপ", "যাচাই করে")
        val units = CourseContentReader.read(assets).inCourse(LessonCourse.Tajweed)

        assertEquals(9, units.size)
        units.forEach { unit ->
            listOf("en", "bn").forEach { languageCode ->
                val text = textOf(unit.text(languageCode))
                forbidden.forEach { phrase ->
                    assertTrue(
                        "${unit.id} ($languageCode) contains '$phrase'",
                        !text.contains(phrase, ignoreCase = true),
                    )
                }
            }
        }
    }

    private fun textOf(lessonText: LessonText): String =
        lessonText.screens.joinToString(" ") { screen ->
            val blockText = screen.blocks.joinToString(" ") { block -> textOf(block) }
            val checkText =
                screen.check?.let { check ->
                    check.question + " " + check.options.joinToString(" ") + check.explanation
                }
            screen.title + " " + blockText + " " + checkText.orEmpty()
        }

    private fun textOf(block: LessonBlock): String =
        when (block) {
            is LessonBlock.Paragraph -> block.text
            is LessonBlock.Bullets -> block.items.joinToString(" ")
            is LessonBlock.Table -> (block.header + block.rows.flatten()).joinToString(" ")
            is LessonBlock.Equation -> block.parts.joinToString(" ") { it.text + it.gloss }
            is LessonBlock.Ayah,
            is LessonBlock.Letters -> ""
        }

    @Test
    fun sixtySnippetsLoadWithReferences() {
        val snippets = SnippetRepository(assets).all()

        assertEquals(60, snippets.size)
        assertEquals(snippets.size, snippets.map { it.id }.toSet().size)
        snippets.forEach { snippet ->
            assertTrue(snippet.arabic.isNotBlank())
            assertTrue(snippet.reference.isNotBlank())
            assertTrue(snippet.textEnglish.isNotBlank())
            assertTrue(snippet.textBangla.isNotBlank())
            if (snippet.url != null) assertEquals("sahih", snippet.grade)
        }
    }

    @Test
    fun theDaySelectsOneSnippetAndConsecutiveDaysDiffer() {
        val snippets = SnippetRepository(assets).all()

        val today = SnippetRepository.forDay(snippets, 20_000)
        val tomorrow = SnippetRepository.forDay(snippets, 20_001)

        assertNotNull(today)
        assertTrue(today != tomorrow)
        assertNull(SnippetRepository.forDay(emptyList(), 20_000))
    }
}
