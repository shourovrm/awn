package app.awn.quran.core.data.fakes

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word

/**
 * In-memory text for tests in other modules. Fill [chapterList], [wordsByAyah] and [pages]; pages
 * are keyed by page number and returned for every script, labelled with the script asked for.
 * [layoutRequests] records every `(page, script)` asked, so tests can see what was loaded.
 */
class FakeQuranTextRepository(
    var chapterList: List<Chapter> = emptyList(),
    val wordsByAyah: MutableMap<AyahRef, List<Word>> = mutableMapOf(),
    val pages: MutableMap<Int, PageLayout> = mutableMapOf(),
    val pageByAyah: MutableMap<AyahRef, Int> = mutableMapOf(),
    private val totalPages: Int = 604,
) : QuranTextRepository {

    val layoutRequests: MutableList<Pair<Int, ScriptId>> = mutableListOf()

    override suspend fun chapters(): List<Chapter> = chapterList

    override suspend fun wordsOfAyah(ayah: AyahRef): List<Word> = wordsByAyah[ayah].orEmpty()

    override suspend fun pageLayout(page: Int, script: ScriptId): PageLayout {
        layoutRequests += page to script
        val layout = pages[page] ?: error("FakeQuranTextRepository has no layout for page $page.")
        return layout.copy(script = script)
    }

    override suspend fun pageOf(ayah: AyahRef, script: ScriptId): Int =
        pageByAyah[ayah] ?: error("FakeQuranTextRepository has no page for ayah $ayah.")

    override fun pageCount(script: ScriptId): Int = totalPages

    override suspend fun ayahsOnPage(page: Int, script: ScriptId): List<AyahRef> =
        pageByAyah.filterValues { it == page }.keys.sorted()
}
