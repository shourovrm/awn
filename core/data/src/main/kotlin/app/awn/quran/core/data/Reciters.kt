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

/** Start and end of one word, in milliseconds from the start of the ayah file. */
data class WordTiming(val position: Int, val startMs: Int, val endMs: Int)

/** [words] is empty when the ayah has no word timings; [durationMs] is null when unknown. */
data class AyahAudio(
    val ayah: AyahRef,
    val url: String,
    val durationMs: Int?,
    val words: List<WordTiming>,
)

interface ReciterRepository {

    /** Returns the reciters in the manifest, in display order. */
    suspend fun reciters(): List<Reciter>

    /**
     * Returns the audio file and word timings of [ayah], or null when [reciterId] is unknown or the
     * reciter has no per-ayah file for it.
     */
    suspend fun ayahAudio(reciterId: String, ayah: AyahRef): AyahAudio?
}
