package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.PlaybackState
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.WordState
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Two bugs the user found on the phone (build 227, 2026-10-22):
 * - "if recited one ayah, and the user paused the recitation with the mic stop button; when
 *   restarts it starts from again." The bar's microphone stop must keep what was recited and the
 *   next start must go on from the next word.
 * - "If one switches from listening to reciting, then switches again, it starts from top of the
 *   page." Listening must start where the user is: the next word to recite, else the ayah after
 *   the last one recited, never the first loaded ayah once something was recited or resumed.
 *
 * The fixture holds 1:1 (indexes 0 and 1) and 1:2 (index 2) on page 1, 1:3 on page 2.
 */
class MicrophonePauseAndListenAgainTest {
    @get:Rule val folder = TemporaryFolder()

    @get:Rule val fixtures = ControllerFixtures()

    private fun confirmed(index: Int) = TrackerEvent.WordChanged(index, WordState.Confirmed)

    @Test
    fun stoppingTheMicrophoneKeepsTheRecitedAyahAndTheNextStartGoesOn() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(confirmed(0), confirmed(1))
        fixture.controller.session.start()

        fixture.controller.stopMicrophone()
        fixture.script(confirmed(2))
        fixture.controller.session.start()

        val states = fixture.controller.session.wordStates.value
        assertEquals(WordState.Confirmed, states[fixture.first])
        assertEquals(WordState.Confirmed, states[fixture.second])
        assertEquals(WordState.Confirmed, states[fixture.third])
        assertEquals(2, fixture.tracker.continuedFrom.last())
    }

    @Test
    fun theBarsMicrophoneStopKeepsProgress() {
        val bar = File("src/main/kotlin/app/awn/quran/feature/recite/ReciteBar.kt").readText()

        assertFalse(
            "The microphone stop must not call session.stop(), which makes the next start clear " +
                "the marks; use controller.stopMicrophone()",
            bar.contains("listening -> session.stop()"),
        )
    }

    @Test
    fun afterTheLastLoadedWordListeningStartsAtTheNextAyah() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(confirmed(0), confirmed(1), confirmed(2))
        fixture.controller.session.start()

        assertEquals(AyahRef(1, 3), fixture.controller.ayahToListenTo())
    }

    @Test
    fun listeningAgainAfterGoingBackToRecitingStartsWhereRecitingResumed() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()
        fixture.audioPlayer.currentState.value =
            PlaybackState.Playing("husary", AyahRef(1, 2), null, 0L, null)
        fixture.controller.resumeReciting()
        fixture.controller.joinPendingWork()

        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()

        assertEquals(AyahRef(1, 2), fixture.audioPlayer.playCalls.last().second)
    }

    @Test
    fun listeningAgainAfterRecitingAWordStartsAtTheNextWordsAyah() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()
        fixture.audioPlayer.currentState.value =
            PlaybackState.Playing("husary", AyahRef(1, 1), null, 0L, null)
        // Back to reciting: the user says both words of 1:1.
        fixture.script(confirmed(0), confirmed(1))
        fixture.controller.resumeReciting()
        fixture.controller.joinPendingWork()

        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()

        assertEquals(AyahRef(1, 2), fixture.audioPlayer.playCalls.last().second)
    }
}
