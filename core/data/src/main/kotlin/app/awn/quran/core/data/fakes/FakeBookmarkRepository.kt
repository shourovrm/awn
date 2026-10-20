package app.awn.quran.core.data.fakes

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Bookmark
import app.awn.quran.core.data.BookmarkRepository
import app.awn.quran.core.data.RecentPage
import app.awn.quran.core.data.SavedAyah
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory bookmarks for tests. [clock] supplies the timestamps. [exportJson] returns an opaque
 * placeholder and [importJson] ignores its input, because the real format belongs to :core:data.
 */
class FakeBookmarkRepository(private val clock: () -> Long = System::currentTimeMillis) :
    BookmarkRepository {

    private val bookmarkState = MutableStateFlow<List<Bookmark>>(emptyList())
    private val savedAyahState = MutableStateFlow<List<SavedAyah>>(emptyList())
    private val recentPageState = MutableStateFlow<List<RecentPage>>(emptyList())

    override fun bookmarks(): Flow<List<Bookmark>> = bookmarkState

    override suspend fun toggleBookmark(page: Int) {
        val current = bookmarkState.value
        bookmarkState.value =
            if (current.any { it.page == page }) {
                current.filterNot { it.page == page }
            } else {
                listOf(Bookmark(page, clock())) + current
            }
    }

    override fun savedAyahs(): Flow<List<SavedAyah>> = savedAyahState

    override suspend fun saveAyah(ayah: AyahRef, note: String?) {
        savedAyahState.value =
            listOf(SavedAyah(ayah, note, clock())) +
                savedAyahState.value.filterNot { it.ayah == ayah }
    }

    override suspend fun removeSavedAyah(ayah: AyahRef) {
        savedAyahState.value = savedAyahState.value.filterNot { it.ayah == ayah }
    }

    override fun recentPages(): Flow<List<RecentPage>> = recentPageState

    override suspend fun recordPageOpened(page: Int) {
        recentPageState.value =
            listOf(RecentPage(page, clock())) + recentPageState.value.filterNot { it.page == page }
    }

    override suspend fun exportJson(): String = "{}"

    override suspend fun importJson(json: String) = Unit
}
