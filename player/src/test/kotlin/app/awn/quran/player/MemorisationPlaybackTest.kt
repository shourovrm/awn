package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Basmalah rule, gapless preparation, the learner's pause, cumulative drill and sleep timer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemorisationPlaybackTest {

    private val engine = FakePlaybackEngine()

    private fun TestScope.controller(): PlaybackController {
        val store = LocalAudioStore(java.io.File("unused"))
        return PlaybackController(
            engine = engine,
            resolver = ReciterAudioResolver(fakeReciters(), store, fakeQuranText()),
            playlistBuilder = PlaylistBuilder(fakeQuranText()),
            scope = backgroundScope,
            clockMs = { testScheduler.currentTime },
        )
    }

    private fun TestScope.endPlays(count: Int) {
        repeat(count) {
            engine.listener!!.onEnded()
            runCurrent()
        }
    }

    private fun fileNames() = engine.starts.map { it.source.url.substringAfterLast('/') }

    private fun preparedFileName() = engine.preparedNext.last()?.url?.substringAfterLast('/')

    @Test
    fun basmalahPlaysBeforeTheFirstAyahAndNotBetweenRepeats() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(repeatEachAyah = 2, repeatRange = 2))
        controller.play("alafasy", AyahRef(2, 1), AyahRef(2, 2))
        endPlays(9)

        assertEquals(
            listOf("001001.mp3", "002001.mp3", "002001.mp3", "002002.mp3", "002002.mp3") +
                listOf("002001.mp3", "002001.mp3", "002002.mp3", "002002.mp3"),
            fileNames(),
        )
        assertEquals("Surah 2 2:1", engine.starts.first().source.title)
    }

    @Test
    fun basmalahStateKeepsTheAyahItIntroducesAndLightsNoWord() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(2, 1), AyahRef(2, 2))
        val playing = controller.state.value as PlaybackState.Playing
        assertEquals(AyahRef(2, 1), playing.ayah)
        assertNull(playing.wordPosition)
    }

    @Test
    fun surahFileReciterGetsNoSeparateBasmalah() = runTest {
        val controller = controller()
        controller.play("raad", AyahRef(2, 1), AyahRef(2, 2))
        assertEquals(listOf("002.mp3"), fileNames())
    }

    @Test
    fun aSingleWordNeverGetsABasmalah() = runTest {
        val controller = controller()
        controller.playWord("alafasy", AyahRef(1, 1), 1)
        assertEquals(1, engine.starts.size)
    }

    @Test
    fun cumulativePlaysTheExactDrillAndReportsTheStep() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(cumulative = true))
        controller.play("alafasy", AyahRef(2, 2), AyahRef(2, 4))
        val steps = mutableListOf((controller.state.value as PlaybackState.Playing).step)
        repeat(7) {
            endPlays(1)
            (controller.state.value as? PlaybackState.Playing)?.let { steps += it.step }
        }
        assertEquals(
            listOf("002002", "002003", "002002", "002003", "002004", "002002", "002003", "002004"),
            fileNames().map { it.removeSuffix(".mp3") },
        )
        assertEquals(PlayStep(1, 5), steps.first())
        assertEquals(PlayStep(5, 5), steps.last())
        endPlays(1)
        assertEquals(PlaybackState.Idle, controller.state.value)
    }

    @Test
    fun cumulativeChangedWhilePlayingWaitsForTheNextRange() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(2, 2), AyahRef(2, 3))
        controller.setOptions(PlaybackOptions(cumulative = true))
        endPlays(1)
        assertEquals(listOf("002002.mp3", "002003.mp3"), fileNames())
        assertNull((controller.state.value as PlaybackState.Playing).step)
    }

    @Test
    fun optionsChangedWhilePlayingNeverRestartTheAudio() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(2, 2), AyahRef(2, 4))
        val startsBefore = engine.starts.size
        engine.stopped = false

        controller.setOptions(
            PlaybackOptions(
                repeatEachAyah = 3,
                repeatRange = 2,
                speed = 1.25f,
                pauseAfterAyah = PauseAfterAyah.Twice,
            ),
        )
        runCurrent()

        assertEquals(startsBefore, engine.starts.size)
        assertEquals(false, engine.stopped)
        assertEquals(1.25f, engine.lastSpeed)
    }

    @Test
    fun theNextAyahIsPreparedWhileTheCurrentOnePlays() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(2, 2), AyahRef(2, 3))
        runCurrent()
        assertEquals("002003.mp3", preparedFileName())

        endPlays(1)
        assertNull(preparedFileName())
    }

    @Test
    fun aRepeatedAyahIsPreparedAsItself() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(repeatEachAyah = 2))
        controller.play("alafasy", AyahRef(2, 2), AyahRef(2, 3))
        runCurrent()
        assertEquals("002002.mp3", preparedFileName())
    }

    @Test
    fun theBasmalahIsPreparedBeforeTheFirstAyahOfANewSurah() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 3), AyahRef(2, 1))
        runCurrent()
        assertEquals("001001.mp3", preparedFileName())
    }

    @Test
    fun changingTheRepeatWhilePlayingChangesWhatIsPrepared() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(2, 2), AyahRef(2, 3))
        runCurrent()
        assertEquals("002003.mp3", preparedFileName())

        controller.setOptions(PlaybackOptions(repeatEachAyah = 2))
        runCurrent()
        assertEquals("002002.mp3", preparedFileName())
    }

    @Test
    fun nothingIsPreparedAcrossALearnerPause() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(pauseAfterAyah = PauseAfterAyah.Once))
        controller.play("alafasy", AyahRef(1, 1), AyahRef(1, 2))
        runCurrent()
        assertNull(preparedFileName())
    }

    @Test
    fun theLearnerGetsOneAndAHalfTimesTheAyahLengthOfSilence() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(pauseAfterAyah = PauseAfterAyah.OneAndHalf))
        controller.play("alafasy", AyahRef(1, 1), AyahRef(1, 2))
        // The fixture says 1:1 is 1600 ms long, so the silence is 2400 ms.
        endPlays(1)
        assertEquals(1, engine.starts.size)
        assertNull((controller.state.value as PlaybackState.Playing).wordPosition)

        advanceTimeBy(2_399)
        runCurrent()
        assertEquals(1, engine.starts.size)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, engine.starts.size)
    }

    @Test
    fun theSilenceIsMeasuredInPlayedTimeSoSlowerSpeechGetsLonger() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(pauseAfterAyah = PauseAfterAyah.Once, speed = 0.8f))
        controller.play("alafasy", AyahRef(1, 1), AyahRef(1, 2))
        endPlays(1)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(1, engine.starts.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, engine.starts.size)
    }

    @Test
    fun noSilenceFollowsTheLastAyahOrABasmalah() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(pauseAfterAyah = PauseAfterAyah.Twice))
        controller.play("alafasy", AyahRef(2, 1), AyahRef(2, 1))
        endPlays(1)
        assertEquals(2, engine.starts.size)

        endPlays(1)
        assertEquals(PlaybackState.Idle, controller.state.value)
    }

    @Test
    fun pausingDuringTheSilenceKeepsTheRestOfItForResume() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(pauseAfterAyah = PauseAfterAyah.Twice))
        controller.play("alafasy", AyahRef(1, 1), AyahRef(1, 2))
        endPlays(1) // silence of 3200 ms begins
        advanceTimeBy(1_000)
        controller.pause()

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, engine.starts.size)
        assertTrue(controller.state.value is PlaybackState.Paused)

        controller.resume()
        advanceTimeBy(2_199)
        runCurrent()
        assertEquals(1, engine.starts.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, engine.starts.size)
    }

    @Test
    fun sleepTimerAtEndOfAyahStopsAfterThatAyah() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(2, 2), AyahRef(2, 4))
        controller.setSleepTimer(SleepTimer.EndOfAyah)
        runCurrent()
        assertNull(preparedFileName())

        endPlays(1)
        assertEquals(PlaybackState.Idle, controller.state.value)
        assertEquals(1, engine.starts.size)
        assertNull(controller.sleepTimer.value)
    }

    @Test
    fun sleepTimerAtEndOfSurahStopsWhenTheNextSurahWouldBegin() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 2), AyahRef(2, 2))
        controller.setSleepTimer(SleepTimer.EndOfSurah)

        endPlays(1)
        assertTrue(controller.state.value is PlaybackState.Playing)
        endPlays(1)
        assertEquals(PlaybackState.Idle, controller.state.value)
        assertEquals(listOf("001002.mp3", "001003.mp3"), fileNames())
    }

    @Test
    fun sleepTimerInMinutesStopsPlaybackWhenItRunsOut() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(2, 2), AyahRef(2, 4))
        controller.setSleepTimer(SleepTimer.Minutes(15))
        assertEquals(SleepTimer.Minutes(15), controller.sleepTimer.value)

        advanceTimeBy(15 * 60_000L - 1)
        runCurrent()
        assertTrue(controller.state.value is PlaybackState.Playing)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(PlaybackState.Idle, controller.state.value)
        assertNull(controller.sleepTimer.value)
    }

    @Test
    fun cancellingTheSleepTimerKeepsPlaying() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(2, 2), AyahRef(2, 4))
        controller.setSleepTimer(SleepTimer.Minutes(15))
        controller.setSleepTimer(null)
        advanceTimeBy(16 * 60_000L)
        runCurrent()
        assertTrue(controller.state.value is PlaybackState.Playing)
    }

    @Test
    fun unpluggingHeadphonesPausesAndResumeContinues() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(2, 2), AyahRef(2, 3))

        engine.listener!!.onPlayWhenReadyChangedExternally(false)
        assertTrue(controller.state.value is PlaybackState.Paused)

        engine.listener!!.onPlayWhenReadyChangedExternally(true)
        assertTrue(controller.state.value is PlaybackState.Playing)
    }

    @Test
    fun unpluggingHeadphonesDuringTheSilenceHoldsTheNextAyah() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(pauseAfterAyah = PauseAfterAyah.Once))
        controller.play("alafasy", AyahRef(1, 1), AyahRef(1, 2))
        endPlays(1)

        engine.listener!!.onPlayWhenReadyChangedExternally(false)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, engine.starts.size)
        assertTrue(controller.state.value is PlaybackState.Paused)
    }
}
