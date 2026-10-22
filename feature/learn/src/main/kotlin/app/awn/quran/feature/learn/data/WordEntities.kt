package app.awn.quran.feature.learn.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The memory state of one word form, keyed by its rank in the frequency list. A row exists once the
 * user has met the word. [stability], [difficulty] and [lastReview] are null until the first
 * rating. [state] holds `FsrsState.value`; [step] is the learning step the scheduler needs.
 * [lastQuestionType] is the type the word was last asked in, so the next question differs.
 */
@Entity(tableName = "word_card")
data class WordCardEntity(
    @PrimaryKey val rank: Int,
    val stability: Double?,
    val difficulty: Double?,
    /** Epoch milliseconds. */
    val due: Long,
    @ColumnInfo(name = "last_review") val lastReview: Long?,
    val reps: Int,
    val lapses: Int,
    val state: Int,
    val step: Int,
    /** Epoch milliseconds of the day the word was first shown. */
    @ColumnInfo(name = "met_at") val metAt: Long,
    @ColumnInfo(name = "last_question_type") val lastQuestionType: String?,
)

/** The memory state of one grammar point, keyed by the id of its lesson. */
@Entity(tableName = "grammar_card")
data class GrammarCardEntity(
    @PrimaryKey @ColumnInfo(name = "lesson_id") val lessonId: String,
    val stability: Double,
    val difficulty: Double,
    val due: Long,
    @ColumnInfo(name = "last_review") val lastReview: Long,
    val reps: Int,
    val lapses: Int,
    val state: Int,
    val step: Int,
)

/** A word the user added from the Quran screen; it is taught before words that follow by rank. */
@Entity(tableName = "queued_word")
data class QueuedWordEntity(
    @PrimaryKey val rank: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** A lesson the user has finished. */
@Entity(tableName = "lesson_done")
data class LessonDoneEntity(
    @PrimaryKey @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "done_at") val doneAt: Long,
)

/**
 * A set of ten words by rank (set 1 is ranks 1 to 10). [startedAt] is the day its first word was
 * met and [finishedAt] the day its last was; null while words of the set are still new.
 */
@Entity(tableName = "learn_set")
data class LearnSetEntity(
    @PrimaryKey val number: Int,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
)

/** How a question was answered. The text values are stored, so they never change. */
object StepOutcome {
    const val CORRECT = "correct"
    const val WRONG = "wrong"
    const val DONT_KNOW = "dont_know"

    /** Shown or read, with no answer: a new word or a lesson screen. */
    const val SEEN = "seen"
}

/** What a [LearnStepEntity] concerns: a word by rank, or a lesson by id. */
object StepItemKind {
    const val WORD = "word"
    const val LESSON = "lesson"
}

/**
 * One finished step of a session or a self-started quiz: the quiz history and the timing the
 * planner measures a user's pace from. [item] is the rank as text for a word, else the lesson id.
 * [isOwnQuiz] is true for a quiz the user started, which never changes a revision date.
 */
@Entity(tableName = "learn_step", indices = [Index("at")])
data class LearnStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    /** `StepKind.stored`. */
    val kind: String,
    @ColumnInfo(name = "item_kind") val itemKind: String,
    val item: String,
    /** `QuestionType.stored`, or null for a step without a question. */
    @ColumnInfo(name = "question_type") val questionType: String?,
    val outcome: String,
    val millis: Long,
    @ColumnInfo(name = "is_own_quiz") val isOwnQuiz: Boolean,
)

/** One finished daily session; [elapsedMillis] is the time from first step to last. */
@Entity(tableName = "session_log", indices = [Index("started_at")])
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "planned_seconds") val plannedSeconds: Int,
    @ColumnInfo(name = "elapsed_millis") val elapsedMillis: Long,
    val reviews: Int,
    @ColumnInfo(name = "new_words") val newWords: Int,
    val lessons: Int,
    @ColumnInfo(name = "words_known_before") val wordsKnownBefore: Int,
    @ColumnInfo(name = "words_met_before") val wordsMetBefore: Int,
)
