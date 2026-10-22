package app.awn.quran.feature.learn.words

import androidx.room.withTransaction
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.data.fsrs.FsrsScheduler
import app.awn.quran.core.data.fsrs.FsrsState
import app.awn.quran.feature.learn.data.GrammarCardEntity
import app.awn.quran.feature.learn.data.LearnDatabase
import app.awn.quran.feature.learn.data.LearnSetEntity
import app.awn.quran.feature.learn.data.LearnStepEntity
import app.awn.quran.feature.learn.data.LessonDoneEntity
import app.awn.quran.feature.learn.data.QueuedWordEntity
import app.awn.quran.feature.learn.data.SessionLogEntity
import app.awn.quran.feature.learn.data.StepItemKind
import app.awn.quran.feature.learn.data.StepOutcome
import app.awn.quran.feature.learn.data.WordCardEntity
import app.awn.quran.feature.learn.progress.LearnClock
import app.awn.quran.feature.learn.session.Pace
import app.awn.quran.feature.learn.session.PaceMeasurement
import app.awn.quran.feature.learn.session.QuestionType
import app.awn.quran.feature.learn.session.StepKind
import app.awn.quran.feature.learn.session.StepTiming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Everything the course screens and the planner read, at one moment. */
data class CourseSnapshot(
    val wordCards: Map<Int, WordCardEntity>,
    val queuedRanks: List<Int>,
    val grammarCards: Map<String, GrammarCardEntity>,
    val doneLessonIds: Set<String>,
    val sets: Map<Int, LearnSetEntity>,
) {
    val wordsMet: Int
        get() = wordCards.size

    val knownRanks: List<Int>
        get() = wordCards.values.filter(WordProgress::isKnown).map { it.rank }

    val wordsKnown: Int
        get() = knownRanks.size

    companion object {
        val Empty = CourseSnapshot(emptyMap(), emptyList(), emptyMap(), emptySet(), emptyMap())
    }
}

/** One finished step as the repository logs it; [millis] is how long the user took. */
data class FinishedStep(
    val kind: StepKind,
    val itemKind: String,
    val item: String,
    val questionType: QuestionType?,
    val outcome: String,
    val millis: Long,
    val isOwnQuiz: Boolean = false,
)

/**
 * The word course in the learning database: which words the user has met and how well they are
 * remembered, the grammar points, the finished lessons, the sets and the timing of every step.
 *
 * A word is first rated in the set check at the end of the new words, so a user who leaves before
 * it leaves the card due at once and the word comes back as the first review of the next day. Words
 * and grammar points use FSRS with a desired retention of [DESIRED_RETENTION] and no minute-long
 * learning steps: revision happens once a day, so steps would only list a word as due again within
 * the hour.
 */
// One entry point per thing a session or a screen records or reads.
@Suppress("TooManyFunctions")
class WordCourseRepository(
    private val database: LearnDatabase,
    private val clock: LearnClock = LearnClock.System,
    private val scheduler: FsrsScheduler = wordScheduler(),
) {
    private val dao = database.wordDao()

    fun observeSnapshot(): Flow<CourseSnapshot> =
        combine(
            dao.observeWordCards(),
            dao.observeQueuedWords(),
            dao.observeGrammarCards(),
            dao.observeDoneLessons(),
            dao.observeSets(),
        ) { cards, queued, grammar, done, sets ->
            CourseSnapshot(
                wordCards = cards.associateBy { it.rank },
                queuedRanks = queued.map { it.rank },
                grammarCards = grammar.associateBy { it.lessonId },
                doneLessonIds = done.map { it.lessonId }.toSet(),
                sets = sets.associateBy { it.number },
            )
        }

    suspend fun snapshot(): CourseSnapshot = observeSnapshot().first()

    /**
     * Records that [rank] was shown to the user for the first time. The card is due at once, so a
     * word whose set check was never finished comes back in the next session.
     */
    suspend fun meet(rank: Int) {
        val nowMillis = clock.now().toEpochMilli()
        database.withTransaction {
            if (dao.findWordCard(rank) != null) return@withTransaction
            dao.upsertWordCard(
                WordCardEntity(
                    rank = rank,
                    stability = null,
                    difficulty = null,
                    due = nowMillis,
                    lastReview = null,
                    reps = 0,
                    lapses = 0,
                    state = FsrsState.Learning.value,
                    step = 0,
                    metAt = nowMillis,
                    lastQuestionType = null,
                ),
            )
            dao.unqueueWord(rank)
            updateSetOf(rank, nowMillis)
        }
    }

    /** Adds a word from the Quran screen to the words taught next, unless it is already met. */
    suspend fun queueWord(rank: Int) {
        database.withTransaction {
            if (dao.findWordCard(rank) == null) {
                dao.queueWord(QueuedWordEntity(rank, clock.now().toEpochMilli()))
            }
        }
    }

    /**
     * Rates one answer about [rank] in a session and logs the step. Returns the epoch milliseconds
     * at which the word is due next.
     */
    suspend fun rateWord(rank: Int, rating: FsrsRating, step: FinishedStep): Long {
        val nowMillis = clock.now().toEpochMilli()
        return database.withTransaction {
            val existing = checkNotNull(dao.findWordCard(rank)) { "Word $rank was never met." }
            val after = scheduler.reviewCard(existing.toFsrsCard(), rating, nowMillis)
            val wasReview = existing.state == FsrsState.Review.value
            dao.upsertWordCard(
                existing.copy(
                    stability = after.stability,
                    difficulty = after.difficulty,
                    due = after.dueMillis,
                    lastReview = nowMillis,
                    reps = existing.reps + 1,
                    lapses =
                        existing.lapses + if (wasReview && rating == FsrsRating.Again) 1 else 0,
                    state = after.state.value,
                    step = after.step,
                    lastQuestionType = step.questionType?.stored ?: existing.lastQuestionType,
                ),
            )
            dao.insertStep(step.toEntity(nowMillis))
            after.dueMillis
        }
    }

    /**
     * Rates the check of a grammar lesson; the first rating creates the grammar card. Returns the
     * epoch milliseconds at which the point is due next.
     */
    suspend fun rateGrammar(lessonId: String, rating: FsrsRating, step: FinishedStep): Long {
        val nowMillis = clock.now().toEpochMilli()
        return database.withTransaction {
            val existing = dao.findGrammarCard(lessonId)
            val before = existing?.toFsrsCard() ?: scheduler.newCard(nowMillis)
            val after = scheduler.reviewCard(before, rating, nowMillis)
            val wasReview = existing?.state == FsrsState.Review.value
            dao.upsertGrammarCard(
                GrammarCardEntity(
                    lessonId = lessonId,
                    stability = checkNotNull(after.stability),
                    difficulty = checkNotNull(after.difficulty),
                    due = after.dueMillis,
                    lastReview = nowMillis,
                    reps = (existing?.reps ?: 0) + 1,
                    lapses =
                        (existing?.lapses ?: 0) +
                            if (wasReview && rating == FsrsRating.Again) 1 else 0,
                    state = after.state.value,
                    step = after.step,
                ),
            )
            dao.insertStep(step.toEntity(nowMillis))
            after.dueMillis
        }
    }

    /** Logs a step that moves no card: a new word shown, a lesson read, a self-started quiz. */
    suspend fun logStep(step: FinishedStep) {
        dao.insertStep(step.toEntity(clock.now().toEpochMilli()))
    }

    suspend fun finishLesson(lessonId: String, step: FinishedStep) {
        val nowMillis = clock.now().toEpochMilli()
        database.withTransaction {
            dao.markLessonDone(LessonDoneEntity(lessonId, nowMillis))
            dao.insertStep(step.toEntity(nowMillis))
        }
    }

    suspend fun logSession(log: SessionLogEntity) = dao.insertSessionLog(log)

    suspend fun latestSession(): SessionLogEntity? = dao.latestSessionLog()

    /**
     * The user's pace from the timings of their latest steps, or the defaults when there are none.
     */
    suspend fun pace(): Pace =
        PaceMeasurement.estimate(
            dao.recentTimings(PACE_STEP_WINDOW).map {
                StepTiming(StepKind.fromStored(it.kind), it.millis)
            },
        )

    /** The ranks of words forgotten at least [WordProgress.WEAK_LAPSES] times in sessions. */
    suspend fun weakRanks(): List<Int> =
        snapshot().wordCards.values.filter(WordProgress::isWeak).map { it.rank }

    private suspend fun updateSetOf(rank: Int, nowMillis: Long) {
        val number = LearnSets.numberOf(rank)
        val existing = dao.findSet(number)
        val range = LearnSets.ranksOf(number)
        val metInSet = range.count { dao.findWordCard(it) != null }
        val finishedAt = if (metInSet == range.count()) nowMillis else null
        dao.upsertSet(
            LearnSetEntity(
                number = number,
                startedAt = existing?.startedAt ?: nowMillis,
                finishedAt = existing?.finishedAt ?: finishedAt,
            ),
        )
    }

    private fun FinishedStep.toEntity(at: Long) =
        LearnStepEntity(
            at = at,
            kind = kind.stored,
            itemKind = itemKind,
            item = item,
            questionType = questionType?.stored,
            outcome = outcome,
            millis = millis,
            isOwnQuiz = isOwnQuiz,
        )

    companion object {
        /** Gentler than FSRS's 0.90: see docs/decisions/learn.md for the simulation behind it. */
        const val DESIRED_RETENTION = 0.85

        /** About 30 steps of each kind are enough for the median that measures a pace. */
        const val PACE_STEP_WINDOW = 120

        fun wordScheduler() =
            FsrsScheduler(
                learningStepsMillis = emptyList(),
                relearningStepsMillis = emptyList(),
                desiredRetention = DESIRED_RETENTION,
            )

        /** The step of a word shown with no question, such as a new word. */
        fun seenStep(kind: StepKind, rank: Int, millis: Long) =
            FinishedStep(kind, StepItemKind.WORD, rank.toString(), null, StepOutcome.SEEN, millis)
    }
}

/** Sets of ten words by rank: set 1 is ranks 1 to 10. */
object LearnSets {
    const val SET_SIZE = 10

    fun numberOf(rank: Int): Int = (rank - 1) / SET_SIZE + 1

    fun ranksOf(number: Int): IntRange = ((number - 1) * SET_SIZE + 1)..(number * SET_SIZE)
}
