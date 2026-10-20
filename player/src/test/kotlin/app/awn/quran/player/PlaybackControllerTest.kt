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

private class FakePlaybackEngine : PlaybackEngine {

    class Start(val source: AudioSource, val startMs: Long, val stopAtMs: Long?)

    override var listener: PlaybackEngine.Listener? = null
    val starts = mutableListOf<Start>()
    var position = 0L
    var paused = false
    var stopped = false
    var lastSpeed = 1f

    override fun setSpeed(speed: Float) {
        lastSpeed = speed
    }

    override fun start(source: AudioSource, startMs: Long, stopAtMs: Long?) {
        starts += Start(source, startMs, stopAtMs)
        position = startMs
        paused = false
    }

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
    }

    override fun stop() {
        stopped = true
    }

    override fun positionMs() = position

    override fun durationMs(): Long? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {

    private val engine = FakePlaybackEngine()

    private fun TestScope.controller(): PlaybackController {
        val store = LocalAudioStore(java.io.File("unused"))
        return PlaybackController(
            engine = engine,
            resolver = ReciterAudioResolver(fakeReciters(), store, fakeQuranText()),
            playlistBuilder = PlaylistBuilder(fakeQuranText()),
            scope = backgroundScope,
        )
    }

    private fun playedAyahs() = engine.starts.map { it.source.title.substringAfterLast(' ') }

    @Test
    fun playsEveryAyahOfTheSurahInOrderThenStops() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 2), null)
        assertEquals(listOf("1:2"), playedAyahs())

        engine.listener!!.onEnded()
        runCurrent()
        engine.listener!!.onEnded()
        runCurrent()

        assertEquals(listOf("1:2", "1:3"), playedAyahs())
        assertEquals(PlaybackState.Idle, controller.state.value)
    }

    @Test
    fun playsAcrossSurahBoundaryWhenRangeEndsInNextSurah() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 3), AyahRef(2, 1))
        engine.listener!!.onEnded()
        runCurrent()
        assertEquals(listOf("1:3", "2:1"), playedAyahs())
    }

    @Test
    fun repeatPlaysEachAyahTheRequestedNumberOfExtraTimes() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(repeatEachAyah = 3))
        controller.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        repeat(5) {
            engine.listener!!.onEnded()
            runCurrent()
        }
        assertEquals(listOf("1:2", "1:2", "1:2", "1:3", "1:3", "1:3"), playedAyahs())
    }

    @Test
    fun repeatZeroPlaysEachAyahOnce() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(repeatEachAyah = 1))
        controller.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        engine.listener!!.onEnded()
        runCurrent()
        assertEquals(listOf("1:2", "1:3"), playedAyahs())
    }

    @Test
    fun usesTimingPackUrlWhenPresentAndAyahPatternOtherwise() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 1), AyahRef(1, 2))
        engine.listener!!.onEnded()
        runCurrent()
        assertEquals(
            "https://mirrors.quranicaudio.com/everyayah/Timed_64kbps/001001.mp3",
            engine.starts[0].source.url,
        )
        assertEquals(
            "https://mirrors.quranicaudio.com/everyayah/Alafasy_128kbps/001002.mp3",
            engine.starts[1].source.url,
        )
        assertEquals("alafasy/001002.mp3", engine.starts[1].source.localKey)
    }

    @Test
    fun wordSeekStartsAtWordStartAndStopsAtWordEnd() = runTest {
        val controller = controller()
        controller.playWord("alafasy", AyahRef(1, 1), 2)
        assertEquals(500L, engine.starts.single().startMs)
        assertEquals(900L, engine.starts.single().stopAtMs)

        engine.listener!!.onEnded()
        runCurrent()
        assertEquals(PlaybackState.Idle, controller.state.value)
    }

    @Test
    fun wordSeekEndIsCutAtKnownFileDuration() = runTest {
        val controller = controller()
        // Duration of 1:1 in the fixture is 1600 ms; the last word ends at 1500 ms, inside it.
        controller.playWord("alafasy", AyahRef(1, 1), 3)
        assertEquals(1500L, engine.starts.single().stopAtMs)
    }

    @Test
    fun wordSeekWithoutTimingIsAnError() = runTest {
        val controller = controller()
        controller.playWord("alafasy", AyahRef(1, 1), 9)
        assertTrue(controller.state.value is PlaybackState.Error)
        assertTrue(engine.starts.isEmpty())
    }

    @Test
    fun wordSeekForReciterWithoutTimingsIsAnError() = runTest {
        val controller = controller()
        controller.playWord("raad", AyahRef(1, 1), 1)
        assertTrue(controller.state.value is PlaybackState.Error)
    }

    @Test
    fun repeatedWordRestartsAtWordStart() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(repeatEachAyah = 2))
        controller.playWord("alafasy", AyahRef(1, 1), 1)
        engine.listener!!.onEnded()
        runCurrent()
        assertEquals(listOf(100L, 100L), engine.starts.map { it.startMs })
    }

    @Test
    fun wordPositionFollowsPlaybackPositionAtFiftyMillisecondSteps() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 1), null)
        assertNull((controller.state.value as PlaybackState.Playing).wordPosition)

        engine.position = 560
        advanceTimeBy(60)
        assertEquals(2, (controller.state.value as PlaybackState.Playing).wordPosition)

        engine.position = 1_020
        advanceTimeBy(60)
        assertEquals(3, (controller.state.value as PlaybackState.Playing).wordPosition)
    }

    @Test
    fun pauseKeepsPositionAndResumeContinues() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 1), null)
        engine.position = 700
        controller.pause()
        assertEquals(PlaybackState.Paused("alafasy", AyahRef(1, 1), 700L), controller.state.value)
        controller.resume()
        assertTrue(controller.state.value is PlaybackState.Playing)
    }

    @Test
    fun failureRetriesOnFallbackDomainOnceThenReportsError() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 2), AyahRef(1, 2))
        engine.listener!!.onFailed("network")
        assertEquals(
            "https://mirrors.quranicaudio.org/everyayah/Alafasy_128kbps/001002.mp3",
            engine.starts[1].source.url,
        )
        engine.listener!!.onFailed("network")
        assertTrue(controller.state.value is PlaybackState.Error)
    }

    @Test
    fun surahFileReciterPlaysTheSurahOnceAndSkipsItsAyahs() = runTest {
        val controller = controller()
        controller.play("raad", AyahRef(1, 1), AyahRef(2, 2))
        engine.listener!!.onEnded()
        runCurrent()
        assertEquals(
            listOf(
                "https://download.quranicaudio.com/quran/raad/mp3/001.mp3",
                "https://download.quranicaudio.com/quran/raad/mp3/002.mp3",
            ),
            engine.starts.map { it.source.url },
        )
    }

    @Test
    fun unknownAyahRangeIsAnError() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 99), null)
        assertTrue(controller.state.value is PlaybackState.Error)
    }

    private fun TestScope.endCurrentPlays(count: Int) {
        repeat(count) {
            engine.listener!!.onEnded()
            runCurrent()
        }
    }

    @Test
    fun repeatEachAyahAndRepeatRangeMultiplyPlays() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(repeatEachAyah = 2, repeatRange = 2))
        controller.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        endCurrentPlays(7)

        assertEquals(
            listOf("1:2", "1:2", "1:3", "1:3", "1:2", "1:2", "1:3", "1:3"),
            playedAyahs(),
        )
        assertTrue(controller.state.value is PlaybackState.Playing)

        endCurrentPlays(1)
        assertEquals(PlaybackState.Idle, controller.state.value)
    }

    @Test
    fun infiniteRangeRepeatNeverEndsTheRange() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(repeatRange = PlaybackOptions.INFINITE_REPEAT))
        controller.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        endCurrentPlays(40)

        assertEquals(41, engine.starts.size)
        assertEquals("1:2", playedAyahs()[40])
        assertTrue(controller.state.value is PlaybackState.Playing)
    }

    @Test
    fun infiniteAyahRepeatStaysOnTheAyah() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(repeatEachAyah = PlaybackOptions.INFINITE_REPEAT))
        controller.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        endCurrentPlays(25)

        assertEquals(setOf("1:2"), playedAyahs().toSet())
    }

    @Test
    fun rangeRepeatDoesNotRepeatASingleWord() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(repeatRange = 3))
        controller.playWord("alafasy", AyahRef(1, 1), 1)
        endCurrentPlays(1)

        assertEquals(1, engine.starts.size)
        assertEquals(PlaybackState.Idle, controller.state.value)
    }

    @Test
    fun nextAndPreviousCrossASurahBoundary() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 3), AyahRef(2, 2))

        controller.next()
        runCurrent()
        assertEquals("2:1", (controller.state.value as PlaybackState.Playing).ayah.toString())

        controller.previous()
        runCurrent()
        assertEquals("1:3", (controller.state.value as PlaybackState.Playing).ayah.toString())
    }

    @Test
    fun previousAfterThreeSecondsRestartsTheAyah() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        controller.next()
        runCurrent()
        engine.position = 3_500

        controller.previous()
        runCurrent()

        assertEquals(listOf("1:2", "1:3", "1:3"), playedAyahs())
    }

    @Test
    fun previousAtTheFirstAyahRestartsIt() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))

        controller.previous()
        runCurrent()

        assertEquals(listOf("1:2", "1:2"), playedAyahs())
    }

    @Test
    fun nextAfterTheLastAyahEndsTheRange() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        controller.next()
        runCurrent()
        controller.next()
        runCurrent()

        assertEquals(PlaybackState.Idle, controller.state.value)
    }

    @Test
    fun nextWhilePausedPlaysTheNextAyah() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        controller.pause()

        controller.next()
        runCurrent()

        assertEquals("1:3", (controller.state.value as PlaybackState.Playing).ayah.toString())
    }

    @Test
    fun nextWithSurahFileReciterJumpsToTheNextSurahFile() = runTest {
        val controller = controller()
        controller.play("raad", AyahRef(1, 1), AyahRef(2, 2))

        controller.next()
        runCurrent()

        assertEquals(
            "https://download.quranicaudio.com/quran/raad/mp3/002.mp3",
            engine.starts.last().source.url,
        )
    }

    @Test
    fun speedIsClampedAndGivenToTheEngine() = runTest {
        val controller = controller()
        controller.setOptions(PlaybackOptions(speed = 3f))
        assertEquals(1.5f, controller.options.value.speed)

        controller.setOptions(PlaybackOptions(speed = 0.25f))
        controller.play("alafasy", AyahRef(1, 2), null)
        assertEquals(0.75f, engine.lastSpeed)
    }

    @Test
    fun titleHoldsSurahNameAndAyahReference() = runTest {
        val controller = controller()
        controller.play("alafasy", AyahRef(2, 1), null)
        assertEquals("Surah 2 2:1", engine.starts.single().source.title)
        assertEquals("Alafasy", engine.starts.single().source.artist)
    }
}
