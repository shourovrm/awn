package app.awn.quran.feature.learn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.LessonRepository
import app.awn.quran.feature.learn.content.SnippetRepository
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

    @Test
    fun twelveLessonsLoadAsUnreviewedDraftsWithExamples() {
        val lessons = LessonRepository(assets).all()

        assertEquals(12, lessons.size)
        lessons.forEach { lesson ->
            assertTrue("${lesson.id} is a draft", lesson.isDraft)
            assertNull(lesson.reviewedOn)
            assertTrue("${lesson.id} has examples", lesson.examples.size >= 3)
            assertTrue(lesson.paragraphs(LearnLanguage.English).size >= 3)
            assertTrue(lesson.paragraphs(LearnLanguage.Bangla).size >= 3)
        }
    }

    /**
     * The live feedback judges skipped words, substituted letters and madd length only, so no
     * lesson may say that the app checks or detects anything else.
     */
    @Test
    fun noLessonClaimsThatTheAppChecksIt() {
        val forbidden = listOf("app ", "detect", "checks your", "অ্যাপ", "যাচাই করে")

        LessonRepository(assets).all().forEach { lesson ->
            val text = lesson.bodyEnglish + lesson.bodyBangla
            forbidden.forEach { phrase ->
                assertTrue(
                    "${lesson.id} contains '$phrase'",
                    !text.contains(phrase, ignoreCase = true),
                )
            }
        }
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
