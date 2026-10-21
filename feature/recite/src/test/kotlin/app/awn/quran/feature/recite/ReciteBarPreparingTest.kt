package app.awn.quran.feature.recite

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The bar shows one waiting word and the close button until the speech model is ready. */
@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class ReciteBarPreparingTest {

    @get:Rule val composeRule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private lateinit var fixture: ControllerFixture
    private var exitCount = 0

    @Before
    fun setUp() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        fixture = ControllerFixture(folder.root, prepareImmediately = false)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        composeRule.setContent { ReciteBar(fixture.controller, onExit = { exitCount++ }) }
        composeRule.waitForIdle()
    }

    @Test
    fun whilePreparingTheBarShowsTheWaitingWordWithProgress() {
        composeRule.onNodeWithText("Preparing…").assertIsDisplayed()
        composeRule.onNodeWithTag(RECITE_BAR_PROGRESS_TAG).assertIsDisplayed()
    }

    @Test
    fun whilePreparingNoControlExceptCloseIsShown() {
        composeRule.onNodeWithTag(RECITE_BAR_CLOSE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RECITE_BAR_MIC_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(RECITE_BAR_SPEED_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(RECITE_BAR_HIDE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(RECITE_BAR_HINT_TAG).assertDoesNotExist()
    }

    @Test
    fun whilePreparingNoWordIsLoadedToFollow() {
        assertEquals(true, fixture.controller.session.words.value.isEmpty())
    }

    @Test
    fun closeStillWorksWhilePreparing() {
        composeRule.onNodeWithTag(RECITE_BAR_CLOSE_TAG).performClick()

        assertEquals(1, exitCount)
        assertEquals(false, fixture.controller.isActive.value)
    }

    @Test
    fun whenTheModelIsReadyTheControlsReplaceTheWaitingWord() {
        fixture.finishPreparing()
        runBlocking { fixture.controller.joinPendingWork() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RECITE_BAR_MIC_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RECITE_BAR_SPEED_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RECITE_BAR_PROGRESS_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Ready").assertIsDisplayed()
    }
}
