package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.PlaybackState
import app.awn.quran.tracker.TrackerInput
import app.awn.quran.tracker.WordState
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A microphone that stays open until it is closed, as a real one does. */
class OpenMicrophone : TrackerInput, AutoCloseable {
    private val closed = CountDownLatch(1)

    override fun nextBlock(): FloatArray? {
        closed.await()
        return null
    }

    override fun close() = closed.countDown()
}

class ReciteListeningTest {
    @get:Rule val folder = TemporaryFolder()
    @get:Rule val fixtures = ControllerFixtures()

    @Test
    fun beforeRecitingListenPlaysTheFirstLoadedAyah() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 2))
        fixture.controller.joinPendingWork()

        assertEquals(AyahRef(1, 2), fixture.controller.ayahToListenTo())
    }

    @Test
    fun afterRecitingListenPlaysTheAyahOfTheNextExpectedWord() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(fixture.confirmed(0), fixture.confirmed(1))
        fixture.controller.session.start()

        assertEquals(AyahRef(1, 2), fixture.controller.ayahToListenTo())
    }

    @Test
    fun nothingLoadedGivesNoAyah() = runTest {
        val fixture = ControllerFixture(folder.root)

        assertNull(fixture.controller.ayahToListenTo())
    }

    @Test
    fun listenPlaysThatOneAyahWithTheChosenReciter() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        fixture.controller.listenToAyah()

        val reciter = fixture.settings.reciterId.first()
        assertEquals(
            listOf(Triple(reciter, AyahRef(1, 1), AyahRef(1, 1))),
            fixture.audioPlayer.playCalls,
        )
    }

    @Test
    fun listenPlaysAFocusedWordWithTheWordAudioReciterNotTheReciteReciter() = runTest {
        val fixture = ControllerFixture(folder.root, hasWordTimings = true, wordReciter = "alafasy")
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.controller.focusWord(fixture.second)

        fixture.controller.listenToAyah()

        assertEquals(
            listOf(Triple("alafasy", AyahRef(1, 1), 2)),
            fixture.audioPlayer.wordCalls,
        )
        assertEquals("husary", fixture.settings.reciterId.first())
    }

    @Test
    fun pausingTheMicrophoneKeepsWhatWasHeard() = runTest {
        val fixture =
            ControllerFixture(
                folder.root,
                microphone = { OpenMicrophone() },
                recognitionThread = RecitationSession.newRecognitionDispatcher(),
            )
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.controller.session.start()

        fixture.controller.pauseMicrophone()

        assertEquals(RecitationPhase.Stopped, fixture.controller.session.phase.value)
        assertEquals(
            listOf(fixture.first, fixture.second, fixture.third),
            fixture.controller.session.wordStates.value.keys.toList(),
        )
    }

    @Test
    fun pausingTheMicrophoneWhenItIsOffChangesNothing() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        fixture.controller.pauseMicrophone()

        assertEquals(RecitationPhase.Idle, fixture.controller.session.phase.value)
    }

    @Test
    fun startingReciteFromHereStopsTheAudio() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.audioPlayer.currentState.value =
            PlaybackState.Playing("husary", AyahRef(1, 1), null, 0L, null)

        fixture.controller.reciteFrom(AyahRef(1, 2))
        fixture.controller.joinPendingWork()

        assertEquals(1, fixture.audioPlayer.stopCalls)
        assertEquals(PlaybackState.Idle, fixture.audioPlayer.currentState.value)
    }

    @Test
    fun startingRevisionStopsTheAudio() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.audioPlayer.currentState.value =
            PlaybackState.Playing("husary", AyahRef(1, 1), null, 0L, null)

        fixture.controller.reviseAt(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        assertEquals(1, fixture.audioPlayer.stopCalls)
    }

    @Test
    fun startAfterListeningGoesOnFromTheNextExpectedWordWithTheMarksKept() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(fixture.confirmed(0))
        fixture.controller.session.start()
        fixture.controller.pauseMicrophone()

        fixture.script(fixture.confirmed(1))
        fixture.controller.session.start()

        val states = fixture.controller.session.wordStates.value
        assertEquals(WordState.Confirmed, states[fixture.first])
        assertEquals(WordState.Confirmed, states[fixture.second])
        assertEquals(listOf(1), fixture.tracker.continuedFrom)
        assertEquals(fixture.third, fixture.controller.session.currentWord.value)
    }

    @Test
    fun startAfterAnOrdinaryStopStillStartsAgainFromTheBeginning() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(fixture.confirmed(0))
        fixture.controller.session.start()

        fixture.controller.session.start()

        assertEquals(WordState.Neutral, fixture.controller.session.wordStates.value[fixture.first])
        assertTrue(fixture.tracker.continuedFrom.isEmpty())
    }

    @Test
    fun reciteFromHereAfterListeningStartsFresh() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(fixture.confirmed(0))
        fixture.controller.session.start()
        fixture.controller.pauseMicrophone()

        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.controller.session.start()

        assertEquals(WordState.Neutral, fixture.controller.session.wordStates.value[fixture.first])
        assertTrue(fixture.tracker.continuedFrom.isEmpty())
    }
}
