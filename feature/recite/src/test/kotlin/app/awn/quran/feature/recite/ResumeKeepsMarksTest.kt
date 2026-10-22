package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Going back to reciting after listening must keep the marks of what was recited before. */
class ResumeKeepsMarksTest {
    @get:Rule val folder = TemporaryFolder()

    @get:Rule val fixtures = ControllerFixtures()

    @Test
    fun marksStayOnThePageRightAfterResumingBeforeAnythingIsHeard() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(TrackerEvent.WordChanged(0, WordState.Confirmed))
        fixture.controller.session.start()
        fixture.controller.listenOnward()
        fixture.controller.joinPendingWork()

        fixture.controller.resumeReciting()
        fixture.controller.joinPendingWork()

        val states = fixture.controller.session.wordStates.value
        assertEquals(WordState.Confirmed, states[fixture.first])
    }
}
