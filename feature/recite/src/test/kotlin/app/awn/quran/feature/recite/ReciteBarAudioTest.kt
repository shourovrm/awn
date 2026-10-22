package app.awn.quran.feature.recite

import android.Manifest
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val PHONE_WIDTH = 360
private const val TOUCH_TARGET = 48
private const val FRAME = "audio-bar-frame"
private const val DURATION_MS = 4_000L

/** The audio controls of the recite bar: Listen, Pause, Stop, the progress line and the mic. */
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@RunWith(RobolectricTestRunner::class)
class ReciteBarAudioTest {
    @get:Rule val composeRule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    @get:Rule val fixtures = ControllerFixtures()

    private lateinit var fixture: ControllerFixture
    private val ayah = AyahRef(1, 1)

    private fun prepare(microphone: (() -> app.awn.quran.tracker.TrackerInput)? = null) {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        fixture =
            ControllerFixture(
                folder.root,
                microphone = microphone,
                recognitionThread =
                    if (microphone != null) RecitationSession.newRecognitionDispatcher() else null,
            )
        fixture.controller.reciteFrom(ayah)
        runBlocking { fixture.controller.joinPendingWork() }
    }

    private var colourMode by mutableStateOf(ColourMode.Awn)
    private var fontScale by mutableFloatStateOf(1f)

    private fun show() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides colourMode,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                AwnTheme {
                    Box(Modifier.width(PHONE_WIDTH.dp).testTag(FRAME)) {
                        ReciteBar(fixture.controller, onExit = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setPlayback(state: PlaybackState) {
        fixture.audioPlayer.currentState.value = state
        composeRule.waitForIdle()
    }

    private fun playing(positionMs: Long = 1_000L) =
        PlaybackState.Playing("husary", ayah, null, positionMs, DURATION_MS)

    private fun hasDescription(text: String) =
        SemanticsMatcher("description is $text") {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { description ->
                description.startsWith(text)
            } == true
        }

    @Test
    fun withNoAudioTheBarOffersListenAndNothingElseOfTheAudio() {
        prepare()
        show()

        composeRule.onNodeWithTag(RECITE_BAR_LISTEN_TAG).assert(hasDescription("Listen"))
        composeRule.onNodeWithTag(RECITE_BAR_AUDIO_STOP_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(RECITE_BAR_AUDIO_PROGRESS_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(RECITE_BAR_MIC_TAG).assertIsEnabled()
    }

    @Test
    fun listenPlaysFromTheCurrentAyahWithNoEnd() {
        prepare()
        show()

        composeRule.onNodeWithTag(RECITE_BAR_LISTEN_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.audioPlayer.playCalls.isNotEmpty() }

        val call = fixture.audioPlayer.playCalls.single()
        assertEquals(ayah, call.second)
        assertEquals(null, call.third)
    }

    @Test
    fun whileTheAyahPlaysListenIsPauseAndStopAndTheProgressLineShow() {
        prepare()
        show()

        setPlayback(playing())

        composeRule.onNodeWithTag(RECITE_BAR_LISTEN_TAG).assert(hasDescription("Pause audio"))
        composeRule.onNodeWithTag(RECITE_BAR_AUDIO_STOP_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(RECITE_BAR_AUDIO_PROGRESS_TAG).assertExists()
        composeRule.onNodeWithTag(RECITE_BAR_MIC_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(RECITE_BAR_HINT_TAG).assertDoesNotExist()
    }

    @Test
    fun theMicrophoneWhileAudioPlaysStopsTheAudioAndStartsReciting() {
        prepare(microphone = { OpenMicrophone() })
        show()
        composeRule.onNodeWithTag(RECITE_BAR_LISTEN_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.audioPlayer.playCalls.isNotEmpty() }
        val stopsBefore = fixture.audioPlayer.stopCalls

        composeRule.onNodeWithTag(RECITE_BAR_MIC_TAG).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.controller.session.phase.value == RecitationPhase.Listening
        }
        assertEquals(stopsBefore + 1, fixture.audioPlayer.stopCalls)
        assertEquals(PlaybackState.Idle, fixture.audioPlayer.currentState.value)
    }

    @Test
    fun pauseThenResumeGoThroughThePlayer() {
        prepare()
        show()
        setPlayback(playing())

        composeRule.onNodeWithTag(RECITE_BAR_LISTEN_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RECITE_BAR_LISTEN_TAG).assert(hasDescription("Resume audio"))

        composeRule.onNodeWithTag(RECITE_BAR_LISTEN_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RECITE_BAR_LISTEN_TAG).assert(hasDescription("Pause audio"))
    }

    @Test
    fun stopReturnsTheBarToNormalAndDoesNotRestartTheMicrophone() {
        prepare()
        show()
        setPlayback(playing())
        val stopsBefore = fixture.audioPlayer.stopCalls

        composeRule.onNodeWithTag(RECITE_BAR_AUDIO_STOP_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(stopsBefore + 1, fixture.audioPlayer.stopCalls)
        composeRule.onNodeWithTag(RECITE_BAR_AUDIO_STOP_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(RECITE_BAR_LISTEN_TAG).assert(hasDescription("Listen"))
        composeRule.onNodeWithTag(RECITE_BAR_MIC_TAG).assertIsEnabled()
        assertTrue(fixture.controller.session.phase.value != RecitationPhase.Listening)
    }

    @Test
    fun audioThatEndsByItselfDoesNotRestartTheMicrophone() {
        prepare()
        show()
        setPlayback(playing())

        setPlayback(PlaybackState.Idle)

        composeRule.onNodeWithTag(RECITE_BAR_AUDIO_STOP_TAG).assertDoesNotExist()
        assertTrue(fixture.controller.session.phase.value != RecitationPhase.Listening)
    }

    @Test
    fun audioStartingWhileTheMicrophoneListensPausesTheMicrophone() {
        prepare(microphone = { OpenMicrophone() })
        show()
        composeRule.onNodeWithTag(RECITE_BAR_MIC_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.controller.session.phase.value == RecitationPhase.Listening
        }

        setPlayback(playing())

        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.controller.session.phase.value == RecitationPhase.Stopped
        }
        setPlayback(PlaybackState.Idle)
        assertEquals(RecitationPhase.Stopped, fixture.controller.session.phase.value)
    }

    @Test
    fun everyButtonFitsInEveryAudioStateInAwnAndBlackAtFontScaleOneAndOnePointThree() {
        prepare()
        show()
        val states =
            listOf(
                PlaybackState.Idle,
                playing(),
                PlaybackState.Paused("husary", ayah, 1_000L),
                PlaybackState.Downloading(1L, 10L),
            )
        for (mode in listOf(ColourMode.Awn, ColourMode.Black)) {
            for (scale in listOf(1f, 1.3f)) {
                colourMode = mode
                fontScale = scale
                for (state in states) {
                    setPlayback(state)
                    assertTheButtonsFit("$mode $scale ${state::class.simpleName}")
                }
            }
        }
    }

    private fun assertTheButtonsFit(situation: String) {
        val tags =
            listOf(
                RECITE_BAR_CLOSE_TAG,
                RECITE_BAR_SPEED_TAG,
                RECITE_BAR_HIDE_TAG,
                RECITE_BAR_LISTEN_TAG,
                RECITE_BAR_MIC_TAG,
            )
        val stopIsShown = fixture.audioPlayer.currentState.value != PlaybackState.Idle
        for (tag in if (stopIsShown) tags + RECITE_BAR_AUDIO_STOP_TAG else tags) {
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$situation $tag left ${bounds.left}", bounds.left >= 0.dp)
            assertTrue("$situation $tag right ${bounds.right}", bounds.right <= PHONE_WIDTH.dp)
            assertTrue("$situation $tag width", bounds.width >= TOUCH_TARGET.dp)
            assertTrue("$situation $tag height", bounds.height >= TOUCH_TARGET.dp)
        }
    }
}
