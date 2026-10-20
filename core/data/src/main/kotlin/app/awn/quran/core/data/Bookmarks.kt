package app.awn.quran.core.data

import kotlinx.coroutines.flow.Flow

/** Timestamps are epoch milliseconds. */
data class Bookmark(val page: Int, val createdAt: Long)

data class SavedAyah(val ayah: AyahRef, val note: String?, val createdAt: Long)

data class RecentPage(val page: Int, val openedAt: Long)

interface BookmarkRepository {

    /** Emits the bookmarked pages, newest first, again after every change. */
    fun bookmarks(): Flow<List<Bookmark>>

    /** Adds the bookmark for [page], or removes it when it exists. */
    suspend fun toggleBookmark(page: Int)

    /** Emits the saved ayahs, newest first, again after every change. */
    fun savedAyahs(): Flow<List<SavedAyah>>

    /** Saves [ayah] with an optional [note]; saving an ayah again replaces its note. */
    suspend fun saveAyah(ayah: AyahRef, note: String?)

    suspend fun removeSavedAyah(ayah: AyahRef)

    /** Emits the recently opened pages, most recent first, one entry per page. */
    fun recentPages(): Flow<List<RecentPage>>

    suspend fun recordPageOpened(page: Int)

    /** Returns bookmarks, saved ayahs and recent pages as one JSON document. */
    suspend fun exportJson(): String

    /**
     * Merges the document written by [exportJson] into the stored data; throws on malformed input.
     */
    suspend fun importJson(json: String)
}
