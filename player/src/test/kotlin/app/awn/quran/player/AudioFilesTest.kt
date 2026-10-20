package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioFilesTest {

    @Test
    fun ayahUrlIsZeroPaddedToThreeAndThreeDigits() {
        assertEquals(
            "https://mirrors.quranicaudio.com/everyayah/Husary_64kbps/002255.mp3",
            AudioUrls.ayahUrl(
                "https://mirrors.quranicaudio.com/everyayah/Husary_64kbps/{SSS}{AAA}.mp3",
                AyahRef(2, 255),
            ),
        )
    }

    @Test
    fun surahUrlIsZeroPadded() {
        assertEquals(
            "https://download.quranicaudio.com/quran/x/005.mp3",
            AudioUrls.surahUrl("https://download.quranicaudio.com/quran/x/{SSS}.mp3", 5),
        )
    }

    @Test
    fun fallbackSwitchesComToOrgOnlyOnQuranicAudioHosts() {
        assertEquals(
            "https://mirrors.quranicaudio.org/everyayah/a/001001.mp3",
            AudioUrls.fallbackUrl("https://mirrors.quranicaudio.com/everyayah/a/001001.mp3"),
        )
        assertEquals(
            "https://download.quranicaudio.org/quran/a/001.mp3",
            AudioUrls.fallbackUrl("https://download.quranicaudio.com/quran/a/001.mp3"),
        )
        assertNull(AudioUrls.fallbackUrl("https://everyayah.com/data/a/001001.mp3"))
        assertNull(AudioUrls.fallbackUrl("https://mirrors.quranicaudio.org/a.mp3"))
    }

    @Test
    fun keysMapToFilesAndRejectPathTricks() {
        val store = LocalAudioStore(File("/data/audio"))
        assertEquals(File("/data/audio/alafasy/001002.mp3"), store.fileForKey("alafasy/001002.mp3"))
        assertEquals(File("/data/audio/alafasy/114.mp3"), store.fileForKey("alafasy/114.mp3"))
        assertNull(store.fileForKey("../etc/001002.mp3"))
        assertNull(store.fileForKey("alafasy/../../x.mp3"))
        assertNull(store.fileForKey("alafasy/001002.mp3.part"))
        assertThrows(IllegalArgumentException::class.java) { store.reciterDirectory("../x") }
    }

    @Test
    fun playlistCrossesSurahsAndDefaultsToEndOfSurah() = runBlocking {
        val builder = PlaylistBuilder(fakeQuranText())
        assertEquals(listOf(AyahRef(1, 2), AyahRef(1, 3)), builder.build(AyahRef(1, 2), null))
        assertEquals(
            listOf(AyahRef(1, 3), AyahRef(2, 1), AyahRef(2, 2)),
            builder.build(AyahRef(1, 3), AyahRef(2, 2)),
        )
    }

    @Test
    fun playlistRejectsBackwardsAndMissingAyahs() {
        val builder = PlaylistBuilder(fakeQuranText())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { builder.build(AyahRef(2, 2), AyahRef(1, 1)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { builder.build(AyahRef(1, 4), null) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { builder.build(AyahRef(3, 1), null) }
        }
    }
}
