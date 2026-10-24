package app.awn.quran.core.data

/**
 * A print of the Quran text. [packId] is the pack id of the script in `scripts.json`. The IndoPak
 * print has its own font and a layout pack that holds its 15-line page layout.
 */
enum class ScriptId(val packId: String) {
    QpcHafs("qpc-hafs"),
    V1("v1"),
    V2("v2"),
    V4Tajweed("v4-tajweed"),
    IndoPak15("indopak-15"),
}

/**
 * One surah. [nameBangla] is the name Bangla readers say aloud ("আল-বাকারা"), null when the pack
 * has none; [nameBanglaMeaning] is what that name means ("বকনা-বাছুর-গরু"), null when the source
 * gives no meaning.
 */
data class Chapter(
    val number: Int,
    val nameArabic: String,
    val nameSimple: String,
    val nameBangla: String?,
    val versesCount: Int,
    val revelationPlace: String,
    val bismillahPre: Boolean,
    val nameBanglaMeaning: String? = null,
)

/**
 * One spoken word. [codeV1] and [codeV2] are private-use glyphs valid only with the font of the
 * page they sit on; they are null when the installed pack does not hold that print. [textNastaleeq]
 * is the word in the IndoPak font; it is set on the pages of an IndoPak script and null elsewhere.
 */
data class Word(
    val key: WordKey,
    val textUthmani: String,
    val codeV1: String?,
    val codeV2: String?,
    val textNastaleeq: String? = null,
)

/** One thing printed on a mushaf line. */
sealed interface LineItem {
    data class WordItem(val word: Word) : LineItem

    /**
     * The ayah-end sign of [ayah]. [glyphCode] is the private-use character that draws the printed
     * sign with the page font of the print; null when it is not known (fakes, Unicode scripts). On
     * an IndoPak page it is the end sign of the pack, a short string that the IndoPak font draws
     * with its number.
     */
    data class AyahEnd(val ayah: AyahRef, val glyphCode: String? = null) : LineItem

    /** The surah name banner above the first ayah of [surah]. */
    data class SurahHeader(val surah: Int) : LineItem

    /** The bismillah line printed above [surah]. */
    data class Basmala(val surah: Int) : LineItem
}

/**
 * One slot (1..15) of a page. Slots that hold only a header or the bismillah are included. On the
 * 14 IndoPak pages where a surah starts too near the top for its header, the header comes first as
 * extra lines numbered 0 and below (the previous page has no free slot to take it).
 */
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
