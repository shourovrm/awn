package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId

/** The three text fields of the Go to page card, exactly as the user typed them. */
data class GoToFields(val page: String = "", val surah: String = "", val ayah: String = "")

/** Why the numbers typed in the Go to page card cannot be used, with the limit that applies. */
sealed interface GoToProblem {
    data class PageOutOfRange(val pageCount: Int) : GoToProblem

    data class SurahOutOfRange(val surahCount: Int) : GoToProblem

    data class AyahOutOfRange(val surah: Int, val ayahCount: Int) : GoToProblem
}

/**
 * Reads a number typed with any decimal digits, such as Bangla ০-৯ or Arabic-Indic ٠-٩, so a reader
 * on a Bangla keyboard can type page numbers in Bangla. Returns null for empty text or text with a
 * character that is not a digit.
 */
internal fun parseTypedNumber(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || !trimmed.all { it.digitToIntOrNull() != null }) return null
    return trimmed.map { it.digitToInt() }.joinToString(separator = "").toIntOrNull()
}

/**
 * Keeps the page field and the surah and ayah fields in step: editing one side fills in the other
 * when the typed numbers are valid, and leaves it alone otherwise. Filled-in numbers are written
 * with [formatNumber], which the card sets to the digits of the app language.
 */
internal class GoToPageResolver(
    private val quranText: QuranTextRepository,
    private val script: ScriptId,
    private val chapters: List<Chapter>,
    private val formatNumber: (Int) -> String = Int::toString,
) {
    private val pageCount = quranText.pageCount(script)

    /** Page 312 gives surah 19 ayah 96: the fields show the first ayah that starts on the page. */
    @Suppress("ReturnCount") // An invalid field leaves the others as typed.
    suspend fun afterPageEdited(fields: GoToFields): GoToFields {
        val page = validPage(fields.page) ?: return fields
        val firstAyah = quranText.ayahsOnPage(page, script).firstOrNull() ?: return fields
        return fields.copy(
            surah = formatNumber(firstAyah.surah),
            ayah = formatNumber(firstAyah.ayah),
        )
    }

    /**
     * A valid surah alone gives the page its first ayah is on; with a valid ayah, that ayah's page.
     */
    @Suppress("ReturnCount") // An invalid field leaves the others as typed.
    suspend fun afterSurahOrAyahEdited(fields: GoToFields): GoToFields {
        val chapter = chapters.firstOrNull { it.number == parseTypedNumber(fields.surah) }
        if (chapter == null) return fields
        val ayahNumber = parseTypedNumber(fields.ayah) ?: FIRST_AYAH
        if (ayahNumber !in FIRST_AYAH..chapter.versesCount) return fields
        val page = quranText.pageOf(AyahRef(chapter.number, ayahNumber), script)
        return fields.copy(page = formatNumber(page))
    }

    /**
     * The first typed number that is outside the book, or null when every typed number fits. An
     * empty field is not a problem; it only keeps Go from working.
     */
    @Suppress("ReturnCount") // One early return per field, in the order the fields are shown.
    fun problemOf(fields: GoToFields): GoToProblem? {
        if (fields.page.isNotBlank() && validPage(fields.page) == null) {
            return GoToProblem.PageOutOfRange(pageCount)
        }
        val chapter = chapters.firstOrNull { it.number == parseTypedNumber(fields.surah) }
        if (fields.surah.isNotBlank() && chapter == null) {
            return GoToProblem.SurahOutOfRange(chapters.size)
        }
        val ayah = parseTypedNumber(fields.ayah)
        if (
            chapter != null && fields.ayah.isNotBlank() && ayah !in FIRST_AYAH..chapter.versesCount
        ) {
            return GoToProblem.AyahOutOfRange(chapter.number, chapter.versesCount)
        }
        return null
    }

    /** The page the Go button opens, or null while a typed number is wrong or the page is empty. */
    fun targetPage(fields: GoToFields): Int? =
        if (problemOf(fields) == null) validPage(fields.page) else null

    private fun validPage(text: String): Int? =
        parseTypedNumber(text)?.takeIf { it in 1..pageCount }

    private companion object {
        const val FIRST_AYAH = 1
    }
}
