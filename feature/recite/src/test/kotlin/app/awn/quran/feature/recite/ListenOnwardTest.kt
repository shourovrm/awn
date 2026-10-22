package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.PlaybackOptions
import app.awn.quran.player.PlaybackState
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The user's rules for listening in the middle of reciting (2026-10-22):
 * - The headphones button keeps playing from the ayah the user is on until the user presses the
 *   microphone; it does not stop after one ayah. The player's repeat setting applies.
 * - The microphone stops the audio and goes on reciting where the listening left off: at the ayah
 *   that was playing when it was stopped, or, when the audio had already ended, at the ayah after
 *   the last one played.
 * - Ayahs the user only listened to are never marked as skipped, and the user may start with the
 *   ayah after the expected one without the expected one being marked as skipped.
 *
 * The fixture holds 1:1 (two words, indexes 0 and 1) and 1:2 (one word, index 2) on page 1.
 */
class ListenOnwardTest {
    @get:Rule val folder = TemporaryFolder()

    @get:Rule val fixtures = ControllerFixtures()

    private suspend fun recitingFrom(first: AyahRef, vararg heard: TrackerEvent): ControllerFixture {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(first)
        fixture.controller.joinPendingWork()
        if (heard.isNotEmpty()) {
            fixture.script(*heard)
            fixture.controller.session.start()
        }
        return fixture
    }

    private fun ControllerFixture.audioIsPlaying(ayah: AyahRef) {
        audioPlayer.currentState.value = PlaybackState.Playing("husary", ayah, null, 0L, null)
    }

    private fun ControllerFixture.stateOf(index: Int): WordState? {
        val key = listOf(first, second, third)[index]
        return controller.session.wordStates.value[key]
    }

    @Test
    fun listeningPlaysFromTheAyahOfTheNextWordWithNoEnd() = runTest {
        val fixture = recitingFrom(AyahRef(1, 1), fixtureConfirmed(0), fixtureConfirmed(1))

        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()

        val reciter = fixture.settings.reciterId.first()
        assertEquals(Triple(reciter, AyahRef(1, 2), null), fixture.audioPlayer.playCalls.last())
        assertTrue(fixture.controller.isListeningOnward.value)
        assertNotEquals(RecitationPhase.Listening, fixture.controller.session.phase.value)
    }

    @Test
    fun listeningKeepsTheRepeatSetting() = runTest {
        val fixture = recitingFrom(AyahRef(1, 1))
        fixture.audioPlayer.setOptions(PlaybackOptions(repeatEachAyah = 3))

        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()

        assertEquals(3, fixture.audioPlayer.options.value.repeatEachAyah)
    }

    @Test
    fun theMicrophoneStopsTheAudioAndGoesOnAtTheAyahThatWasPlaying() = runTest {
        val fixture = recitingFrom(AyahRef(1, 1), fixtureConfirmed(0))
        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()
        fixture.audioIsPlaying(AyahRef(1, 1))
        fixture.audioIsPlaying(AyahRef(1, 2))
        val stopsBefore = fixture.audioPlayer.stopCalls

        fixture.controller.resumeReciting()
        fixture.controller.joinPendingWork()

        assertEquals(stopsBefore + 1, fixture.audioPlayer.stopCalls)
        // Index 2 is the first word of 1:2, the ayah that was playing.
        assertEquals(2, fixture.tracker.continuedFrom.last())
        assertFalse(fixture.controller.isListeningOnward.value)
    }

    @Test
    fun whenTheAudioHadEndedRecitingGoesOnAfterTheLastAyahPlayed() = runTest {
        val fixture = recitingFrom(AyahRef(1, 1))
        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()
        fixture.audioIsPlaying(AyahRef(1, 1))
        fixture.audioPlayer.currentState.value = PlaybackState.Idle

        fixture.controller.resumeReciting()
        fixture.controller.joinPendingWork()

        assertEquals(2, fixture.tracker.continuedFrom.last())
    }

    @Test
    fun wordsTheUserOnlyListenedToAreNeverMarkedSkipped() = runTest {
        val fixture = recitingFrom(AyahRef(1, 1), fixtureConfirmed(0))
        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()
        fixture.audioIsPlaying(AyahRef(1, 1))
        fixture.audioIsPlaying(AyahRef(1, 2))
        // What the microphone hears once it is on again: the tracker reports the listened word
        // (index 1) as left out when the user says 1:2.
        fixture.script(
            TrackerEvent.Skipped(1),
            TrackerEvent.WordChanged(1, WordState.Wrong),
            fixtureConfirmed(2),
        )

        fixture.controller.resumeReciting()
        fixture.controller.joinPendingWork()

        assertNotEquals(WordState.Wrong, fixture.stateOf(1))
        assertEquals(WordState.Confirmed, fixture.stateOf(2))
    }

    @Test
    fun theUserMayStartWithTheAyahAfterTheExpectedOne() = runTest {
        val fixture = recitingFrom(AyahRef(1, 1))
        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()
        // Stopped in the middle of 1:1, so reciting is expected to go on at 1:1.
        fixture.audioIsPlaying(AyahRef(1, 1))
        // The user goes straight to 1:2: the tracker reports both words of 1:1 as left out.
        fixture.script(
            TrackerEvent.Skipped(0),
            TrackerEvent.WordChanged(0, WordState.Wrong),
            TrackerEvent.Skipped(1),
            TrackerEvent.WordChanged(1, WordState.Wrong),
            fixtureConfirmed(2),
        )

        fixture.controller.resumeReciting()
        fixture.controller.joinPendingWork()

        assertNotEquals(WordState.Wrong, fixture.stateOf(0))
        assertNotEquals(WordState.Wrong, fixture.stateOf(1))
        assertEquals(WordState.Confirmed, fixture.stateOf(2))
    }

    @Test
    fun withoutListeningALeftOutWordIsStillMarked() = runTest {
        val fixture =
            recitingFrom(
                AyahRef(1, 1),
                TrackerEvent.Skipped(0),
                TrackerEvent.WordChanged(0, WordState.Wrong),
                fixtureConfirmed(1),
            )

        assertEquals(WordState.Wrong, fixture.stateOf(0))
    }

    @Test
    fun stoppingTheAudioWithoutTheMicrophoneLeavesTheMicrophoneOff() = runTest {
        val fixture = recitingFrom(AyahRef(1, 1))
        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()
        fixture.audioIsPlaying(AyahRef(1, 1))

        fixture.controller.stopListening()
        fixture.controller.joinPendingWork()

        assertEquals(PlaybackState.Idle, fixture.audioPlayer.currentState.value)
        assertFalse(fixture.controller.isListeningOnward.value)
        assertNotEquals(RecitationPhase.Listening, fixture.controller.session.phase.value)
    }

    private fun fixtureConfirmed(index: Int) = TrackerEvent.WordChanged(index, WordState.Confirmed)
}
