package app.awn.quran.feature.recite

import android.Manifest
import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.fakes.FakeAudioPlayer
import app.awn.quran.tracker.TrackerEvent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
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
class ReciteScreenTest {

    @get:Rule val composeRule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private val fixture = SessionFixture()
    private val audioPlayer = FakeAudioPlayer()
    private lateinit var settings: ReciteSettings

    @Before
    fun setUp() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        settings =
            ReciteSettings(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) {
                    File(folder.root, "screen.preferences_pb")
                },
            )
        composeRule.setContent {
            ReciteScreen(fixture.range, fixture.session, settings, audioPlayer)
        }
        composeRule.waitForIdle()
    }

    private fun hasState(description: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)

    private val hasNoState = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

    @Test
    fun wordsStartWithoutAState() {
        composeRule.onNodeWithTag(reciteWordTag(fixture.first)).assert(hasNoState)
    }

    @Test
    fun aConfirmedWordChangesStateAfterTheTrackerReportsIt() {
        fixture.script(fixture.confirmed(0))

        composeRule.onNodeWithTag(RECITE_PRIMARY_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(reciteWordTag(fixture.first)).assert(hasState("Correct"))
        composeRule.onNodeWithTag(reciteWordTag(fixture.second)).assert(hasNoState)
    }

    @Test
    fun aSkippedWordIsDescribedAsSkippedAndItsAyahGetsTheReviewButton() {
        composeRule.onNodeWithTag(reviewLabelTag(1)).assertDoesNotExist()
        fixture.script(TrackerEvent.Skipped(index = 1))

        composeRule.onNodeWithTag(RECITE_PRIMARY_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(reciteWordTag(fixture.second)).assert(hasState("Skipped"))
        composeRule.onNodeWithTag(reviewLabelTag(1)).assertExists()
    }

    @Test
    fun listenInTheReviewSheetPlaysTheFlaggedWord() {
        fixture.script(TrackerEvent.Skipped(index = 1))
        composeRule.onNodeWithTag(RECITE_PRIMARY_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(reciteWordTag(fixture.second)).performClick()
        composeRule.onNodeWithTag(REVIEW_LISTEN_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(Triple("husary", AyahRef(1, 1), 2)), audioPlayer.wordCalls)
    }
}
