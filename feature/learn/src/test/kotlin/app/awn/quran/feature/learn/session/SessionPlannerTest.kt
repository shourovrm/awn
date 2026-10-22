package app.awn.quran.feature.learn.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlannerTest {

    private val pace =
        Pace(secondsPerReview = 10.0, secondsPerNewWord = 30.0, secondsPerLesson = 150.0)
    private val fullLesson = LessonSlot("grammar-03-pronoun-endings", weight = 1.0)
    private val shortLesson = LessonSlot("basics-01-reading-direction", weight = 0.4)

    private fun wordItems(count: Int): List<DueItem> =
        (1..count).map { DueItem(ItemRef.Word(it), dueMillis = it.toLong()) }

    @Suppress("LongParameterList") // One argument per planner input, with defaults.
    private fun input(
        budgetSeconds: Int = 600,
        dueItems: List<DueItem> = emptyList(),
        newWordRanks: List<Int> = (1..100).toList(),
        maximumNewWords: Int = 10,
        startLessons: List<LessonSlot> = emptyList(),
        hasFinishedStartLesson: Boolean = true,
        pendingGrammar: List<LessonSlot> = emptyList(),
        grammarBroughtBy: (List<Int>) -> List<LessonSlot> = { emptyList() },
    ) =
        PlannerInput(
            budgetSeconds = budgetSeconds,
            pace = pace,
            dueItems = dueItems,
            newWordRanks = newWordRanks,
            maximumNewWords = maximumNewWords,
            startLessons = startLessons,
            hasFinishedStartLesson = hasFinishedStartLesson,
            pendingGrammar = pendingGrammar,
            grammarBroughtBy = grammarBroughtBy,
        )

    @Test
    fun reviewsTakeAtMostTheirShareWhileNewWordsWait() {
        val plan = SessionPlanner.plan(input(dueItems = wordItems(100)))

        // 65 % of 600 s is 390 s, so 39 reviews; the other 210 s buy seven new words.
        assertEquals(39, plan.reviewCount)
        assertEquals(7, plan.newWordCount)
    }

    @Test
    fun aHugeBacklogNeverRemovesTheNewWords() {
        val plan = SessionPlanner.plan(input(dueItems = wordItems(5000)))

        assertTrue(plan.newWordCount >= 1)
    }

    @Test
    fun reviewsFillTheWholeBudgetWhenNothingElseIsWaiting() {
        val plan = SessionPlanner.plan(input(dueItems = wordItems(100), newWordRanks = emptyList()))

        assertEquals(60, plan.reviewCount)
        assertEquals(0, plan.newWordCount)
    }

    @Test
    fun timeLeftByTheNewWordLimitGoesBackToReviews() {
        val plan =
            SessionPlanner.plan(
                input(dueItems = wordItems(100), maximumNewWords = 2),
            )

        // 39 reviews and two new words use 450 s; the other 150 s hold 15 more reviews.
        assertEquals(54, plan.reviewCount)
        assertEquals(2, plan.newWordCount)
    }

    @Test
    fun theOldestDueItemIsReviewedFirst() {
        val due =
            listOf(
                DueItem(ItemRef.Word(5), dueMillis = 300),
                DueItem(ItemRef.Word(6), dueMillis = 100),
                DueItem(ItemRef.Grammar("grammar-03-pronoun-endings"), dueMillis = 200),
            )

        val plan = SessionPlanner.plan(input(dueItems = due))

        val reviewed = plan.steps.filterIsInstance<PlanStep.Review>().map { it.item }
        assertEquals(
            listOf(ItemRef.Word(6), ItemRef.Grammar("grammar-03-pronoun-endings"), ItemRef.Word(5)),
            reviewed,
        )
    }

    @Test
    fun theStepsRunInTheAgreedOrder() {
        val plan =
            SessionPlanner.plan(
                input(
                    dueItems = wordItems(2),
                    newWordRanks = listOf(11, 12),
                    startLessons = listOf(shortLesson),
                    hasFinishedStartLesson = false,
                    grammarBroughtBy = { listOf(fullLesson) },
                ),
            )

        assertEquals(
            listOf(
                PlanStep.Lesson("basics-01-reading-direction"),
                PlanStep.Review(ItemRef.Word(1)),
                PlanStep.Review(ItemRef.Word(2)),
                PlanStep.NewWord(11),
                PlanStep.NewWord(12),
                PlanStep.Lesson("grammar-03-pronoun-endings"),
                PlanStep.SetCheck(listOf(11, 12)),
            ),
            plan.steps,
        )
    }

    @Test
    fun onlyOneStartLessonIsPlacedPerDay() {
        val second = LessonSlot("basics-02-three-letter-roots", weight = 0.4)

        val plan = SessionPlanner.plan(input(startLessons = listOf(shortLesson, second)))

        assertEquals(listOf("basics-01-reading-direction"), plan.lessonIds)
    }

    @Test
    fun wordsWaitForTheFirstStartLessonWhenItDoesNotFit() {
        val plan =
            SessionPlanner.plan(
                input(
                    budgetSeconds = 30,
                    startLessons = listOf(shortLesson),
                    hasFinishedStartLesson = false,
                ),
            )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun wordsBeginAfterTheStartLessonInTheSameSession() {
        val plan =
            SessionPlanner.plan(
                input(
                    startLessons = listOf(shortLesson),
                    hasFinishedStartLesson = false,
                ),
            )

        assertEquals(listOf("basics-01-reading-direction"), plan.lessonIds)
        assertTrue(plan.newWordCount > 0)
    }

    @Test
    fun aGrammarLessonBroughtByTheNewWordsIsPlacedWhenItFits() {
        val plan = SessionPlanner.plan(input(grammarBroughtBy = { listOf(fullLesson) }))

        assertEquals(listOf(fullLesson.lessonId), plan.lessonIds)
        // 150 s for the lesson leave 450 s, which is 10 new words after the limit of 10.
        assertEquals(10, plan.newWordCount)
    }

    @Test
    fun aGrammarLessonThatDoesNotFitWaitsForTomorrow() {
        val plan =
            SessionPlanner.plan(
                input(budgetSeconds = 300, grammarBroughtBy = { listOf(fullLesson) }),
            )

        // 300 s hold ten new words exactly, so no time is left for a 150 s lesson.
        assertEquals(10, plan.newWordCount)
        assertTrue(plan.lessonIds.isEmpty())
    }

    @Test
    fun aPendingGrammarLessonKeepsItsTimeBeforeNewWordsTakeThemAll() {
        val plan =
            SessionPlanner.plan(
                input(budgetSeconds = 300, pendingGrammar = listOf(fullLesson)),
            )

        assertEquals(listOf(fullLesson.lessonId), plan.lessonIds)
        // 150 s remain after the lesson: five new words.
        assertEquals(5, plan.newWordCount)
    }

    @Test
    fun theSetCheckCoversTheNewWordsOfTheSession() {
        val plan = SessionPlanner.plan(input(maximumNewWords = 3))

        assertEquals(PlanStep.SetCheck(listOf(1, 2, 3)), plan.steps.last())
    }

    @Test
    fun noSetCheckWithoutNewWords() {
        val plan = SessionPlanner.plan(input(dueItems = wordItems(3), newWordRanks = emptyList()))

        assertTrue(plan.steps.none { it is PlanStep.SetCheck })
    }

    @Test
    fun plannedTimeStaysWithinTheBudget() {
        val plan =
            SessionPlanner.plan(
                input(
                    dueItems = wordItems(100),
                    grammarBroughtBy = { listOf(fullLesson) },
                ),
            )

        assertTrue(plan.plannedSeconds <= 600)
        assertTrue(plan.plannedSeconds > 500)
    }
}
