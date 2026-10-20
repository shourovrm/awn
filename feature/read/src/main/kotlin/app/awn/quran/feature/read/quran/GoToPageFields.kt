package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId

/** The three text fields of the Go to page dialog, exactly as the user typed them. */
data class GoToFields(val page: String = "", val surah: String = "", val ayah: String = "")

/**
 * Keeps the page field and the surah and ayah fields in step: editing one side fills in the other
 * when the typed numbers are valid, and leaves it alone otherwise.
 */
internal class GoToPageResolver(
    private val quranText: QuranTextRepository,
    private val script: ScriptId,
    private val chapters: List<Chapter>,
) {
    private val pageCount = quranText.pageCount(script)

    /** Page 312 gives surah 19 ayah 96: the fields show the first ayah that starts on the page. */
    @Suppress("ReturnCount") // An invalid field leaves the others as typed.
    suspend fun afterPageEdited(fields: GoToFields): GoToFields {
        val page = validPage(fields.page) ?: return fields
        val firstAyah = quranText.ayahsOnPage(page, script).firstOrNull() ?: return fields
        return fields.copy(surah = firstAyah.surah.toString(), ayah = firstAyah.ayah.toString())
    }

    /**
     * A valid surah alone gives the page its first ayah is on; with a valid ayah, that ayah's page.
     */
    @Suppress("ReturnCount") // An invalid field leaves the others as typed.
    suspend fun afterSurahOrAyahEdited(fields: GoToFields): GoToFields {
        val chapter = chapters.firstOrNull { it.number == fields.surah.trim().toIntOrNull() }
        if (chapter == null) return fields
        val ayahNumber = fields.ayah.trim().toIntOrNull() ?: FIRST_AYAH
        if (ayahNumber !in FIRST_AYAH..chapter.versesCount) return fields
        val page = quranText.pageOf(AyahRef(chapter.number, ayahNumber), script)
        return fields.copy(page = page.toString())
    }

    /** The page the Go button opens, or null while the page field is not a page of the book. */
    fun targetPage(fields: GoToFields): Int? = validPage(fields.page)

    private fun validPage(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 1..pageCount }

    private companion object {
        const val FIRST_AYAH = 1
    }
}
