package app.awn.quran.feature.recite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which message the strip above the recite bar shows; the bar's buttons carry everything else. */
class ReciteStripTest {
    private val quiet =
        ReciteStripInputs(
            phase = RecitationPhase.Idle,
            access = MicrophoneAccess.Granted,
            gradeAyah = null,
            status = null,
            isRevising = false,
            revisionRemaining = 0,
            hasMicrophoneFailure = false,
        )

    @Test
    fun readyListeningAndStoppedShowNoMessage() {
        for (phase in
            listOf(RecitationPhase.Idle, RecitationPhase.Listening, RecitationPhase.Stopped)) {
            assertNull(reciteStripMessageOf(quiet.copy(phase = phase)))
        }
    }

    @Test
    fun aMissingPermissionAsksForTheMicrophoneUntilTheUserListens() {
        val asking = quiet.copy(access = MicrophoneAccess.CanAsk)

        assertEquals(ReciteStripMessage.AllowMicrophone, reciteStripMessageOf(asking))
        assertNull(reciteStripMessageOf(asking.copy(phase = RecitationPhase.Listening)))
    }

    @Test
    fun aPermissionTurnedOffForGoodPointsToTheSettings() {
        val denied = quiet.copy(access = MicrophoneAccess.DeniedPermanently)

        assertEquals(ReciteStripMessage.MicrophoneOff, reciteStripMessageOf(denied))
    }

    @Test
    fun aFinishedAyahNamesItForTheGradeButtons() {
        assertEquals(
            ReciteStripMessage.Grade(3),
            reciteStripMessageOf(quiet.copy(gradeAyah = 3, phase = RecitationPhase.Stopped)),
        )
    }

    @Test
    fun whatTheRunFoundShowsAfterTheGradeAndBeforeTheRevisionCount() {
        val finding = ReciteStatus.Skipped(ayah = 2, wordPosition = 4)
        val busy = quiet.copy(status = finding, isRevising = true, revisionRemaining = 5)

        assertEquals(ReciteStripMessage.Finding(finding), reciteStripMessageOf(busy))
        assertEquals(
            ReciteStripMessage.Grade(2),
            reciteStripMessageOf(busy.copy(gradeAyah = 2)),
        )
    }

    @Test
    fun theRevisionCountShowsOnlyBeforeTheFirstRun() {
        val revising = quiet.copy(isRevising = true, revisionRemaining = 5)

        assertEquals(ReciteStripMessage.Revising(5), reciteStripMessageOf(revising))
        assertNull(reciteStripMessageOf(revising.copy(phase = RecitationPhase.Stopped)))
        assertNull(reciteStripMessageOf(revising.copy(revisionRemaining = 0)))
    }

    @Test
    fun aFailedRunSaysWhichPartFailed() {
        val failed = quiet.copy(phase = RecitationPhase.Error)

        assertEquals(ReciteStripMessage.NoText, reciteStripMessageOf(failed))
        assertEquals(
            ReciteStripMessage.NoMicrophone,
            reciteStripMessageOf(failed.copy(hasMicrophoneFailure = true)),
        )
    }
}
