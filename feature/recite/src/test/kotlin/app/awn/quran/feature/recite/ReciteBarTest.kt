package app.awn.quran.feature.recite

import android.Manifest
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Espresso, used by the Compose test rule, reflects on InputManager.getInstance(), which newer
// Android versions removed.
@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class ReciteBarTest {

    @get:Rule val composeRule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private lateinit var fixture: ControllerFixture
    private var exitCount = 0
    private var barShown by mutableStateOf(true)

    @Before
    fun setUp() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        runBlocking { fixture.controller.joinPendingWork() }
        composeRule.setContent {
            if (barShown) ReciteBar(fixture.controller, onExit = { exitCount++ })
        }
        composeRule.waitForIdle()
    }

    private fun stateOf(key: WordKey): WordState? = fixture.controller.session.wordStates.value[key]

    private fun recite(vararg events: TrackerEvent) {
        fixture.script(*events)
        composeRule.onNodeWithTag(RECITE_BAR_MIC_TAG).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun theBarStartsReadyWithoutAReviewButton() {
        composeRule.onNodeWithTag(RECITE_BAR_STATUS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Ready").assertIsDisplayed()
        composeRule.onNodeWithTag(RECITE_BAR_REVIEW_TAG).assertDoesNotExist()
    }

    @Test
    fun theMicrophoneButtonStartsAndTheWordTakesItsState() {
        recite(fixture.confirmed(0))

        assertEquals(WordState.Confirmed, stateOf(fixture.first))
        assertEquals(WordState.Neutral, stateOf(fixture.second))
    }

    @Test
    fun afterStoppingOneReviewButtonOpensTheSheetWithTheFlaggedWord() {
        recite(TrackerEvent.Skipped(index = 1))

        composeRule.onNodeWithTag(RECITE_BAR_REVIEW_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(REVIEW_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(REVIEW_LISTEN_TAG).performClick()
        // The reciter comes from a DataStore read on another thread.
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.audioPlayer.wordCalls.isNotEmpty() }
        val call = fixture.audioPlayer.wordCalls.single()
        assertEquals("husary", call.first)
        assertEquals(AyahRef(1, 1), call.second)
        assertEquals(2, call.third)
    }

    @Test
    fun theReviewSheetOpensEvenWhenNothingWasFlagged() {
        recite(fixture.confirmed(0))

        composeRule.onNodeWithTag(RECITE_BAR_REVIEW_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(REVIEW_SHEET_TAG).assertIsDisplayed()
        composeRule.onNode(hasText("Nothing to review")).assertIsDisplayed()
    }

    @Test
    fun theHideWordsSwitchHidesUnheardWords() {
        composeRule.onNodeWithTag(RECITE_BAR_HIDE_TAG).performClick()

        // The choice goes through the DataStore on another thread and back into the session.
        composeRule.waitUntil(timeoutMillis = 5_000) { stateOf(fixture.first) == WordState.Hidden }
    }

    @Test
    fun theHintButtonIsOffUntilWordsAreHidden() {
        composeRule.onNodeWithTag(RECITE_BAR_HINT_TAG).assertIsNotEnabled()
    }

    @Test
    fun theSpeedPopupSetsTheSpeed() {
        composeRule.onNodeWithTag(RECITE_BAR_SPEED_TAG).performClick()
        composeRule.onNodeWithText("Fastest").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.controller.session.speed.value == 1f
        }
    }

    @Test
    fun closeCallsOnExitAndClearsTheWordStates() {
        recite(fixture.confirmed(0))

        composeRule.onNodeWithTag(RECITE_BAR_CLOSE_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(1, exitCount)
        assertTrue(fixture.controller.session.wordStates.value.isEmpty())
        assertFalse(fixture.controller.isActive.value)
    }

    @Test
    fun removingTheBarStopsReciting() {
        recite(fixture.confirmed(0))

        barShown = false
        composeRule.waitForIdle()

        assertTrue(fixture.controller.session.wordStates.value.isEmpty())
        assertFalse(fixture.controller.isActive.value)
    }

    // ---- Reveal and grade ---------------------------------------------------------------

    private fun hideWords() {
        composeRule.onNodeWithTag(RECITE_BAR_HIDE_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { stateOf(fixture.first) == WordState.Hidden }
    }

    @Test
    fun theRevealButtonsAppearOnlyWhileWordsAreHidden() {
        composeRule.onNodeWithTag(RECITE_BAR_REVEAL_WORD_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(RECITE_BAR_REVEAL_AYAH_TAG).assertDoesNotExist()

        hideWords()

        composeRule.onNodeWithTag(RECITE_BAR_REVEAL_WORD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RECITE_BAR_REVEAL_AYAH_TAG).assertIsDisplayed()
    }

    @Test
    fun showNextWordRevealsOneWordWithoutTheMicrophone() {
        hideWords()

        composeRule.onNodeWithTag(RECITE_BAR_REVEAL_WORD_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(WordState.Neutral, stateOf(fixture.first))
        assertEquals(WordState.Hidden, stateOf(fixture.second))
        assertEquals(RecitationPhase.Idle, fixture.controller.session.phase.value)
    }

    @Test
    fun showAyahRevealsItAndOffersTheFourGrades() {
        hideWords()
        composeRule.onNodeWithTag(RECITE_BAR_GRADE_TAG_PREFIX + "easy").assertDoesNotExist()

        composeRule.onNodeWithTag(RECITE_BAR_REVEAL_AYAH_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(WordState.Neutral, stateOf(fixture.second))
        for (grade in listOf("easy", "good", "hard", "forgot")) {
            composeRule.onNodeWithTag(RECITE_BAR_GRADE_TAG_PREFIX + grade).assertIsDisplayed()
        }
    }

    @Test
    fun oneTapOnAGradeSendsItAndHidesTheGradeButtons() {
        hideWords()
        composeRule.onNodeWithTag(RECITE_BAR_REVEAL_AYAH_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RECITE_BAR_GRADE_TAG_PREFIX + "hard").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.graded.isNotEmpty() }

        assertEquals(listOf(AyahRef(1, 1) to AyahGrade.Hard), fixture.graded)
        composeRule.onNodeWithTag(RECITE_BAR_GRADE_TAG_PREFIX + "hard").assertDoesNotExist()
    }

    @Test
    fun aRecitedAyahOffersGradesWithTheSuggestedOneMarked() {
        hideWords()
        recite(fixture.confirmed(0), fixture.confirmed(1))

        composeRule.onNodeWithTag(RECITE_BAR_GRADE_TAG_PREFIX + "good").assertIsDisplayed()
        composeRule
            .onNodeWithTag(RECITE_BAR_GRADE_TAG_PREFIX + "good")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule
            .onNodeWithTag(RECITE_BAR_GRADE_TAG_PREFIX + "easy")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }
}
