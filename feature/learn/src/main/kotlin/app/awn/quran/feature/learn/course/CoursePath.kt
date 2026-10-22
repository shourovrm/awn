package app.awn.quran.feature.learn.course

import app.awn.quran.feature.learn.session.PlanStep
import app.awn.quran.feature.learn.session.TodayPlan
import app.awn.quran.feature.learn.words.LearnSets
import app.awn.quran.feature.learn.words.WordFrequency

/** Where a step of the path stands. */
enum class PathState {
    Done,

    /** The first step not done: what the next session continues with. */
    Next,
    Upcoming,
}

/** One row of the course path on the Learn hub. */
sealed interface PathRow {
    /**
     * The steps done long ago, folded into one row. [lessonIds] are the lessons among them and
     * [sets] the range of sets, null when no set is done.
     */
    data class Folded(val sets: IntRange?, val lessonIds: List<String>, val stepCount: Int) :
        PathRow

    /** A set of ten words; [knownCount] of its [metCount] met words are known. */
    data class SetStep(
        val number: Int,
        val metCount: Int,
        val knownCount: Int,
        val state: PathState,
        val isToday: Boolean,
    ) : PathRow

    /**
     * A lesson. A grammar lesson sits after the set that brought it: [triggerRank] is the most
     * frequent word that brings it and [triggerSet] the set that holds that word.
     */
    data class LessonStep(
        val lessonId: String,
        val triggerRank: Int?,
        val triggerSet: Int?,
        val state: PathState,
        val isToday: Boolean,
    ) : PathRow

    /** Everything after the rows shown, from set [fromSet] on. */
    data class Later(val fromSet: Int) : PathRow
}

/** What the path needs to know about the user. */
data class PathInput(
    val content: CourseContent,
    val frequency: WordFrequency,
    /** Rank of every met word. */
    val metRanks: Set<Int>,
    /** Rank of every known word. */
    val knownRanks: Set<Int>,
    val doneLessonIds: Set<String>,
    /** True when the user chose to start with the letters course. */
    val includeLetters: Boolean,
    val plan: TodayPlan?,
)

/**
 * Builds the rows of the path: the start lessons, then the sets in frequency order with each
 * grammar lesson right after the set that brings it. Steps done long ago fold into one row, and the
 * rows after the next sets collapse into "Later", so the path stays short however far the user is.
 */
// One small function per kind of row.
@Suppress("TooManyFunctions")
object CoursePath {

    /** How many done steps stay visible above the next step. */
    const val VISIBLE_DONE_STEPS = 2

    /** How many sets after the current one are listed. */
    const val UPCOMING_SETS = 2

    fun build(input: PathInput): List<PathRow> {
        val steps = orderedSteps(input)
        val firstNotDone = steps.indexOfFirst { it.stateOrNull() != PathState.Done }
        val nextIndex = if (firstNotDone == -1) steps.size else firstNotDone
        val marked = steps.mapIndexed { index, step ->
            if (index == nextIndex) step.asNext() else step
        }
        val foldedCount = (nextIndex - VISIBLE_DONE_STEPS).coerceAtLeast(0)
        val rows = mutableListOf<PathRow>()
        if (foldedCount > 0) rows += folded(marked.take(foldedCount))
        rows += marked.drop(foldedCount)
        rows += PathRow.Later(fromSet = lastSetNumber(marked) + 1)
        return rows
    }

    private fun orderedSteps(input: PathInput): List<PathRow> {
        val todaySets =
            input.plan
                ?.steps
                ?.filterIsInstance<PlanStep.NewWord>()
                ?.map { LearnSets.numberOf(it.rank) }
                .orEmpty()
                .toSet()
        val todayLessons = input.plan?.lessonIds.orEmpty().toSet()
        val steps = mutableListOf<PathRow>()
        startLessons(input).forEach { steps += lessonStep(it, input, todayLessons, null, null) }
        val grammar = input.content.inCourse(LessonCourse.Grammar)
        val currentSet = currentSetNumber(input)
        for (number in 1..(currentSet + UPCOMING_SETS)) {
            steps += setStep(number, input, number in todaySets)
            grammar
                .filter { triggerSetOf(it, input) == number }
                .forEach {
                    steps += lessonStep(it, input, todayLessons, number, triggerRankOf(it, input))
                }
        }
        return steps
    }

    private fun startLessons(input: PathInput): List<CourseLesson> {
        val letters =
            if (input.includeLetters) input.content.inCourse(LessonCourse.Letters) else emptyList()
        return letters + input.content.inCourse(LessonCourse.Basics)
    }

    private fun setStep(number: Int, input: PathInput, isToday: Boolean): PathRow.SetStep {
        val ranks = LearnSets.ranksOf(number).filter { it <= input.frequency.formCount }
        val met = ranks.count { it in input.metRanks }
        val known = ranks.count { it in input.knownRanks }
        val state = if (met == ranks.size) PathState.Done else PathState.Upcoming
        return PathRow.SetStep(number, met, known, state, isToday)
    }

    private fun lessonStep(
        lesson: CourseLesson,
        input: PathInput,
        todayLessons: Set<String>,
        triggerSet: Int?,
        triggerRank: Int?,
    ): PathRow.LessonStep =
        PathRow.LessonStep(
            lessonId = lesson.id,
            triggerRank = triggerRank,
            triggerSet = triggerSet,
            state = if (lesson.id in input.doneLessonIds) PathState.Done else PathState.Upcoming,
            isToday = lesson.id in todayLessons,
        )

    private fun currentSetNumber(input: PathInput): Int {
        var number = 1
        while (
            LearnSets.ranksOf(number).all { it in input.metRanks || it > input.frequency.formCount }
        ) number++
        return number
    }

    private fun triggerRankOf(lesson: CourseLesson, input: PathInput): Int? =
        lesson.introducedBy.minOfOrNull { input.frequency.rankOf(it) }

    private fun triggerSetOf(lesson: CourseLesson, input: PathInput): Int? =
        triggerRankOf(lesson, input)?.let(LearnSets::numberOf)

    private fun PathRow.stateOrNull(): PathState? =
        when (this) {
            is PathRow.SetStep -> state
            is PathRow.LessonStep -> state
            else -> null
        }

    private fun PathRow.asNext(): PathRow =
        when (this) {
            is PathRow.SetStep -> copy(state = PathState.Next)
            is PathRow.LessonStep -> copy(state = PathState.Next)
            else -> this
        }

    private fun folded(done: List<PathRow>): PathRow.Folded {
        val setNumbers = done.filterIsInstance<PathRow.SetStep>().map { it.number }
        return PathRow.Folded(
            sets = if (setNumbers.isEmpty()) null else setNumbers.min()..setNumbers.max(),
            lessonIds = done.filterIsInstance<PathRow.LessonStep>().map { it.lessonId },
            stepCount = done.size,
        )
    }

    private fun lastSetNumber(rows: List<PathRow>): Int =
        rows.filterIsInstance<PathRow.SetStep>().maxOf { it.number }
}
