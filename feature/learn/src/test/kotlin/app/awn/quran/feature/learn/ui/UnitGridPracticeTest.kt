package app.awn.quran.feature.learn.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import app.awn.quran.feature.learn.course.CourseContentReader
import app.awn.quran.feature.learn.course.CourseLesson
import app.awn.quran.feature.learn.sound.LocalUnitSounds
import app.awn.quran.feature.learn.sound.UnitSounds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Remembers what the unit asked to play; every clip counts as played. */
private class RecordingSounds : UnitSounds {
    val clipRequests = mutableListOf<Pair<List<String>, Float>>()
    val words = mutableListOf<WordKey>()

    override suspend fun playClips(clipIds: List<String>, speed: Float): Boolean {
        clipRequests += clipIds to speed
        return true
    }

    override suspend fun playWord(word: WordKey) {
        words += word
    }

    override fun stop() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnitGridPracticeTest {

    @get:Rule val composeRule = createComposeRule()

    private val sounds = RecordingSounds()

    private fun lessonWith(practiceScreens: String): CourseLesson =
        CourseContentReader.parse(
                """{"formatVersion":1,"references":[],"lessons":[{"id":"letters-04",
            "course":"letters","order":4,"introducedBy":[],"licence":"x","reviewed":false,
            "languages":{"en":{"title":"Short vowels","screens":[
            {"kind":"screen","title":"Fatha","blocks":[{"type":"paragraph","text":"A small stroke."}]},
            {"kind":"grid","title":"Fatha","cells":[
              {"arabic":"بَ","transliteration":"ba","clip":"qaida-04/012"},
              {"arabic":"حَ","transliteration":"\u09B9\u09BC\u09BE","clip":"qaida-04/016"},
              {"arabic":"تَ","transliteration":"ta","clip":"qaida-04/013+qaida-04/014"}]},
            $practiceScreens
            ]}}}]}""",
            )
            .lessons
            .single()

    private val questionScreen =
        """{"kind":"practice","title":"P1","check":{"question":"Which one did you hear?",
              "options":["[[سَ]]","[[صَ]]"],"answer":1,"clip":"qaida-01/014","explain":"The heavy s."}}"""

    private val readAloudScreen =
        """{"kind":"practice","title":"P2","read_aloud":{"read":"[[حَ]] [[خَ]]","clip":"qaida-04/015"}}"""

    private fun show(
        practiceScreens: String = questionScreen,
        offer: SoundsOfferState = SoundsOfferState.None,
    ) {
        composeRule.setContent {
            AwnTheme(field = LearnField) {
                CompositionLocalProvider(LocalUnitSounds provides sounds) {
                    UnitScreen(
                        lesson = lessonWith(practiceScreens),
                        languageCode = "en",
                        place = null,
                        actions = UnitActions(onBack = {}, onFinished = {}, onNextUnit = {}),
                        soundsOffer = offer,
                    )
                }
            }
        }
    }

    @Test
    fun theReadingScreenAndItsGridShareOnePage() {
        show()

        composeRule.onNodeWithText("A small stroke.").assertIsDisplayed()
        assertEquals(3, composeRule.onAllNodesWithTag(GRID_CELL_TAG).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("ba").assertIsDisplayed()
        // The Bangla label keeps its combining nukta (U+09BC) as the lesson file writes it.
        composeRule.onNodeWithText("\u09B9\u09BC\u09BE").assertExists()
        composeRule.onNodeWithText("1 of 3").assertIsDisplayed()
    }

    @Test
    fun aTapOnACellPlaysItsSoundsInOrder() {
        show()

        composeRule.onAllNodesWithTag(GRID_CELL_TAG)[2].performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(listOf("qaida-04/013", "qaida-04/014") to 1f), sounds.clipRequests)
    }

    @Test
    fun playAllPlaysEveryCellInOrder() {
        show()

        composeRule.onNodeWithTag(PLAY_ALL_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                listOf("qaida-04/012"),
                listOf("qaida-04/016"),
                listOf("qaida-04/013", "qaida-04/014"),
            ),
            sounds.clipRequests.map { it.first },
        )
    }

    @Test
    fun theDownloadOfferShowsOnTheFirstPageWithItsSize() {
        var downloads = 0
        show(offer = SoundsOfferState(SoundsOffer.Download(2_000_000L)) { downloads += 1 })

        composeRule.onNodeWithTag(PLAY_ALL_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Download sounds", substring = true).performClick()

        assertEquals(1, downloads)
    }

    @Test
    fun aQuestionShowsTilesMarksTheAnswerAndPlaysItsSound() {
        show()
        composeRule.onNodeWithContentDescription("Page 2").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1 of 1").assertIsDisplayed()
        composeRule.onNodeWithText("Which one did you hear?").assertExists()
        composeRule.onNodeWithTag(PRACTICE_LISTEN_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("qaida-01/014"), sounds.clipRequests.last().first)

        sounds.clipRequests.clear()
        composeRule.onNodeWithText("\u0633\u064E", substring = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("The heavy s.").assertExists()
        assertEquals(listOf("qaida-01/014"), sounds.clipRequests.single().first)
    }

    @Test
    fun aReadAloudItemPlaysNormallyAndSlowlyAndCountsAsDone() {
        show(practiceScreens = readAloudScreen)
        composeRule.onNodeWithContentDescription("Page 2").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Read it aloud, then listen.").assertExists()
        composeRule.onNodeWithTag(PRACTICE_LISTEN_TAG).performClick()
        composeRule.onNodeWithTag(PRACTICE_SLOW_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(1f, 0.75f), sounds.clipRequests.map { it.second })

        composeRule.onNodeWithTag(PRACTICE_NEXT_TAG).performClick()

        composeRule.onNodeWithText("Practice finished").assertExists()
        composeRule.onNodeWithText("Right: 1 of 1").assertDoesNotExist()
    }
}
