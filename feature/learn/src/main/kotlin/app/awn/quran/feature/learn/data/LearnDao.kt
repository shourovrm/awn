package app.awn.quran.feature.learn.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** The time of an event, and the page it opened. */
data class PageOpenRow(val page: Int, val at: Long)

/** The time of an event on one ayah. */
data class AyahEventRow(val surah: Int, val ayah: Int, val at: Long)

@Dao
interface LearnDao {
    @Insert suspend fun insertEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCard(card: CardEntity)

    @Query("SELECT * FROM card WHERE surah = :surah AND ayah = :ayah")
    suspend fun findCard(surah: Int, ayah: Int): CardEntity?

    @Query("SELECT * FROM card ORDER BY due ASC, surah ASC, ayah ASC")
    fun observeCards(): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM event") fun observeEventCount(): Flow<Int>

    @Query("SELECT at FROM event ORDER BY at ASC") suspend fun allEventTimes(): List<Long>

    @Query(
        "SELECT page, at FROM event WHERE kind = '" +
            EventKind.PAGE_OPENED +
            "' AND page IS NOT NULL",
    )
    suspend fun pageOpens(): List<PageOpenRow>

    @Query(
        "SELECT surah, ayah, at FROM event WHERE kind = '" +
            EventKind.HIFZ_CLEAN +
            "' AND surah IS NOT NULL",
    )
    suspend fun cleanHifzCompletions(): List<AyahEventRow>
}
