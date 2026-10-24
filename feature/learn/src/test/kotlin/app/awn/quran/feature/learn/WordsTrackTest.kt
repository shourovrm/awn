package app.awn.quran.feature.learn

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.downloads.PackWorkScheduler
import app.awn.quran.core.data.downloads.WantedPackStore
import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fakes.FakeTranslationRepository
import app.awn.quran.core.quranfont.LocalQuranPrint
import app.awn.quran.core.quranfont.QuranPrint
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.course.CourseContentReader
import app.awn.quran.feature.learn.course.LessonCourse
import app.awn.quran.feature.learn.ui.LearnPageLinks
import app.awn.quran.feature.learn.words.WordFormsAsset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Quran words page opened from the hub: it lists word sets, today's ten words and the pages the
 * user reads, and none of the Reading or Basics lessons that the path used to put first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordsTrackTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val noWantedPacks =
        object : WantedPackStore {
            override fun ids(): Set<String> = emptySet()

            override fun add(id: String) = Unit

            override fun remove(id: String) = Unit
        }

    private val noScheduler =
        object : PackWorkScheduler {
            override val transfers = MutableStateFlow(emptyMap<String, PackTransfer>())

            override fun enqueue(id: String) = Unit

            override fun cancel(id: String) = Unit

            override fun pruneFinished() = Unit
        }

    /** A Quran whose first ten word forms read "word1" to "word10" and sit on page 7. */
    private fun quranWithFirstSet(): FakeQuranTextRepository {
        val forms = WordFormsAsset.read(context.assets)
        val firstSet = (1..SET_SIZE).map { rank -> rank to forms.representative(rank) }
        val fake = FakeQuranTextRepository()
        firstSet
            .groupBy { (_, key) -> AyahRef(key.surah, key.ayah) }
            .forEach { (ayah, entries) ->
                fake.wordsByAyah[ayah] = entries.map { (rank, key) ->
                    Word(key, "word$rank", null, null)
                }
                fake.pageByAyah[ayah] = PAGE
            }
        return fake
    }

    private fun showWordsPage(links: LearnPageLinks = LearnPageLinks()) {
        val services = LearnServices.get(context)
        runBlocking {
            services.settings.setIncludeLetters(true)
            services.settings.setCourseStarted()
        }
        val quranText = quranWithFirstSet()
        composeRule.setContent {
            AwnTheme {
                CompositionLocalProvider(
                    LocalQuranPrint provides QuranPrint(quranText = quranText),
                ) {
                    LearnScreen(
                        quranText = quranText,
                        translations = FakeTranslationRepository(),
                        packs = FakePackRepository(),
                        downloads = PackDownloads(noWantedPacks, noScheduler),
                        pageLinks = links,
                    )
                }
            }
        }
        waitForText("Quran words")
        composeRule.onNodeWithText("Quran words").performScrollTo().performClick()
    }

    @Test
    fun theWordsPageShowsTodaysSetAndNoReadingOrBasicsLessons() {
        showWordsPage()

        waitForText("Sets")
        waitForText("word1")
        waitForText("word10")
        val content = CourseContentReader.read(context.assets)
        val startLessonTitles =
            (content.inCourse(LessonCourse.Basics) + content.inCourse(LessonCourse.Letters)).map {
                it.text("en").title
            }
        startLessonTitles.forEach { title ->
            assertTrue(
                "The words page lists the start lesson $title.",
                composeRule
                    .onAllNodes(hasText(title, substring = true))
                    .fetchSemanticsNodes()
                    .isEmpty(),
            )
        }
    }

    @Test
    fun aPageTheUserReadsOpensItsWords() {
        showWordsPage(LearnPageLinks(lastReadPage = PAGE))

        waitForText("Last read · page $PAGE")
        composeRule.onNodeWithText("Last read · page $PAGE").performScrollTo().performClick()

        waitForText("Words on page $PAGE")
        waitForText("All $SET_SIZE")
    }

    @Test
    fun withoutAHifzPlanThereIsNoHifzRow() {
        showWordsPage(LearnPageLinks(lastReadPage = PAGE))

        waitForText("Last read · page $PAGE")
        assertTrue(
            composeRule
                .onAllNodes(hasText("Hifz today", substring = true))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(
            conditionDescription = "text '$text' shown",
            timeoutMillis = WAIT_MILLIS,
        ) {
            composeRule
                .onAllNodes(hasText(text, substring = false))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val WAIT_MILLIS = 20_000L
        const val SET_SIZE = 10
        const val PAGE = 7
    }
}
