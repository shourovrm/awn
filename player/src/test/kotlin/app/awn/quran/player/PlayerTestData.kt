package app.awn.quran.player

import app.awn.quran.core.data.AyahAudio
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.WordTiming
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fakes.FakeReciterRepository

fun chapter(number: Int, verses: Int) =
    Chapter(number, "", "Surah $number", null, verses, "makkah", false)

/** Surah 1 has 3 ayahs and surah 2 has 4, which is enough to cross a surah boundary. */
fun fakeQuranText() = FakeQuranTextRepository(chapterList = listOf(chapter(1, 3), chapter(2, 4)))

val timedReciter =
    Reciter(
        id = "alafasy",
        name = "Alafasy",
        surahUrlPattern = "https://download.quranicaudio.com/quran/alafasy/{SSS}.mp3",
        ayahUrlPattern =
            "https://mirrors.quranicaudio.com/everyayah/Alafasy_128kbps/{SSS}{AAA}.mp3",
        wordByWord = true,
        timingPack = "alafasy",
    )

val surahOnlyReciter =
    Reciter(
        id = "raad",
        name = "Raad",
        surahUrlPattern = "https://download.quranicaudio.com/quran/raad/mp3/{SSS}.mp3",
        ayahUrlPattern = null,
        wordByWord = false,
        timingPack = null,
    )

/** Three words at 100-400, 500-900 and 1000-1500 ms. */
val threeWordTimings =
    listOf(WordTiming(1, 100, 400), WordTiming(2, 500, 900), WordTiming(3, 1000, 1500))

fun fakeReciters(): FakeReciterRepository {
    val repository = FakeReciterRepository()
    repository.reciterList += listOf(timedReciter, surahOnlyReciter)
    repository.audio["alafasy" to AyahRef(1, 1)] =
        AyahAudio(
            ayah = AyahRef(1, 1),
            url = "https://mirrors.quranicaudio.com/everyayah/Timed_64kbps/001001.mp3",
            durationMs = 1_600,
            words = threeWordTimings,
        )
    return repository
}
