package app.awn.quran.feature.hifz.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// One function per query the repository needs.
@Suppress("TooManyFunctions")
@Dao
interface HifzDao {

    @Query("SELECT * FROM plan WHERE id = 1") fun observePlan(): Flow<PlanEntity?>

    @Query("SELECT * FROM plan WHERE id = 1") suspend fun findPlan(): PlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlan(plan: PlanEntity)

    @Query("SELECT * FROM page_progress ORDER BY page ASC")
    fun observePages(): Flow<List<PageProgressEntity>>

    @Query("SELECT * FROM page_progress ORDER BY page ASC")
    suspend fun allPages(): List<PageProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPages(pages: List<PageProgressEntity>)

    @Query("DELETE FROM page_progress WHERE page IN (:pages)")
    suspend fun deletePages(pages: List<Int>)

    @Query("DELETE FROM page_progress") suspend fun deleteAllPages()

    /** Returns the new session's id, which its ayah and page answers refer to. */
    @Insert suspend fun insertSession(session: SessionEntity): Long

    @Insert suspend fun insertSessionAyahs(ayahs: List<SessionAyahEntity>)

    @Insert suspend fun insertSessionPages(pages: List<SessionPageEntity>)

    @Transaction
    @Query("SELECT * FROM session WHERE day = :day ORDER BY at ASC")
    fun observeSessionsOn(day: Long): Flow<List<SessionWithResults>>

    @Transaction
    @Query("SELECT * FROM session WHERE day = :day ORDER BY at ASC")
    suspend fun sessionsOn(day: Long): List<SessionWithResults>

    /** Every session from [fromDay] on, newest first, for the journal. */
    @Transaction
    @Query("SELECT * FROM session WHERE day >= :fromDay ORDER BY at DESC")
    fun observeSessionsFrom(fromDay: Long): Flow<List<SessionWithResults>>

    /** Every answer for an ayah, oldest first, so the last one for an ayah is its latest. */
    @Query(
        "SELECT session_ayah.* FROM session_ayah JOIN session ON session.id = " +
            "session_ayah.session_id ORDER BY session.at ASC",
    )
    fun observeAyahAnswers(): Flow<List<SessionAyahEntity>>

    @Insert suspend fun insertMistakes(mistakes: List<MistakeEntity>)

    @Query("SELECT * FROM mistake WHERE at >= :sinceMillis ORDER BY at ASC")
    fun observeMistakesSince(sinceMillis: Long): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistake WHERE at >= :sinceMillis ORDER BY at ASC")
    suspend fun mistakesSince(sinceMillis: Long): List<MistakeEntity>
}
