package app.awn.quran.feature.learn

import androidx.compose.ui.test.hasText
import androidx.activity.ComponentActivity
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
import app.awn.quran.feature.learn.content.LessonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The whole path a user takes in the Tajweed track: hub, Tajweed row, a unit, back to the track.
 * The unit must open with its text, and leaving it by the bar arrow or by the system back gesture
 * must mark it done.
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

    @Test
    fun everyTajweedUnitOpensWithItsTextAndLeavingItMarksItDone() {
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
        waitForText("Tajweed")
        composeRule.onNodeWithText("Tajweed").performScrollTo().performClick()

        val lessons = LessonRepository(context.assets).all()
        val openedTitles = mutableListOf<String>()
        LearnTrackOrder.tajweedIds.forEachIndexed { index, lessonId ->
            val title = lessons.first { it.id == lessonId }.titleEnglish
            val rowText = "${index + 1} · $title"
            waitForText(rowText)
            composeRule.onNodeWithText(rowText).performScrollTo().performClick()
            // The lesson screen shows its title in the bar and again above the text.
            waitForText("Examples")
            openedTitles += title
            if (index % 2 == 0) {
                composeRule.onNodeWithContentDescription("Back").performClick()
            } else {
                composeRule.runOnUiThread {
                    composeRule.activity.onBackPressedDispatcher.onBackPressed()
                }
            }
            // The numbered row is on the track page only, so it shows once the lesson is left.
            waitForText(rowText)
            waitForDone(services, lessonId)
        }
        assertTrue(openedTitles.size == LearnTrackOrder.tajweedIds.size)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(conditionDescription = "text '$text' shown", timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodes(hasText(text, substring = false)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForDone(services: LearnServices, lessonId: String) {
        composeRule.waitUntil(conditionDescription = "$lessonId marked done", timeoutMillis = WAIT_MILLIS) {
            runBlocking(Dispatchers.IO) { lessonId in services.courseRepository.observeSnapshot().first().doneLessonIds }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 20_000L
    }
}

private object LearnTrackOrder {
    val tajweedIds = app.awn.quran.feature.learn.course.LearnTracks.TAJWEED_ORDER
}
