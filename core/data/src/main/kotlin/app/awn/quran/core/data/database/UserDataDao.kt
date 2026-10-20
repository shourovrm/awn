package app.awn.quran.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Suppress(
    "TooManyFunctions",
) // One DAO per database keeps the three user tables in one transaction scope.
@Dao
abstract class UserDataDao {

    @Query("SELECT * FROM bookmarked_page ORDER BY createdAt DESC, page")
    abstract fun observeBookmarkedPages(): Flow<List<BookmarkedPageEntity>>

    @Query("SELECT * FROM bookmarked_page ORDER BY createdAt DESC, page")
    abstract suspend fun bookmarkedPages(): List<BookmarkedPageEntity>

    @Query("SELECT COUNT(*) FROM bookmarked_page WHERE page = :page")
    abstract suspend fun countBookmarkedPage(page: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBookmarkedPage(bookmark: BookmarkedPageEntity)

    @Query("DELETE FROM bookmarked_page WHERE page = :page")
    abstract suspend fun deleteBookmarkedPage(page: Int)

    @Transaction
    open suspend fun toggleBookmarkedPage(page: Int, now: Long) {
        if (countBookmarkedPage(page) > 0) {
            deleteBookmarkedPage(page)
        } else {
            upsertBookmarkedPage(BookmarkedPageEntity(page, now))
        }
    }

    @Query("SELECT * FROM saved_ayah ORDER BY createdAt DESC, surah, ayah")
    abstract fun observeSavedAyahs(): Flow<List<SavedAyahEntity>>

    @Query("SELECT * FROM saved_ayah ORDER BY createdAt DESC, surah, ayah")
    abstract suspend fun savedAyahs(): List<SavedAyahEntity>

    @Query("SELECT * FROM saved_ayah WHERE surah = :surah AND ayah = :ayah")
    abstract suspend fun savedAyah(surah: Int, ayah: Int): SavedAyahEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSavedAyah(savedAyah: SavedAyahEntity)

    @Query("DELETE FROM saved_ayah WHERE surah = :surah AND ayah = :ayah")
    abstract suspend fun deleteSavedAyah(surah: Int, ayah: Int)

    @Query("SELECT * FROM recent_page ORDER BY openedAt DESC, page")
    abstract fun observeRecentPages(): Flow<List<RecentPageEntity>>

    @Query("SELECT * FROM recent_page ORDER BY openedAt DESC, page")
    abstract suspend fun recentPages(): List<RecentPageEntity>

    @Query("SELECT * FROM recent_page WHERE page = :page")
    abstract suspend fun recentPage(page: Int): RecentPageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRecentPage(recentPage: RecentPageEntity)

    /** Keeps only the [limit] most recently opened pages. */
    @Query(
        "DELETE FROM recent_page WHERE page NOT IN " +
            "(SELECT page FROM recent_page ORDER BY openedAt DESC, page LIMIT :limit)",
    )
    abstract suspend fun trimRecentPages(limit: Int)

    @Transaction
    open suspend fun recordPageOpened(page: Int, now: Long, limit: Int) {
        upsertRecentPage(RecentPageEntity(page, now))
        trimRecentPages(limit)
    }
}
