package app.awn.quran.feature.learn.session

import app.awn.quran.feature.learn.fsrs.FsrsCard
import app.awn.quran.feature.learn.fsrs.FsrsRating
import app.awn.quran.feature.learn.fsrs.FsrsScheduler
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compares desired retentions of the word scheduler over 90 days of 10-minute sessions, to choose
 * the number recorded in docs/decisions/learn.md. A simulated user answers a review correctly with
 * the probability FSRS predicts (retrievability) and a first check of a new word with probability
 * [FIRST_CHECK_SUCCESS]. Each day the real [SessionPlanner] fills the budget, so the review cap, the
 * new-word limit and the backlog behave as in the app.
 */
class RetentionSimulationTest {

    private data class Outcome(
        val desiredRetention: Double,
        val wordsMet: Int,
        val wordsKnown: Int,
        val reviewsDone: Int,
        val lapses: Int,
        val dueAtEnd: Int,
        val seconds: Int,
    ) {
        val answeredCorrectly: Double
            get() = if (reviewsDone == 0) 1.0 else 1.0 - lapses.toDouble() / reviewsDone
    }

    private fun simulate(desiredRetention: Double, days: Int, seed: Int): Outcome {
        val scheduler =
            FsrsScheduler(
                learningStepsMillis = emptyList(),
                relearningStepsMillis = emptyList(),
                desiredRetention = desiredRetention,
            )
        val random = Random(seed)
        val cards = mutableMapOf<Int, FsrsCard>()
        var nextRank = 1
        var reviewsDone = 0
        var lapses = 0
        var seconds = 0
        for (day in 0 until days) {
            val now = day * DAY_MILLIS + MORNING_MILLIS
            val due = cards.filter { it.value.dueMillis <= now }.map { DueItem(ItemRef.Word(it.key), it.value.dueMillis) }
            val plan =
                SessionPlanner.plan(
                    PlannerInput(
                        budgetSeconds = BUDGET_SECONDS,
                        pace = Pace.Default,
                        dueItems = due,
                        newWordRanks = (nextRank until nextRank + SET_SIZE).toList(),
                        maximumNewWords = SET_SIZE,
                        startLessons = emptyList(),
                        hasFinishedStartLesson = true,
                        pendingGrammar = emptyList(),
                        grammarBroughtBy = { emptyList() },
                    ),
                )
            seconds += plan.plannedSeconds
            for (step in plan.steps) {
                when (step) {
                    is PlanStep.Review -> {
                        val rank = (step.item as ItemRef.Word).rank
                        val card = cards.getValue(rank)
                        val recalled = random.nextDouble() < scheduler.getCardRetrievability(card, now)
                        reviewsDone++
                        if (!recalled) lapses++
                        cards[rank] = scheduler.reviewCard(card, rating(recalled), now)
                    }
                    is PlanStep.SetCheck ->
                        step.ranks.forEach { rank ->
                            val recalled = random.nextDouble() < FIRST_CHECK_SUCCESS
                            cards[rank] = scheduler.reviewCard(scheduler.newCard(now), rating(recalled), now)
                            nextRank = maxOf(nextRank, rank + 1)
                        }
                    is PlanStep.NewWord,
                    is PlanStep.Lesson -> Unit
                }
            }
        }
        val end = days * DAY_MILLIS
        return Outcome(
            desiredRetention = desiredRetention,
            wordsMet = cards.size,
            wordsKnown = cards.values.count { (it.stability ?: 0.0) >= KNOWN_STABILITY_DAYS },
            reviewsDone = reviewsDone,
            lapses = lapses,
            dueAtEnd = cards.values.count { it.dueMillis <= end },
            seconds = seconds,
        )
    }

    private fun rating(recalled: Boolean) = if (recalled) FsrsRating.Good else FsrsRating.Again

    @Test
    fun theChosenRetentionKeepsAnswersReliableWithLessWorkThanTheDefault() {
        val retentions = listOf(0.80, 0.85, 0.90, 0.95)
        val outcomes =
            retentions.associateWith { retention ->
                (1..SEEDS).map { simulate(retention, DAYS, seed = it) }
            }
        println("desired  met  known  reviews  correct  due-at-end  (mean of $SEEDS runs, $DAYS days)")
        outcomes.forEach { (retention, runs) ->
            println(
                "%.2f  %5.0f  %5.0f  %7.0f  %6.1f%%  %9.0f".format(
                    retention,
                    runs.map { it.wordsMet }.average(),
                    runs.map { it.wordsKnown }.average(),
                    runs.map { it.reviewsDone }.average(),
                    runs.map { it.answeredCorrectly }.average() * 100,
                    runs.map { it.dueAtEnd }.average(),
                ),
            )
        }

        val chosen = outcomes.getValue(CHOSEN_RETENTION)
        val defaultRetention = outcomes.getValue(0.90)
        // Fewer reviews per known word than at 0.90: the gentler setting costs less time.
        fun reviewsPerKnown(runs: List<Outcome>) =
            runs.sumOf { it.reviewsDone }.toDouble() / runs.sumOf { it.wordsKnown }
        assertTrue(reviewsPerKnown(chosen) < reviewsPerKnown(defaultRetention))
        // Reviews are still answered right most of the time.
        assertTrue(chosen.map { it.answeredCorrectly }.average() >= 0.80)
    }

    @Test
    fun theSameSeedGivesTheSameOutcome() {
        assertEquals(simulate(0.85, 30, seed = 3), simulate(0.85, 30, seed = 3))
    }

    private companion object {
        const val CHOSEN_RETENTION = 0.85
        const val DAYS = 90
        const val SEEDS = 5
        const val SET_SIZE = 10
        const val BUDGET_SECONDS = 600
        const val FIRST_CHECK_SUCCESS = 0.9
        const val KNOWN_STABILITY_DAYS = 4.0
        const val DAY_MILLIS = 86_400_000L
        const val MORNING_MILLIS = 8 * 3_600_000L
    }
}
