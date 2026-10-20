package app.awn.quran.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("SELECT * FROM bookmark ORDER BY createdAtEpochSeconds DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmark WHERE id = :bookmarkId") suspend fun delete(bookmarkId: Long)
}
