package app.awn.quran.feature.learn.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitProgressDao {
    @Query("SELECT * FROM unit_progress WHERE lesson_id = :lessonId")
    suspend fun find(lessonId: String): UnitProgressEntity?

    @Query("SELECT * FROM unit_progress") fun observeAll(): Flow<List<UnitProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: UnitProgressEntity)
}
