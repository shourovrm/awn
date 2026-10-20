package app.awn.quran.feature.recite

import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.fakes.FakeReferencePhonemes
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
}
