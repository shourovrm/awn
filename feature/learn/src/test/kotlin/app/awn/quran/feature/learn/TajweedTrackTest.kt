package app.awn.quran.feature.learn

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.downloads.PackWorkScheduler
import app.awn.quran.core.data.downloads.WantedPackStore
import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fakes.FakeTranslationRepository
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.course.CourseContentReader
import app.awn.quran.feature.learn.course.LessonCourse
import app.awn.quran.feature.learn.course.unitPages
import app.awn.quran.feature.learn.data.StepItemKind
import app.awn.quran.feature.learn.data.StepOutcome
import app.awn.quran.feature.learn.session.StepKind
import app.awn.quran.feature.learn.words.FinishedStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The whole path a user takes in the Tajweed track: hub, Tajweed row, a unit, back to the track.
 * Every unit must open on its first page, and leaving it by the bar arrow or by the system back
 * gesture must return to the track.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TajweedTrackTest {

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

    private fun showLearnTab() {
        val services = LearnServices.get(context)
        // The user who picked "Not yet" for the letters is the one who found the track closed.
        runBlocking {
            services.settings.setIncludeLetters(true)
            services.settings.setCourseStarted()
        }
        composeRule.setContent {
            AwnTheme {
                LearnScreen(
                    quranText = FakeQuranTextRepository(),
                    translations = FakeTranslationRepository(),
                    packs = FakePackRepository(),
                    downloads = PackDownloads(noWantedPacks, noScheduler),
                )
            }
        }
    }

    @Test
    fun everyTajweedUnitOpensOnItsFirstPageAndBackReturnsToTheTrack() {
        showLearnTab()
        waitForText("Tajweed")
        composeRule.onNodeWithText("Tajweed").performScrollTo().performClick()

        val units = CourseContentReader.read(context.assets).inCourse(LessonCourse.Tajweed)
        assertEquals(9, units.size)
        units.forEachIndexed { index, unit ->
            val title = unit.text("en").title
            val rowText = "${index + 1} · $title"
            waitForText(rowText)
            composeRule.onNodeWithText(rowText).performScrollTo().performClick()
            val pageCount = unitPages(unit.text("en")).size
            waitForText("1 of $pageCount")
            if (index % 2 == 0) {
                composeRule.onNodeWithContentDescription("Back").performClick()
            } else {
                composeRule.runOnUiThread {
                    composeRule.activity.onBackPressedDispatcher.onBackPressed()
                }
            }
            // The numbered row is on the track page only, so it shows once the unit is left.
            waitForText(rowText)
        }
    }

    @Test
    fun anOlderLessonThatTheUserFinishedShowsAsADoneUnit() {
        val services = LearnServices.get(context)
        runBlocking {
            val olderLesson =
                FinishedStep(
                    StepKind.Lesson,
                    StepItemKind.LESSON,
                    "ghunnah",
                    null,
                    StepOutcome.SEEN,
                    0L,
                )
            services.courseRepository.finishLesson("ghunnah", olderLesson)
        }

        val snapshot = runBlocking { services.courseRepository.snapshot() }

        assertTrue("tajweed-15-nasal-sound" in snapshot.doneLessonIds)
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
    }
}
