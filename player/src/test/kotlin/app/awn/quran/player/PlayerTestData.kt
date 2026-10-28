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
fun fakeQuranText() =
    FakeQuranTextRepository(
        chapterList = listOf(chapter(1, 3), chapter(2, 4)),
        pageByAyah =
            mutableMapOf(
                AyahRef(1, 1) to 1,
                AyahRef(1, 2) to 1,
                AyahRef(1, 3) to 1,
                AyahRef(2, 1) to 2,
                AyahRef(2, 2) to 2,
                AyahRef(2, 3) to 3,
                AyahRef(2, 4) to 3,
            ),
    )

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

/** A reciter with one mp3 per surah whose timing pack places each ayah inside it. */
val windowedReciter =
    Reciter(
        id = "windowed",
        name = "Windowed",
        surahUrlPattern = "https://download.quranicaudio.com/quran/windowed/{SSS}.mp3",
        ayahUrlPattern = null,
        wordByWord = true,
        timingPack = "windowed",
    )

const val WINDOWED_SURAH_ONE_URL = "https://download.quranicaudio.com/quran/windowed/001.mp3"

/**
 * Ayah [number] of a surah of [lastAyah] ayahs in the surah file of [windowedReciter]: it starts at
 * `number * 4000` ms and ends 4000 ms later (null for the last ayah), its bytes are `number *
 * 32000` to `(number + 1) * 32000`, and its clip starts 100 ms before the ayah. Two words lie in
 * the first second of the ayah. With [withByteData] false the byte fields are null, as in a pack
 * that was not measured.
 */
fun windowedAyahAudio(
    ayah: AyahRef,
    lastAyah: Int,
    withByteData: Boolean = true,
): AyahAudio {
    val number = ayah.ayah
    val startMs = number * 4_000
    val isLast = number == lastAyah
    val surahUrl = AudioUrls.surahUrl(windowedReciter.surahUrlPattern, ayah.surah)
    return AyahAudio(
        ayah = ayah,
        url = surahUrl,
        durationMs = if (isLast) null else startMs + 4_000,
        words =
            listOf(
                WordTiming(1, startMs + 100, startMs + 500),
                WordTiming(2, startMs + 600, startMs + 1_000),
            ),
        startMs = startMs,
        isInWholeSurahFile = true,
        startByte = if (withByteData) number * 32_000L else null,
        endByte = if (withByteData && !isLast) (number + 1) * 32_000L else null,
        startByteMs = if (withByteData) startMs - 100 else null,
    )
}

/** [fakeReciters] plus [windowedReciter] with audio for every ayah of the two fixture surahs. */
fun fakeWindowedReciters(withByteData: Boolean = true): FakeReciterRepository {
    val repository = fakeReciters()
    repository.reciterList += windowedReciter
    for ((surah, lastAyah) in listOf(1 to 3, 2 to 4)) {
        for (number in 1..lastAyah) {
            val ayah = AyahRef(surah, number)
            repository.audio[windowedReciter.id to ayah] =
                windowedAyahAudio(ayah, lastAyah, withByteData)
        }
    }
    return repository
}
