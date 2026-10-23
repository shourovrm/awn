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

    @get:Rule val fixtures = ControllerFixtures()

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
        fixture.controller.joinPendingWork()
        assertTrue(withContext(Dispatchers.Default) { fixture.settings.hifzEnabled.first() })
    }

    @Test
    fun speechIsNotPreparedUntilReciteStarts() {
        val fixture = ControllerFixture(folder.root, prepareImmediately = false)

        assertEquals(SpeechState.Idle, fixture.controller.speechState.value)
    }

    @Test
    fun speechStateShowsPreparingWhileReciteLoads() = runTest {
        val fixture = ControllerFixture(folder.root, prepareImmediately = false)

        fixture.controller.reciteFrom(AyahRef(1, 1))
        assertEquals(SpeechState.Preparing, fixture.controller.speechState.value)

        fixture.finishPreparing()
        fixture.controller.joinPendingWork()
        assertEquals(SpeechState.Ready, fixture.controller.speechState.value)
    }

    @Test
    fun exitKeepsTheSpeechModelForTheGracePeriodThenReleasesIt() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        assertEquals(SpeechState.Ready, fixture.controller.speechState.value)

        fixture.controller.exit()
        fixture.controller.joinPendingWork()

        assertEquals(SpeechState.Ready, fixture.controller.speechState.value)
        assertEquals(0, fixture.closedModelCount)

        fixture.graceTimer.fireAll()

        assertEquals(SpeechState.Idle, fixture.controller.speechState.value)
        assertEquals(1, fixture.closedModelCount)
    }

    @Test
    fun reciteInsideTheGracePeriodReusesTheLoadedModel() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.controller.exit()
        fixture.controller.joinPendingWork()

        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.graceTimer.fireAll()

        assertEquals(1, fixture.builtModelCount)
        assertEquals(SpeechState.Ready, fixture.controller.speechState.value)
        assertEquals(true, fixture.controller.session.words.value.isNotEmpty())
    }

    @Test
    fun reciteAfterTheGracePeriodLoadsTheModelAgain() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.controller.exit()
        fixture.controller.joinPendingWork()
        fixture.graceTimer.fireAll()

        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        assertEquals(2, fixture.builtModelCount)
        assertEquals(SpeechState.Ready, fixture.controller.speechState.value)
        assertEquals(true, fixture.controller.session.words.value.isNotEmpty())
    }

    @Test
    fun lowMemoryReleasesTheModelThatOnlyWaitsForTheGracePeriod() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.controller.exit()
        fixture.controller.joinPendingWork()

        fixture.controller.onLowMemory()
        fixture.controller.joinPendingWork()

        assertEquals(SpeechState.Idle, fixture.controller.speechState.value)
        assertEquals(1, fixture.closedModelCount)
    }

    @Test
    fun lowMemoryLeavesTheModelAloneWhileReciting() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        fixture.controller.onLowMemory()
        fixture.controller.joinPendingWork()

        assertEquals(SpeechState.Ready, fixture.controller.speechState.value)
        assertEquals(0, fixture.closedModelCount)
    }

    @Test
    fun reciteGoesOnIntoTheNextPageWithoutAReset() = runTest {
        val fixture = ControllerFixture(folder.root, inputBlocks = 1)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(fixture.confirmed(0))

        fixture.controller.session.start()

        val session = fixture.controller.session
        assertEquals(
            listOf(fixture.first, fixture.second, fixture.third, fixture.fourth),
            session.words.value.map { it.key },
        )
        assertEquals(1, fixture.tracker.appended.size)
        assertEquals(WordState.Confirmed, session.wordStates.value[fixture.first])
        assertEquals(WordState.Neutral, session.wordStates.value[fixture.fourth])
    }

    @Test
    fun reciteCrossesIntoTheNextSurahAndReportsItsOwnAyah() = runTest {
        val fixture = ControllerFixture(folder.root, inputBlocks = 3)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.controller.setHideWords(true)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { fixture.controller.session.hifzEnabled.first { it } }
        }
        fixture.script(fixture.confirmed(0))
        fixture.script(fixture.confirmed(1), fixture.confirmed(2), fixture.confirmed(3))
        fixture.script(fixture.confirmed(4))

        fixture.controller.session.start()

        val session = fixture.controller.session
        assertEquals(fixture.fifth, session.words.value.last().key)
        assertTrue(
            "ayah 2:1 reported once, not mixed up with 1:1",
            fixture.progress.hifzCompletions.contains(AyahRef(2, 1) to 0),
        )
        assertEquals(1, fixture.progress.hifzCompletions.count { it.first == AyahRef(1, 1) })
    }

    @Test
    fun reciteStopsExtendingAtTheEndOfTheQuran() = runTest {
        val fixture = ControllerFixture(folder.root, inputBlocks = 4)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        repeat(4) { fixture.script(fixture.confirmed(it)) }

        fixture.controller.session.start()

        // Page 4 holds no ayah in the fixture, so the third request finds nothing more.
        assertEquals(5, fixture.controller.session.words.value.size)
        assertEquals(2, fixture.tracker.appended.size)
    }

    // ---- Revision -----------------------------------------------------------------------

    // The setting travels through the DataStore on another thread and back into the session.
    private suspend fun ControllerFixture.awaitHidingWords() {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { controller.session.hifzEnabled.first { it } }
        }
    }

    private suspend fun ControllerFixture.reviseAndReveal(ayah: AyahRef) {
        controller.reviseAt(ayah)
        controller.joinPendingWork()
        awaitHidingWords()
        controller.session.revealAyah()
    }

    @Test
    fun reviseAtLoadsFromTheAyahWithWordsHiddenAndMarksRevision() = runTest {
        val fixture = ControllerFixture(folder.root, dueAyahs = mutableListOf(AyahRef(1, 3)))

        fixture.controller.reviseAt(AyahRef(1, 3))
        fixture.controller.joinPendingWork()
        fixture.awaitHidingWords()

        assertTrue(fixture.controller.isActive.value)
        assertTrue(fixture.controller.isRevising.value)
        assertEquals(WordState.Hidden, fixture.controller.session.wordStates.value[fixture.fourth])
        assertEquals(1, fixture.controller.revisionRemaining.value)
    }

    @Test
    fun gradingMovesTheRevisionToTheNextDueAyah() = runTest {
        val fixture =
            ControllerFixture(
                folder.root,
                dueAyahs = mutableListOf(AyahRef(1, 1), AyahRef(1, 3)),
            )
        fixture.reviseAndReveal(AyahRef(1, 1))

        fixture.controller.grade(AyahGrade.Good)
        fixture.controller.joinPendingWork()

        assertEquals(listOf(AyahRef(1, 1) to AyahGrade.Good), fixture.graded)
        assertEquals(listOf(AyahRef(1, 3)), fixture.opened)
        assertEquals(fixture.fourth, fixture.controller.session.words.value.first().key)
        assertEquals(1, fixture.controller.revisionRemaining.value)
        assertTrue(fixture.controller.isRevising.value)
    }

    @Test
    fun gradingTheLastDueAyahEndsTheRevisionAndOpensNothing() = runTest {
        val fixture = ControllerFixture(folder.root, dueAyahs = mutableListOf(AyahRef(1, 1)))
        fixture.reviseAndReveal(AyahRef(1, 1))

        fixture.controller.grade(AyahGrade.Easy)
        fixture.controller.joinPendingWork()

        assertFalse(fixture.controller.isRevising.value)
        assertEquals(0, fixture.controller.revisionRemaining.value)
        assertTrue(fixture.opened.isEmpty())
        assertTrue(fixture.controller.isActive.value)
    }

    @Test
    fun gradingOutsideRevisionOnlySendsTheGrade() = runTest {
        val fixture = ControllerFixture(folder.root, dueAyahs = mutableListOf(AyahRef(1, 3)))
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.controller.setHideWords(true)
        fixture.awaitHidingWords()
        fixture.controller.session.revealAyah()

        fixture.controller.grade(AyahGrade.Hard)
        fixture.controller.joinPendingWork()

        assertEquals(listOf(AyahRef(1, 1) to AyahGrade.Hard), fixture.graded)
        assertTrue(fixture.opened.isEmpty())
        assertFalse(fixture.controller.isRevising.value)
    }

    @Test
    fun exitLeavesRevision() = runTest {
        val fixture = ControllerFixture(folder.root, dueAyahs = mutableListOf(AyahRef(1, 1)))
        fixture.controller.reviseAt(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        fixture.controller.exit()
        fixture.controller.joinPendingWork()

        assertFalse(fixture.controller.isRevising.value)
    }
}
