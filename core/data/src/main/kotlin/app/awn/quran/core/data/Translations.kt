package app.awn.quran.core.data

/**
 * A translation pack file stem: `en_saheeh`, `en_abdul_haleem`, `en_transliteration`, `bn_zakaria`,
 * `bn_quantum`.
 */
data class TranslationId(val id: String)

data class TranslationInfo(
    val id: TranslationId,
    val name: String,
    val language: String,
    val installed: Boolean,
)

/**
 * A translation passage. When a publisher merges several ayahs, [ayahFrom] and [ayahTo] give the
 * range and the same [text] is returned for each ayah in it; otherwise both equal [ayah]'s number.
 */
data class TranslationText(val ayah: AyahRef, val ayahFrom: Int, val ayahTo: Int, val text: String)

interface TranslationRepository {

    /** Returns every translation in the manifest, with [TranslationInfo.installed] set. */
    suspend fun available(): List<TranslationInfo>

    /** Returns the passage for [ayah], or null when the pack [id] is not installed. */
    suspend fun text(id: TranslationId, ayah: AyahRef): TranslationText?

    /**
     * Returns the word-by-word meanings of [ayah] for [language] ("en" or "bn"), one entry per word
     * position. Returns an empty list when the word pack is not installed.
     */
    suspend fun wordByWord(language: String, ayah: AyahRef): List<String>
}
