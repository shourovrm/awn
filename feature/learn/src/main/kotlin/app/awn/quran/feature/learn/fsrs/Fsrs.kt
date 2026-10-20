// The numbers in this file are the weight indices (parameters[0] to parameters[20]) and the
// constants of the reference formulas; naming each one would hide the match with py-fsrs.
@file:Suppress("MagicNumber")

package app.awn.quran.feature.learn.fsrs

import kotlin.math.E
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Port of the FSRS scheduler (Free Spaced Repetition Scheduler, open-spaced-repetition) from
 * py-fsrs 6.3.2 with fuzzing removed. Names of the formulas follow `fsrs/scheduler.py` so a reader
 * can hold the two files side by side. The vectors in `fsrs_vectors.json` come from py-fsrs and
 * `FsrsReferenceTest` checks that this class gives the same due times.
 */
enum class FsrsRating(val value: Int) {
    Again(1),
    Hard(2),
    Good(3),
    Easy(4),
}

enum class FsrsState(val value: Int) {
    Learning(1),
    Review(2),
    Relearning(3),
}

/** All times are epoch milliseconds. [step] is the position in the learning or relearning steps. */
data class FsrsCard(
    val state: FsrsState = FsrsState.Learning,
    val step: Int = 0,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val dueMillis: Long,
    val lastReviewMillis: Long? = null,
)

private const val FSRS_DEFAULT_DECAY = 0.1542
private const val STABILITY_MIN = 0.001
private const val MIN_DIFFICULTY = 1.0
private const val MAX_DIFFICULTY = 10.0
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L

/** The 21 default weights of py-fsrs 6.3.2 (FSRS-6). */
val DEFAULT_PARAMETERS: List<Double> =
    listOf(
        0.212,
        1.2931,
        2.3065,
        8.2956,
        6.4133,
        0.8334,
        3.0194,
        0.001,
        1.8722,
        0.1666,
        0.796,
        1.4835,
        0.0614,
        0.2629,
        1.6483,
        0.6014,
        1.8729,
        0.5425,
        0.0912,
        0.0658,
        FSRS_DEFAULT_DECAY,
    )

// One method per formula of the reference, so the class is long by design.
@Suppress("TooManyFunctions")
class FsrsScheduler(
    private val parameters: List<Double> = DEFAULT_PARAMETERS,
    private val desiredRetention: Double = 0.9,
    private val learningStepsMillis: List<Long> = listOf(MILLIS_PER_MINUTE, 10 * MILLIS_PER_MINUTE),
    private val relearningStepsMillis: List<Long> = listOf(10 * MILLIS_PER_MINUTE),
    private val maximumIntervalDays: Int = 36_500,
) {
    private val decay = -parameters[20]
    private val factor = 0.9.pow(1 / decay) - 1

    init {
        require(parameters.size == DEFAULT_PARAMETERS.size) { "FSRS-6 needs 21 parameters" }
    }

    fun newCard(nowMillis: Long): FsrsCard = FsrsCard(dueMillis = nowMillis)

    /** Predicted chance that [card] is recalled at [nowMillis]. */
    fun getCardRetrievability(card: FsrsCard, nowMillis: Long): Double {
        val lastReview = card.lastReviewMillis
        val stability = card.stability
        if (lastReview == null || stability == null) return 0.0
        val elapsedDays = max(0L, daysBetween(lastReview, nowMillis))
        return (1 + factor * elapsedDays / stability).pow(decay)
    }

    fun reviewCard(card: FsrsCard, rating: FsrsRating, reviewMillis: Long): FsrsCard {
        val lastReview = card.lastReviewMillis
        val daysSinceLastReview = lastReview?.let { daysBetween(it, reviewMillis) }
        val sameDay = daysSinceLastReview != null && daysSinceLastReview < 1

        val oldStability = card.stability
        val oldDifficulty = card.difficulty
        val stability: Double
        val difficulty: Double
        if (oldStability == null || oldDifficulty == null) {
            // Only a first review in the Learning state has no stability yet.
            stability = initialStability(rating)
            difficulty = initialDifficulty(rating, clamp = true)
        } else {
            stability =
                if (sameDay) {
                    shortTermStability(oldStability, rating)
                } else {
                    val retrievability = getCardRetrievability(card, reviewMillis)
                    nextStability(oldDifficulty, oldStability, retrievability, rating)
                }
            difficulty = nextDifficulty(oldDifficulty, rating)
        }

        val schedule = scheduleNext(card.state, card.step, rating, stability)
        return FsrsCard(
            state = schedule.state,
            step = schedule.step,
            stability = stability,
            difficulty = difficulty,
            dueMillis = reviewMillis + schedule.intervalMillis,
            lastReviewMillis = reviewMillis,
        )
    }

    private class Schedule(val state: FsrsState, val step: Int, val intervalMillis: Long)

    private fun scheduleNext(
        state: FsrsState,
        step: Int,
        rating: FsrsRating,
        stability: Double,
    ): Schedule {
        val steps = if (state == FsrsState.Learning) learningStepsMillis else relearningStepsMillis
        // A card whose steps were shortened since it was saved leaves Learning on any rating but
        // Again.
        val stepsAreExhausted =
            steps.isEmpty() || (step >= steps.size && rating != FsrsRating.Again)
        return when {
            state == FsrsState.Review -> scheduleAfterReview(rating, stability)
            stepsAreExhausted -> reviewSchedule(stability)
            else -> scheduleWithinSteps(state, step, steps, rating, stability)
        }
    }

    private fun scheduleAfterReview(rating: FsrsRating, stability: Double): Schedule =
        if (rating == FsrsRating.Again && relearningStepsMillis.isNotEmpty()) {
            Schedule(FsrsState.Relearning, 0, relearningStepsMillis[0])
        } else {
            reviewSchedule(stability)
        }

    private fun scheduleWithinSteps(
        state: FsrsState,
        step: Int,
        steps: List<Long>,
        rating: FsrsRating,
        stability: Double,
    ): Schedule =
        when (rating) {
            FsrsRating.Again -> Schedule(state, 0, steps[0])
            FsrsRating.Hard -> Schedule(state, step, hardInterval(steps, step))
            FsrsRating.Good ->
                if (step + 1 == steps.size) reviewSchedule(stability)
                else Schedule(state, step + 1, steps[step + 1])
            FsrsRating.Easy -> reviewSchedule(stability)
        }

    /** Hard on the first step waits between the first two steps; elsewhere it repeats the step. */
    private fun hardInterval(steps: List<Long>, step: Int): Long =
        when {
            step == 0 && steps.size == 1 -> steps[0] * 3 / 2
            step == 0 -> (steps[0] + steps[1]) / 2
            else -> steps[step]
        }

    private fun reviewSchedule(stability: Double): Schedule =
        Schedule(FsrsState.Review, 0, nextInterval(stability) * MILLIS_PER_DAY)

    /** Whole days until retrievability falls to the desired retention; at least one day. */
    fun nextInterval(stability: Double): Long {
        val interval = (stability / factor) * (desiredRetention.pow(1 / decay) - 1)
        // py-fsrs uses Python's round(), which rounds halves to even, exactly like rint.
        return min(max(Math.rint(interval).toLong(), 1L), maximumIntervalDays.toLong())
    }

    private fun initialStability(rating: FsrsRating): Double =
        max(parameters[rating.value - 1], STABILITY_MIN)

    private fun initialDifficulty(rating: FsrsRating, clamp: Boolean): Double {
        val difficulty = parameters[4] - E.pow(parameters[5] * (rating.value - 1)) + 1
        return if (clamp) clampDifficulty(difficulty) else difficulty
    }

    private fun shortTermStability(stability: Double, rating: FsrsRating): Double {
        var increase =
            E.pow(parameters[17] * (rating.value - 3 + parameters[18])) *
                stability.pow(-parameters[19])
        if (rating != FsrsRating.Again) increase = max(increase, 1.0)
        return max(stability * increase, STABILITY_MIN)
    }

    private fun nextDifficulty(difficulty: Double, rating: FsrsRating): Double {
        val easyInitialDifficulty = initialDifficulty(FsrsRating.Easy, clamp = false)
        val deltaDifficulty = -(parameters[6] * (rating.value - 3))
        val linearDamping = (10.0 - difficulty) * deltaDifficulty / 9.0
        val meanReversionTarget = difficulty + linearDamping
        val next = parameters[7] * easyInitialDifficulty + (1 - parameters[7]) * meanReversionTarget
        return clampDifficulty(next)
    }

    private fun nextStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: FsrsRating,
    ): Double {
        val next =
            if (rating == FsrsRating.Again) {
                nextForgetStability(difficulty, stability, retrievability)
            } else {
                nextRecallStability(difficulty, stability, retrievability, rating)
            }
        return max(next, STABILITY_MIN)
    }

    private fun nextForgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
    ): Double {
        val longTerm =
            parameters[11] *
                difficulty.pow(-parameters[12]) *
                ((stability + 1).pow(parameters[13]) - 1) *
                E.pow((1 - retrievability) * parameters[14])
        val shortTerm = stability / E.pow(parameters[17] * parameters[18])
        return min(longTerm, shortTerm)
    }

    private fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: FsrsRating,
    ): Double {
        val hardPenalty = if (rating == FsrsRating.Hard) parameters[15] else 1.0
        val easyBonus = if (rating == FsrsRating.Easy) parameters[16] else 1.0
        return stability *
            (1 +
                E.pow(parameters[8]) *
                    (11 - difficulty) *
                    stability.pow(-parameters[9]) *
                    (E.pow((1 - retrievability) * parameters[10]) - 1) *
                    hardPenalty *
                    easyBonus)
    }

    private fun clampDifficulty(difficulty: Double): Double =
        min(max(difficulty, MIN_DIFFICULTY), MAX_DIFFICULTY)

    /** Whole days from [fromMillis] to [toMillis], rounded down like Python's `timedelta.days`. */
    private fun daysBetween(fromMillis: Long, toMillis: Long): Long =
        Math.floorDiv(toMillis - fromMillis, MILLIS_PER_DAY)
}
