package app.awn.quran.core.data

/**
 * A reciter from `reciters.json`. The URL patterns use `{SSS}` for the surah and `{AAA}` for the
 * ayah, both zero-padded to three digits. [ayahUrlPattern] and [timingPack] are null when the
 * reciter has no per-ayah files or no timings.
 */
data class Reciter(
    val id: String,
    val name: String,
    val surahUrlPattern: String,
    val ayahUrlPattern: String?,
    val wordByWord: Boolean,
    val timingPack: String?,
)

/** Start and end of one word, in milliseconds from the start of the file at [AyahAudio.url]. */
data class WordTiming(val position: Int, val startMs: Int, val endMs: Int)

/**
 * The file that holds an ayah and where the ayah lies inside it.
 *
 * A reciter with one file per ayah has [startMs] 0 and [isInWholeSurahFile] false, and [durationMs]
 * is the length of that file. A reciter with one file per surah has the surah file as [url],
 * [isInWholeSurahFile] true, [startMs] where the ayah begins and [durationMs] where it ends, both
 * in milliseconds from the start of that file. So in both cases the ayah plays from [startMs] to
 * [durationMs], and [durationMs] is null when the ayah runs to the end of the file or its end is
 * unknown. [words] is empty when the ayah has no word timings, and its times are in milliseconds
 * from the start of the file at [url].
 *
 * [startByte] and [endByte] are the bytes of the file that hold the ayah, measured by walking the
 * file's MP3 frames, so a download can fetch just those bytes. [startByte] is the start of a frame
 * a few frames before the ayah, so the decoder has the audio it refers back to, and [startByteMs]
 * is the time of that frame in the file. They are null for per-ayah files, and [endByte] is null
 * when the ayah runs to the end of the file. They are measured because a file's bitrate is not
 * always constant: one Raad surah file has a 320 kbps run in the middle of a 128 kbps recording, so
 * seconds times 16,000 would land in the wrong place.
 */
data class AyahAudio(
    val ayah: AyahRef,
    val url: String,
    val durationMs: Int?,
    val words: List<WordTiming>,
    val startMs: Int = 0,
    val isInWholeSurahFile: Boolean = false,
    val startByte: Long? = null,
    val endByte: Long? = null,
    val startByteMs: Int? = null,
)

interface ReciterRepository {

    /** Returns the reciters in the manifest, in display order. */
    suspend fun reciters(): List<Reciter>

    /**
     * Returns the audio file and word timings of [ayah], or null when [reciterId] is unknown, or
     * the reciter has neither a per-ayah file for it nor a timing pack that places it in a surah
     * file.
     */
    suspend fun ayahAudio(reciterId: String, ayah: AyahRef): AyahAudio?
}

/**
 * The reciter whose voice plays single words until the user picks another one: Husary reading
 * slowly for learners.
 */
const val DEFAULT_WORD_RECITER_ID = "husary_muallim"

/**
 * The reciter whose voice plays a single word: the [chosen] one when it has word timings, else
 * [DEFAULT_WORD_RECITER_ID] when it has them, else the [listening] reciter when it has them, else
 * the first of [withTimings], else null. A reciter the user chose always wins over the default.
 */
fun wordReciterIdFor(chosen: String?, listening: String, withTimings: List<String>): String? =
    when {
        chosen != null && chosen in withTimings -> chosen
        DEFAULT_WORD_RECITER_ID in withTimings -> DEFAULT_WORD_RECITER_ID
        listening in withTimings -> listening
        else -> withTimings.firstOrNull()
    }
