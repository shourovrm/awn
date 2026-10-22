package app.awn.quran.feature.learn.ui

import app.awn.quran.feature.learn.session.ItemRef
import app.awn.quran.feature.learn.session.Pace
import app.awn.quran.feature.learn.session.PlanStep
import app.awn.quran.feature.learn.words.CourseSnapshot
import app.awn.quran.feature.learn.words.WordProgress
import java.time.Duration

private val WEEK_MILLIS = Duration.ofDays(7).toMillis()

/** How many met words each quiz scope holds. */
internal fun quizCounts(snapshot: CourseSnapshot, nowMillis: Long): QuizCounts =
    QuizCounts(
        all = ranksInScope(QuizScope.All, snapshot, nowMillis).size,
        thisWeek = ranksInScope(QuizScope.ThisWeek, snapshot, nowMillis).size,
        weak = ranksInScope(QuizScope.Weak, snapshot, nowMillis).size,
    )

private fun ranksInScope(scope: QuizScope, snapshot: CourseSnapshot, nowMillis: Long): List<Int> {
    val cards = snapshot.wordCards.values
    return when (scope) {
        QuizScope.All -> cards.map { it.rank }
        QuizScope.ThisWeek -> cards.filter { it.metAt >= nowMillis - WEEK_MILLIS }.map { it.rank }
        QuizScope.Weak -> cards.filter(WordProgress::isWeak).map { it.rank }
    }
}

/** The steps of a quiz the user started, and how long they are expected to take. */
internal class OwnQuiz(val steps: List<PlanStep>, val plannedSeconds: Int)

internal fun buildOwnQuiz(
    scope: QuizScope,
    length: QuizLength,
    snapshot: CourseSnapshot,
    nowMillis: Long,
    pace: Pace,
): OwnQuiz {
    val ranks = ranksInScope(scope, snapshot, nowMillis).shuffled().take(length.questions)
    return quizOfRanks(ranks, pace)
}

/** A quiz about one word, for "Revise now" on the word page. */
internal fun singleWordQuiz(rank: Int, pace: Pace): OwnQuiz = quizOfRanks(listOf(rank), pace)

private fun quizOfRanks(ranks: List<Int>, pace: Pace): OwnQuiz =
    OwnQuiz(
        steps = ranks.map { PlanStep.Review(ItemRef.Word(it)) },
        plannedSeconds = (ranks.size * pace.secondsPerReview).toInt(),
    )
