package app.awn.quran.core.data

/** A print of the Quran text. [packId] is the pack id of the script in `scripts.json`. */
enum class ScriptId(val packId: String) {
    QpcHafs("qpc-hafs"),
    V1("v1"),
    V2("v2"),
    V4Tajweed("v4-tajweed"),
    IndoPak("indopak"),
}

/** One surah. [nameBangla] is null when the pack has no Bangla name. */
data class Chapter(
    val number: Int,
    val nameArabic: String,
    val nameSimple: String,
    val nameBangla: String?,
    val versesCount: Int,
    val revelationPlace: String,
    val bismillahPre: Boolean,
)

/**
 * One spoken word. [codeV1] and [codeV2] are private-use glyphs valid only with the font of the
 * page they sit on; they are null when the installed pack does not hold that print.
 */
data class Word(
    val key: WordKey,
    val textUthmani: String,
    val codeV1: String?,
    val codeV2: String?,
    val transliteration: String?,
)

/** One thing printed on a mushaf line. */
sealed interface LineItem {
    data class WordItem(val word: Word) : LineItem

    /**
     * The ayah-end sign of [ayah]. [glyphCode] is the private-use character that draws the printed
     * sign with the page font of the print; null when it is not known (fakes, Unicode scripts).
     */
    data class AyahEnd(val ayah: AyahRef, val glyphCode: String? = null) : LineItem

    /** The surah name banner above the first ayah of [surah]. */
    data class SurahHeader(val surah: Int) : LineItem

    /** The bismillah line printed above [surah]. */
    data class Basmala(val surah: Int) : LineItem
}

/** One slot (1..15) of a page. Slots that hold only a header or the bismillah are included. */
data class PageLine(val line: Int, val items: List<LineItem>)

data class PageLayout(val page: Int, val script: ScriptId, val lines: List<PageLine>)

interface QuranTextRepository {

    /** Returns all 114 chapters in surah order. */
    suspend fun chapters(): List<Chapter>

    /**
     * Returns the words of [ayah] in reading order, or an empty list when the ayah does not exist.
     */
    suspend fun wordsOfAyah(ayah: AyahRef): List<Word>

    /**
     * Returns the lines of [page] (1..604) in the print [script]. Throws when the script pack is
     * not installed or the page is out of range.
     */
    suspend fun pageLayout(page: Int, script: ScriptId): PageLayout

    /** Returns the page that holds the first word of [ayah] in the print [script]. */
    suspend fun pageOf(ayah: AyahRef, script: ScriptId): Int

    /** Returns the number of pages of the print [script] (604 for every shipped script). */
    fun pageCount(script: ScriptId): Int

    /** Returns the ayahs that have at least one word on [page], in reading order. */
    suspend fun ayahsOnPage(page: Int, script: ScriptId): List<AyahRef>
}
