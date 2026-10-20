package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReciteControllerTest {

    @get:Rule val folder = TemporaryFolder()

    @Test
    fun reciteFromLoadsTheAyahsFromThereToTheEndOfThePage() = runTest {
        val fixture = ControllerFixture(folder.root)

        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        assertTrue(fixture.controller.isActive.value)
        assertEquals(
            listOf(fixture.first, fixture.second, fixture.third),
            fixture.controller.session.wordStates.value.keys.toList(),
        )
    }

    @Test
    fun reciteFromTheMiddleOfAPageSkipsTheEarlierAyahs() = runTest {
        val fixture = ControllerFixture(folder.root)

        fixture.controller.reciteFrom(AyahRef(1, 2))
        fixture.controller.joinPendingWork()

        assertEquals(
            listOf(fixture.third),
            fixture.controller.session.wordStates.value.keys.toList(),
        )
    }

    @Test
    fun exitStopsReciteAndClearsTheWordStates() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(fixture.confirmed(0))
        fixture.controller.session.start()
        assertEquals(
            WordState.Confirmed,
            fixture.controller.session.wordStates.value[fixture.first],
        )

        fixture.controller.exit()
        fixture.controller.joinPendingWork()

        assertFalse(fixture.controller.isActive.value)
        assertTrue(fixture.controller.session.wordStates.value.isEmpty())
        assertEquals(RecitationPhase.Idle, fixture.controller.session.phase.value)
    }

    @Test
    fun hideWordsTurnsUnheardWordsHiddenAndIsKept() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        fixture.controller.setHideWords(true)

        // The choice goes through the DataStore on another thread and back into the session.
        val hidden =
            withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    fixture.controller.session.wordStates.first {
                        it[fixture.first] == WordState.Hidden
                    }
                }
            }
        assertEquals(WordState.Hidden, hidden[fixture.first])
        assertTrue(withContext(Dispatchers.Default) { fixture.settings.hifzEnabled.first() })
    }

    @Test
    fun speechStateFollowsThePreparation() {
        val fixture = ControllerFixture(folder.root, prepareImmediately = false)
        assertEquals(SpeechState.Idle, fixture.controller.speechState.value)

        fixture.controller.prepareSpeech()
        assertEquals(SpeechState.Preparing, fixture.controller.speechState.value)

        fixture.finishPreparing()
        assertEquals(SpeechState.Ready, fixture.controller.speechState.value)
    }
}
