package app.awn.quran.feature.recite

import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.fakes.FakeReferencePhonemes
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeechPreparationTest {

    private class FakePrepared : PreparedSpeech {
        var closed = false
        override val referencePhonemes: ReferencePhonemes = FakeReferencePhonemes()

        override fun newPhonemeSource(): PhonemeSource = error("not needed")

        override fun close() {
            closed = true
        }
    }

    @Test
    fun prepareBuildsOnceAndReportsReady() {
        var builds = 0
        val preparation =
            SpeechPreparation(executor = { it.run() }) {
                builds++
                FakePrepared()
            }
        assertEquals(SpeechState.Idle, preparation.state.value)

        preparation.prepare()
        preparation.prepare()

        assertEquals(1, builds)
        assertEquals(SpeechState.Ready, preparation.state.value)
    }

    @Test
    fun stateIsPreparingUntilTheBackgroundWorkRuns() {
        val held = mutableListOf<Runnable>()
        val preparation = SpeechPreparation(executor = { held += it }) { FakePrepared() }

        preparation.prepare()
        assertEquals(SpeechState.Preparing, preparation.state.value)

        held.single().run()
        assertEquals(SpeechState.Ready, preparation.state.value)
    }

    @Test
    fun aFailedBuildIsReportedAndTriedAgainByPrepare() {
        var attempts = 0
        val preparation =
            SpeechPreparation(executor = { it.run() }) {
                attempts++
                check(attempts > 1) { "copy failed" }
                FakePrepared()
            }

        preparation.prepare()
        assertEquals(SpeechState.Failed, preparation.state.value)

        preparation.awaitReady()

        assertEquals(SpeechState.Ready, preparation.state.value)
    }

    @Test
    fun awaitReadyThrowsWhenPreparingKeepsFailing() {
        val preparation =
            SpeechPreparation(executor = { it.run() }) { error("the files are missing") }

        assertThrows(IllegalStateException::class.java) { preparation.awaitReady() }
        assertEquals(SpeechState.Failed, preparation.state.value)
    }

    @Test
    fun releaseClosesTheSpeechAndReturnsToIdle() {
        val prepared = FakePrepared()
        val preparation = SpeechPreparation(executor = { it.run() }) { prepared }
        preparation.prepare()
        assertSame(prepared, preparation.awaitReady())

        preparation.release()

        assertEquals(true, prepared.closed)
        assertEquals(SpeechState.Idle, preparation.state.value)
    }

    @Test
    fun releaseWhilePreparingClosesTheResultThatIsStillBeingBuilt() {
        val held = mutableListOf<Runnable>()
        val prepared = FakePrepared()
        val preparation = SpeechPreparation(executor = { held += it }) { prepared }
        preparation.prepare()

        preparation.release()
        held.single().run()

        assertEquals(true, prepared.closed)
        assertEquals(SpeechState.Idle, preparation.state.value)
    }

    @Test
    fun aStaleBuildNeverOverwritesTheStateOfANewerPreparation() {
        val held = mutableListOf<Runnable>()
        val builtModels = mutableListOf<FakePrepared>()
        val preparation =
            SpeechPreparation(executor = { held += it }) {
                FakePrepared().also { builtModels += it }
            }
        preparation.prepare()
        preparation.release()
        preparation.prepare()

        held[0].run()

        assertEquals(SpeechState.Preparing, preparation.state.value)
        assertEquals(true, builtModels[0].closed)

        held[1].run()

        assertEquals(SpeechState.Ready, preparation.state.value)
        assertEquals(false, builtModels[1].closed)
        assertSame(builtModels[1], preparation.awaitReady())
    }

    @Test
    fun aStaleBuildThatFailsLeavesTheStateAlone() {
        val held = mutableListOf<Runnable>()
        val preparation = SpeechPreparation(executor = { held += it }) { error("copy failed") }
        preparation.prepare()
        preparation.release()

        held.single().run()

        assertEquals(SpeechState.Idle, preparation.state.value)
    }

    @Test
    fun releaseWakesAWaiterInsteadOfLeavingItBlocked() {
        val preparation = SpeechPreparation(executor = {}) { FakePrepared() }
        val waiterOutcome = AtomicReference<Throwable?>()
        val waiter = Thread {
            try {
                preparation.awaitReady()
            } catch (problem: IllegalStateException) {
                waiterOutcome.set(problem)
            }
        }
        waiter.start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (waiter.state != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }

        preparation.release()
        waiter.join(5_000)

        assertEquals(false, waiter.isAlive)
        assertEquals(true, waiterOutcome.get() is IllegalStateException)
    }

    @Test
    fun theModelStaysReadyDuringTheGracePeriodAndIsReleasedWhenItEnds() {
        val runner = ManualDelayedRunner()
        val prepared = FakePrepared()
        val preparation =
            SpeechPreparation(executor = { it.run() }, delayedRunner = runner) { prepared }
        preparation.prepare()

        preparation.releaseAfterGrace(graceMillis = 120_000)

        assertEquals(SpeechState.Ready, preparation.state.value)
        assertEquals(false, prepared.closed)
        assertEquals(120_000L, runner.scheduled.single().delayMillis)

        runner.fireAll()

        assertEquals(true, prepared.closed)
        assertEquals(SpeechState.Idle, preparation.state.value)
    }

    @Test
    fun aRestartInsideTheGracePeriodReusesTheModelAndCancelsTheRelease() {
        val runner = ManualDelayedRunner()
        var builds = 0
        val preparation =
            SpeechPreparation(executor = { it.run() }, delayedRunner = runner) {
                builds++
                FakePrepared()
            }
        preparation.prepare()
        preparation.releaseAfterGrace(graceMillis = 120_000)

        preparation.prepare()
        runner.fireAll()

        assertEquals(1, builds)
        assertEquals(SpeechState.Ready, preparation.state.value)
        assertEquals(true, runner.scheduled.single().cancelled)
    }

    @Test
    fun aGraceTimerThatFiresAfterARestartDoesNotReleaseTheNewModel() {
        val runner = ManualDelayedRunner()
        val preparation =
            SpeechPreparation(executor = { it.run() }, delayedRunner = runner) { FakePrepared() }
        preparation.prepare()
        preparation.releaseAfterGrace(graceMillis = 120_000)
        preparation.prepare()

        // A timer thread that was already past its cancel check when prepare ran.
        runner.scheduled.single().action.run()

        assertEquals(SpeechState.Ready, preparation.state.value)
    }

    @Test
    fun releaseNowCancelsThePendingGraceTimer() {
        val runner = ManualDelayedRunner()
        val prepared = FakePrepared()
        val preparation =
            SpeechPreparation(executor = { it.run() }, delayedRunner = runner) { prepared }
        preparation.prepare()
        preparation.releaseAfterGrace(graceMillis = 120_000)

        preparation.release()

        assertEquals(true, prepared.closed)
        assertEquals(true, runner.scheduled.single().cancelled)
    }

    @Test
    fun aSecondGracePeriodReplacesTheFirst() {
        val runner = ManualDelayedRunner()
        val preparation =
            SpeechPreparation(executor = { it.run() }, delayedRunner = runner) { FakePrepared() }
        preparation.prepare()
        preparation.releaseAfterGrace(graceMillis = 120_000)
        preparation.releaseAfterGrace(graceMillis = 60_000)

        assertEquals(listOf(true, false), runner.scheduled.map { it.cancelled })
    }

    @Test
    fun graceWithNothingLoadedSchedulesNothing() {
        val runner = ManualDelayedRunner()
        val preparation =
            SpeechPreparation(executor = { it.run() }, delayedRunner = runner) { FakePrepared() }

        preparation.releaseAfterGrace(graceMillis = 120_000)

        assertEquals(0, runner.scheduled.size)
    }
}
