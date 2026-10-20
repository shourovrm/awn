package app.awn.quran.core.data.bookmarks

import androidx.room.withTransaction
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Bookmark
import app.awn.quran.core.data.BookmarkRepository
import app.awn.quran.core.data.RecentPage
import app.awn.quran.core.data.SavedAyah
import app.awn.quran.core.data.database.AwnDatabase
import app.awn.quran.core.data.database.BookmarkedPageEntity
import app.awn.quran.core.data.database.RecentPageEntity
import app.awn.quran.core.data.database.SavedAyahEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Suppress("TooManyFunctions") // The BookmarkRepository interface itself has 9 members.
/** Stores bookmarks, saved ayahs and recent pages in the [AwnDatabase]. */
class RoomBookmarkRepository(
    private val database: AwnDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : BookmarkRepository {

    private val dao = database.userDataDao()
    private val exportFormat = BookmarkExportFormat()

    override fun bookmarks(): Flow<List<Bookmark>> =
        dao.observeBookmarkedPages().map { rows -> rows.map { Bookmark(it.page, it.createdAt) } }

    override suspend fun toggleBookmark(page: Int) {
        requirePage(page)
        dao.toggleBookmarkedPage(page, clock())
    }

    override fun savedAyahs(): Flow<List<SavedAyah>> =
        dao.observeSavedAyahs().map { rows -> rows.map(::toSavedAyah) }

    override suspend fun saveAyah(ayah: AyahRef, note: String?) {
        requireAyah(ayah)
        dao.upsertSavedAyah(SavedAyahEntity(ayah.surah, ayah.ayah, note, clock()))
    }

    override suspend fun removeSavedAyah(ayah: AyahRef) {
        dao.deleteSavedAyah(ayah.surah, ayah.ayah)
    }

    override fun recentPages(): Flow<List<RecentPage>> =
        dao.observeRecentPages().map { rows -> rows.map { RecentPage(it.page, it.openedAt) } }

    override suspend fun recordPageOpened(page: Int) {
        requirePage(page)
        dao.recordPageOpened(page, clock(), RECENT_PAGE_LIMIT)
    }

    override suspend fun exportJson(): String =
        exportFormat.write(
            BookmarkExport(
                bookmarks = dao.bookmarkedPages().map { Bookmark(it.page, it.createdAt) },
                savedAyahs = dao.savedAyahs().map(::toSavedAyah),
                recentPages = dao.recentPages().map { RecentPage(it.page, it.openedAt) },
            ),
        )

    override suspend fun importJson(json: String) {
        // Parse everything before touching the database so a bad file changes nothing.
        val imported = exportFormat.read(json)
        database.withTransaction {
            for (bookmark in imported.bookmarks) {
                if (dao.countBookmarkedPage(bookmark.page) == 0) {
                    dao.upsertBookmarkedPage(
                        BookmarkedPageEntity(bookmark.page, bookmark.createdAt),
                    )
                }
            }
            for (saved in imported.savedAyahs) {
                val existing = dao.savedAyah(saved.ayah.surah, saved.ayah.ayah)
                if (existing == null || existing.createdAt < saved.createdAt) {
                    dao.upsertSavedAyah(
                        SavedAyahEntity(
                            saved.ayah.surah,
                            saved.ayah.ayah,
                            saved.note,
                            saved.createdAt,
                        ),
                    )
                }
            }
            for (recent in imported.recentPages) {
                val existing = dao.recentPage(recent.page)
                if (existing == null || existing.openedAt < recent.openedAt) {
                    dao.upsertRecentPage(RecentPageEntity(recent.page, recent.openedAt))
                }
            }
            dao.trimRecentPages(RECENT_PAGE_LIMIT)
        }
    }

    private fun toSavedAyah(row: SavedAyahEntity) =
        SavedAyah(AyahRef(row.surah, row.ayah), row.note, row.createdAt)

    private fun requirePage(page: Int) {
        require(page in FIRST_PAGE..LAST_PAGE) { "Page must be 1..604 but was $page." }
    }

    private fun requireAyah(ayah: AyahRef) {
        require(ayah.surah in 1..LAST_SURAH && ayah.ayah >= 1) { "Not a valid ayah: $ayah." }
    }

    companion object {
        const val RECENT_PAGE_LIMIT = 50
        const val FIRST_PAGE = 1
        const val LAST_PAGE = 604
        const val LAST_SURAH = 114
    }
}
