package app.awn.quran.feature.learn.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.quranfont.LocalQuranPrint
import app.awn.quran.core.quranfont.QuranPrint
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.Snippet
import app.awn.quran.feature.learn.content.SnippetSource
import app.awn.quran.feature.learn.session.ItemRef
import app.awn.quran.feature.learn.session.PlanStep
import app.awn.quran.feature.learn.session.TodayPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Compose tests run on Android 15: Espresso calls an InputManager method that Android 16 removed.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DailySayingTest {

    @get:Rule val composeRule = createComposeRule()

    private val saying =
        DailySaying(
            arabic = "إِنَّ اللَّهَ يَرْفَعُ بِهَذَا الْكِتَابِ أَقْوَامًا",
            translation = "Allah raises some people by this Book.",
            source = "Sahih Muslim 817",
        )

    private val noHubActions =
        HubActions(
            onStart = {},
            onProgress = {},
            onWordList = {},
            onQuiz = {},
            onTrack = {},
            onReadingProgress = {},
            onReviseAyahs = {},
            onSessionMinutes = {},
            onSources = {},
            onDownloadMeanings = {},
            onLesson = {},
            onChangeChoices = {},
        )

    private val hubState =
        HubState(
            plan =
                TodayPlan(
                    steps =
                        List(10) { PlanStep.NewWord(rank = it + 1) } +
                            List(3) { PlanStep.Review(ItemRef.Word(rank = 100 + it)) },
                    plannedSeconds = 600,
                ),
            sessionMinutes = 10,
            path = emptyList(),
            lessonTitles = emptyMap(),
            triggerMeanings = emptyMap(),
            wordsMet = 0,
            wordsKnown = 0,
            coverage = 0.0,
            dueAyahs = 0,
            meanings = MeaningsState.Ready,
            tracks = emptyList(),
        )

    private val otherSaying =
        DailySaying(
            arabic = "اقْرَءُوا الْقُرْآنَ",
            translation = "Read the Quran.",
            source = "Sahih Muslim 804",
        )

    private fun showHub(withSaying: DailySaying?) {
        showDeck(withSaying?.let { SayingDeck(listOf(it), dayIndex = 0) })
    }

    private fun showDeck(deck: SayingDeck?) {
        composeRule.setContent {
            AwnTheme(field = LearnField) { CourseHub(hubState.copy(sayings = deck), noHubActions) }
        }
    }

    private fun snippet(id: String, surah: Int) =
        Snippet(
            id = id,
            arabic = "arabic $id",
            source = SnippetSource.Ayah(surah, 1),
            textEnglish = "english $id",
            textBangla = "bangla $id",
            url = null,
            grade = null,
        )

    @Test
    fun anAyahSayingIsDrawnFromTheQuranTextInThePrintWithItsEndMarker() {
        val ayah = AyahRef(73, 4)
        val quranText =
            FakeQuranTextRepository(
                wordsByAyah =
                    mutableMapOf(
                        ayah to listOf(Word(WordKey(73, 4, 1), "from the print", null, null)),
                    ),
            )
        val ayahSaying = saying.copy(arabic = "snippet text", ayah = ayah)
        composeRule.setContent {
            AwnTheme(field = LearnField) {
                CompositionLocalProvider(
                    LocalQuranPrint provides QuranPrint(quranText = quranText),
                ) {
                    CourseHub(
                        hubState.copy(sayings = SayingDeck(listOf(ayahSaying), dayIndex = 0)),
                        noHubActions,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("from the print").assertIsDisplayed()
        composeRule.onNodeWithText("٤").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("snippet text").fetchSemanticsNodes().size)
    }

    @Test
    fun theSayingShowsAboveTheTodayCard() {
        showHub(saying)

        composeRule.onNodeWithText(saying.arabic).assertIsDisplayed()
        composeRule.onNodeWithText(saying.translation).assertIsDisplayed()
        composeRule.onNodeWithText(saying.source).assertIsDisplayed()
        val sayingTop = composeRule.onNodeWithText(saying.arabic).getBoundsInRoot().top
        val todayTop = composeRule.onNodeWithText("Today · 10 min").getBoundsInRoot().top
        assertTrue("saying $sayingTop above today $todayTop", sayingTop < todayTop)
    }

    @Test
    fun withoutASayingTheHubShowsNoCardAndNoShareButton() {
        showHub(null)

        composeRule.onNodeWithText("Today · 10 min").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Share").assertDoesNotExist()
    }

    @Test
    fun theShareButtonOpensTheShareSheetWithArabicTranslationAndSource() {
        showHub(saying)

        composeRule.onNodeWithContentDescription("Share").performClick()

        val application = ApplicationProvider.getApplicationContext<Application>()
        val chooser = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val sent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, sent?.action)
        assertEquals(saying.shareText, sent?.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(
            "${saying.arabic}\n${saying.translation}\n${saying.source}",
            saying.shareText,
        )
    }

    @Test
    fun aTapOnTheSayingShowsAnotherOne() {
        showDeck(SayingDeck(listOf(saying, otherSaying), dayIndex = 0))

        composeRule.onNodeWithText(saying.arabic).performClick()

        composeRule.onNodeWithText(otherSaying.arabic).assertIsDisplayed()
        composeRule.onNodeWithText(saying.arabic).assertDoesNotExist()
    }

    @Test
    fun theSayingChangesByItselfAfterHalfAnHour() {
        composeRule.mainClock.autoAdvance = false
        showDeck(SayingDeck(listOf(saying, otherSaying), dayIndex = 0))
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText(saying.arabic).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(HALF_AN_HOUR_MILLIS)

        composeRule.onNodeWithText(otherSaying.arabic).assertIsDisplayed()
    }

    @Test
    fun theDeckStartsOnTheDaysSnippetAndTheLanguagePicksItsRendering() {
        val snippets = listOf(snippet("a", 1), snippet("b", 2), snippet("c", 3))

        val englishDeck = sayingDeckOf(snippets, epochDay = 4, LearnLanguage.English) { "x" }
        val banglaDeck = sayingDeckOf(snippets, epochDay = 4, LearnLanguage.Bangla) { "নাম $it" }
        val english = englishDeck?.sayings?.get(englishDeck.dayIndex)
        val bangla = banglaDeck?.sayings?.get(banglaDeck.dayIndex)

        assertEquals("arabic b", english?.arabic)
        assertEquals("english b", english?.translation)
        assertEquals("Quran 2:1", english?.source)
        assertEquals("bangla b", bangla?.translation)
        assertTrue(bangla?.source?.contains("নাম 2") == true)
    }

    @Test
    fun noSnippetsGiveNoSaying() {
        assertNull(sayingDeckOf(emptyList(), epochDay = 4, LearnLanguage.English) { "x" })
    }

    private companion object {
        const val HALF_AN_HOUR_MILLIS = 30L * 60L * 1000L
    }
}
