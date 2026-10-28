package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.fakes.FakeReciterRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Playback of a reciter whose audio is one mp3 per surah. Ayah 1:2 of the fixture starts at 8000 ms
 * and ends at 12000 ms of the surah file; its clip starts at 7900 ms of that file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WindowedPlaybackTest {

    @get:Rule val folder = TemporaryFolder()

    private val engine = FakePlaybackEngine()

    private fun TestScope.controller(
        reciters: FakeReciterRepository = fakeWindowedReciters(),
    ): PlaybackController =
        PlaybackController(
            engine = engine,
            resolver =
                ReciterAudioResolver(
                    reciters,
                    LocalAudioStore(folder.root),
                    fakeQuranText(),
                    diskDispatcher = StandardTestDispatcher(testScheduler),
                ),
            playlistBuilder = PlaylistBuilder(fakeQuranText()),
            scope = backgroundScope,
            clockMs = { testScheduler.currentTime },
        )

    private fun putClipOnDisk(surah: Int, ayah: Int) {
        val name = AudioUrls.padded(surah) + AudioUrls.padded(ayah) + ".mp3"
        val clip = File(folder.root, "windowed/$name")
        clip.parentFile?.mkdirs()
        clip.writeBytes(ByteArray(1))
    }

    @Test
    fun clipStartsAtTheAyahLessTheClipStartAndStopsAtTheAyahEnd() = runTest {
        putClipOnDisk(1, 2)
        controller().play("windowed", AyahRef(1, 2), AyahRef(1, 2))

        val start = engine.starts.single()
        assertEquals(100L, start.startMs)
        assertEquals(4_100L, start.stopAtMs)
        assertEquals("windowed/001002.mp3", start.source.localKey)
        assertEquals(WINDOWED_SURAH_ONE_URL, start.source.url)
    }

    @Test
    fun lastAyahOfTheSurahHasNoStop() = runTest {
        putClipOnDisk(1, 3)
        controller().play("windowed", AyahRef(1, 3), AyahRef(1, 3))

        assertEquals(100L, engine.starts.single().startMs)
        assertNull(engine.starts.single().stopAtMs)
    }

    @Test
    fun perAyahReciterStillPlaysFromZeroWithNoStop() = runTest {
        controller().play("alafasy", AyahRef(1, 2), AyahRef(1, 2))

        assertEquals(0L, engine.starts.single().startMs)
        assertNull(engine.starts.single().stopAtMs)
    }

    @Test
    fun withoutByteDataTheSurahFilePlaysFromTheAyahStart() = runTest {
        controller(fakeWindowedReciters(withByteData = false))
            .play("windowed", AyahRef(1, 2), AyahRef(1, 2))

        val start = engine.starts.single()
        assertEquals(8_000L, start.startMs)
        assertEquals(12_000L, start.stopAtMs)
        assertEquals("windowed/001.mp3", start.source.localKey)
    }

    @Test
    fun clipThatIsNotOnDiskStreamsTheSurahFileAtTheAyahStart() = runTest {
        controller().play("windowed", AyahRef(1, 2), AyahRef(1, 2))

        val start = engine.starts.single()
        assertEquals(8_000L, start.startMs)
        assertEquals(12_000L, start.stopAtMs)
        assertEquals("windowed/001.mp3", start.source.localKey)
    }

    @Test
    fun wordWindowOfAClipIsMovedBackByTheClipStart() = runTest {
        putClipOnDisk(1, 2)
        // Word 1 of 1:2 is at 8100-8500 ms of the surah file, so 200-600 ms of its clip.
        controller().playWord("windowed", AyahRef(1, 2), 1)

        assertEquals(200L, engine.starts.single().startMs)
        assertEquals(600L, engine.starts.single().stopAtMs)
    }

    @Test
    fun wordWindowInTheSurahFileKeepsTheTimesOfThePack() = runTest {
        controller().playWord("windowed", AyahRef(1, 2), 1)

        assertEquals(8_100L, engine.starts.single().startMs)
        assertEquals(8_500L, engine.starts.single().stopAtMs)
    }

    @Test
    fun highlightedWordFollowsTheClipPosition() = runTest {
        putClipOnDisk(1, 2)
        val controller = controller()
        controller.play("windowed", AyahRef(1, 2), AyahRef(1, 2))

        // 700 ms into the clip is inside word 2 (8600-9000 ms of the surah file).
        engine.position = 700
        advanceTimeBy(60)

        assertEquals(2, (controller.state.value as PlaybackState.Playing).wordPosition)
    }

    @Test
    fun pauseAfterAyahLastsTheAyahsOwnLengthNotTheClipsOffset() = runTest {
        putClipOnDisk(1, 1)
        putClipOnDisk(1, 2)
        val controller = controller()
        controller.setOptions(PlaybackOptions(pauseAfterAyah = PauseAfterAyah.Once))
        controller.play("windowed", AyahRef(1, 1), AyahRef(1, 2))

        engine.listener!!.onEnded()
        runCurrent()
        assertEquals(1, engine.starts.size)
        advanceTimeBy(3_999)
        assertEquals(1, engine.starts.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, engine.starts.size)
    }

    @Test
    fun aWindowedFileIsNeverPreparedAsTheGaplessNextFile() = runTest {
        putClipOnDisk(1, 1)
        putClipOnDisk(1, 2)
        controller().play("windowed", AyahRef(1, 1), AyahRef(1, 2))
        runCurrent()

        assertTrue(engine.preparedNext.all { it == null })
    }

    @Test
    fun basmalahOfAWindowedReciterComesFromItsFirstAyahClip() = runTest {
        putClipOnDisk(1, 1)
        putClipOnDisk(2, 1)
        controller().play("windowed", AyahRef(2, 1), AyahRef(2, 1))

        assertEquals("windowed/001001.mp3", engine.starts[0].source.localKey)
        assertEquals(100L, engine.starts[0].startMs)
        assertEquals("Surah 2 2:1", engine.starts[0].source.title)
    }
}
