package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The user listened in the middle of reciting, went back to the microphone and was not heard
 * (2026-10-22). Only audio started with the headphones button was followed, so audio started from
 * the ayah bar left no trace and the recitation waited at the old word while the user recited the
 * ayahs that had played. The fixture holds 1:1 (indexes 0 and 1) and 1:2 (index 2) on page 1.
 */
class ListenFromElsewhereTest {
    @get:Rule val folder = TemporaryFolder()

    @get:Rule val fixtures = ControllerFixtures()

    private suspend fun recitingFirstAyah(): ControllerFixture {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        return fixture
    }

    private fun ControllerFixture.audioIsPlaying(ayah: AyahRef) {
        audioPlayer.currentState.value = PlaybackState.Playing("husary", ayah, null, 0L, null)
    }

    @Test
    fun audioStartedElsewhereMakesTheMicrophoneGoOnAtTheAyahThatPlayed() = runTest {
        val fixture = recitingFirstAyah()

        fixture.audioIsPlaying(AyahRef(1, 2))
        fixture.controller.pauseMicrophone()
        fixture.controller.resumeReciting()
        fixture.controller.joinPendingWork()

        assertEquals(2, fixture.tracker.continuedFrom.last())
    }

    @Test
    fun audioThatEndedByItselfMakesTheNextStartGoOnAfterTheLastAyahPlayed() = runTest {
        val fixture = recitingFirstAyah()

        fixture.audioIsPlaying(AyahRef(1, 2))
        fixture.audioPlayer.currentState.value = PlaybackState.Idle
        fixture.controller.joinPendingWork()

        // The ayah after 1:2 is on the next page, so it is loaded fresh.
        assertEquals(fixture.fourth, fixture.controller.session.words.value.first().key)
    }

    @Test
    fun audioOfTheAyahBeingRecitedLeavesTheMicrophoneWhereItWas() = runTest {
        val fixture = recitingFirstAyah()

        fixture.audioIsPlaying(AyahRef(1, 1))
        fixture.controller.pauseMicrophone()
        fixture.controller.resumeReciting()
        fixture.controller.joinPendingWork()

        assertEquals(0, fixture.tracker.continuedFrom.last())
    }

    @Test
    fun audioWhileNotRecitingIsIgnored() = runTest {
        val fixture = ControllerFixture(folder.root)

        fixture.audioIsPlaying(AyahRef(1, 2))

        assertFalse(fixture.controller.isListeningOnward.value)
    }
}
