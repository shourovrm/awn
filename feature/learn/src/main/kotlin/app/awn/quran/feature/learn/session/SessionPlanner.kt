package app.awn.quran.feature.learn.session

import kotlin.math.min

/** A thing the user revises: one word, or one grammar point (a lesson that has a check). */
sealed interface ItemRef {
    data class Word(val rank: Int) : ItemRef

    data class Grammar(val lessonId: String) : ItemRef
}

/** A revision item and when its schedule says it is due, in epoch milliseconds. */
data class DueItem(val item: ItemRef, val dueMillis: Long)

/**
 * A lesson the plan may place. [weight] is the length of the lesson relative to a full grammar
 * lesson (1.0): one short screen weighs less than a lesson of reading screens and a check.
 */
data class LessonSlot(val lessonId: String, val weight: Double) {
    fun seconds(pace: Pace): Double = pace.secondsPerLesson * weight
}

/** One step of a daily session, in the order the user meets them. */
sealed interface PlanStep {
    data class Lesson(val lessonId: String) : PlanStep

    data class Review(val item: ItemRef) : PlanStep

    data class NewWord(val rank: Int) : PlanStep

    /**
     * The questions that end the new words of this session; they give each new word its first
     * rating.
     */
    data class SetCheck(val ranks: List<Int>) : PlanStep
}

/** Everything the planner needs to know about the user and the day. */
data class PlannerInput(
    val budgetSeconds: Int,
    val pace: Pace,
    val dueItems: List<DueItem>,
    /** Words not yet met, in the order they are taught: words the user added, then by rank. */
    val newWordRanks: List<Int>,
    val maximumNewWords: Int,
    /** Basics and letters lessons not yet done, in the order of the course. */
    val startLessons: List<LessonSlot>,
    /** Whether the user has finished a start lesson, so that words may begin. */
    val hasFinishedStartLesson: Boolean,
    /** Grammar lessons whose words the user has already met, not yet done, in path order. */
    val pendingGrammar: List<LessonSlot>,
    /** The grammar lessons that meeting [ranks] would bring up, not yet done and not pending. */
    val grammarBroughtBy: (ranks: List<Int>) -> List<LessonSlot>,
)

/** The plan for one day. [plannedSeconds] is an estimate from the user's pace. */
data class TodayPlan(val steps: List<PlanStep>, val plannedSeconds: Int) {
    val reviewCount: Int = steps.count { it is PlanStep.Review }
    val newWordCount: Int = steps.count { it is PlanStep.NewWord }
    val lessonIds: List<String> = steps.filterIsInstance<PlanStep.Lesson>().map { it.lessonId }
    val isEmpty: Boolean
        get() = steps.isEmpty()
}

/**
 * Fills a time budget with a day's steps in this order: a start lesson (basics or letters, one per
 * day), reviews, new words, a grammar lesson when it fits, and the set check.
 *
 * Reviews come first, but they may use only [REVIEW_SHARE] of the budget while there is anything
 * else to do, so a long list of due words never keeps the user from new words. When nothing else is
 * left, reviews fill the rest of the time. How well the user answers never decides whether new
 * words appear: the planner sees no accuracy, only what is due.
 */
object SessionPlanner {

    /** The share of the budget that reviews may take while new words or a lesson are waiting. */
    const val REVIEW_SHARE = 0.65

    fun plan(input: PlannerInput): TodayPlan {
        var secondsLeft = input.budgetSeconds.toDouble()

        val startLesson = input.startLessons.firstOrNull { it.seconds(input.pace) <= secondsLeft }
        if (startLesson != null) secondsLeft -= startLesson.seconds(input.pace)

        val dueInOrder = input.dueItems.sortedBy { it.dueMillis }.map { it.item }
        val reviewSecondsCap = min(input.budgetSeconds * REVIEW_SHARE, secondsLeft)
        val reviewLimit = (reviewSecondsCap / input.pace.secondsPerReview).toInt()
        val reviews = dueInOrder.take(reviewLimit).toMutableList()
        secondsLeft -= reviews.size * input.pace.secondsPerReview

        val wordsAllowed =
            input.hasFinishedStartLesson || startLesson != null || input.startLessons.isEmpty()
        val pendingLesson =
            input.pendingGrammar.firstOrNull { it.seconds(input.pace) <= secondsLeft }
        val secondsForWords = secondsLeft - (pendingLesson?.seconds(input.pace) ?: 0.0)
        val newRanks = if (wordsAllowed) newWords(input, secondsForWords) else emptyList()
        secondsLeft -= newRanks.size * input.pace.secondsPerNewWord

        val grammarLesson = pendingLesson ?: broughtLesson(input, newRanks, secondsLeft)
        if (grammarLesson != null) secondsLeft -= grammarLesson.seconds(input.pace)

        val extraReviews = extraReviews(input, dueInOrder, reviews.size, secondsLeft)
        reviews += extraReviews
        secondsLeft -= extraReviews.size * input.pace.secondsPerReview

        val steps = buildList {
            startLesson?.let { add(PlanStep.Lesson(it.lessonId)) }
            reviews.forEach { add(PlanStep.Review(it)) }
            newRanks.forEach { add(PlanStep.NewWord(it)) }
            grammarLesson?.let { add(PlanStep.Lesson(it.lessonId)) }
            if (newRanks.isNotEmpty()) add(PlanStep.SetCheck(newRanks))
        }
        return TodayPlan(steps, plannedSeconds = (input.budgetSeconds - secondsLeft).toInt())
    }

    private fun newWords(input: PlannerInput, secondsAvailable: Double): List<Int> {
        val affordable = (secondsAvailable / input.pace.secondsPerNewWord).toInt().coerceAtLeast(0)
        return input.newWordRanks.take(min(affordable, input.maximumNewWords))
    }

    private fun broughtLesson(
        input: PlannerInput,
        newRanks: List<Int>,
        secondsLeft: Double,
    ): LessonSlot? {
        if (newRanks.isEmpty()) return null
        return input.grammarBroughtBy(newRanks).firstOrNull {
            it.seconds(input.pace) <= secondsLeft
        }
    }

    private fun extraReviews(
        input: PlannerInput,
        dueInOrder: List<ItemRef>,
        alreadyPlanned: Int,
        secondsLeft: Double,
    ): List<ItemRef> {
        val affordable = (secondsLeft / input.pace.secondsPerReview).toInt().coerceAtLeast(0)
        return dueInOrder.drop(alreadyPlanned).take(affordable)
    }
}
