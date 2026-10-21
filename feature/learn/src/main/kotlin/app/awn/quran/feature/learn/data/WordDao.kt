package app.awn.quran.feature.learn.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** The time one step took, as stored. */
data class StepTimingRow(val kind: String, val millis: Long)

/** The outcomes of one word's recent questions, for finding the words a user often forgets. */
data class WordOutcomeRow(val item: String, val outcome: String)

// One query per screen or per step; the DAO is the list of them.
@Suppress("TooManyFunctions")
@Dao
interface WordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWordCard(card: WordCardEntity)

    @Query("SELECT * FROM word_card WHERE rank = :rank")
    suspend fun findWordCard(rank: Int): WordCardEntity?

    @Query("SELECT * FROM word_card ORDER BY rank ASC")
    fun observeWordCards(): Flow<List<WordCardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGrammarCard(card: GrammarCardEntity)

    @Query("SELECT * FROM grammar_card WHERE lesson_id = :lessonId")
    suspend fun findGrammarCard(lessonId: String): GrammarCardEntity?

    @Query("SELECT * FROM grammar_card ORDER BY lesson_id ASC")
    fun observeGrammarCards(): Flow<List<GrammarCardEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun queueWord(word: QueuedWordEntity)

    @Query("DELETE FROM queued_word WHERE rank = :rank") suspend fun unqueueWord(rank: Int)

    @Query("SELECT * FROM queued_word ORDER BY added_at ASC, rank ASC")
    fun observeQueuedWords(): Flow<List<QueuedWordEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun markLessonDone(done: LessonDoneEntity)

    @Query("SELECT * FROM lesson_done") fun observeDoneLessons(): Flow<List<LessonDoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSet(set: LearnSetEntity)

    @Query("SELECT * FROM learn_set WHERE number = :number")
    suspend fun findSet(number: Int): LearnSetEntity?

    @Query("SELECT * FROM learn_set ORDER BY number ASC")
    fun observeSets(): Flow<List<LearnSetEntity>>

    @Insert suspend fun insertStep(step: LearnStepEntity)

    /** The timings of the latest [limit] steps of every kind, oldest first. */
    @Query(
        "SELECT kind, millis FROM (SELECT id, kind, millis FROM learn_step " +
            "ORDER BY id DESC LIMIT :limit) ORDER BY id ASC",
    )
    suspend fun recentTimings(limit: Int): List<StepTimingRow>

    @Query(
        "SELECT item, outcome FROM learn_step WHERE item_kind = '" +
            StepItemKind.WORD +
            "' AND question_type IS NOT NULL ORDER BY id ASC",
    )
    suspend fun wordOutcomes(): List<WordOutcomeRow>

    @Insert suspend fun insertSessionLog(log: SessionLogEntity)

    @Query("SELECT * FROM session_log ORDER BY id DESC LIMIT 1")
    suspend fun latestSessionLog(): SessionLogEntity?

    @Query("SELECT * FROM session_log ORDER BY id DESC") fun observeSessionLogs(): Flow<List<SessionLogEntity>>
}
