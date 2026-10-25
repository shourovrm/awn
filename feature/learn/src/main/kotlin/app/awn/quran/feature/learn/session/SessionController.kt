package app.awn.quran.feature.learn.session

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.feature.learn.course.CourseChoices
import app.awn.quran.feature.learn.course.CourseContent
import app.awn.quran.feature.learn.course.CourseLesson
import app.awn.quran.feature.learn.course.CoursePlanner
import app.awn.quran.feature.learn.course.LearningArabic
import app.awn.quran.feature.learn.course.ScreenKind
import app.awn.quran.feature.learn.data.SessionLogEntity
import app.awn.quran.feature.learn.data.StepItemKind
import app.awn.quran.feature.learn.data.StepOutcome
import app.awn.quran.feature.learn.progress.LearnClock
import app.awn.quran.feature.learn.words.CourseSnapshot
import app.awn.quran.feature.learn.words.FinishedStep
import app.awn.quran.feature.learn.words.LevelComplete
import app.awn.quran.feature.learn.words.WordCourseRepository
import app.awn.quran.feature.learn.words.WordFrequency
import app.awn.quran.feature.learn.words.WordProgress
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Everything a session reads and writes; built once by the Learn host. */
// One field per service the session reads; a smaller bundle would only hide them.
@Suppress("LongParameterList")
class SessionDependencies(
    val repository: WordCourseRepository,
    val factory: QuestionFactory,
    val content: CourseContent,
    val frequency: WordFrequency,
    val infoSource: WordInfoSource,
    val arabic: LearningArabic,
    val planner: CoursePlanner,
    val choices: CourseChoices,
    val languageCode: String,
    val clock: LearnClock = LearnClock.System,
    /** The word levels whose completion screen the user has already seen. */
    val celebratedLevels: suspend () -> Set<Int> = { emptySet() },
)

/**
 * Runs one session or quiz: the steps of a [TodayPlan] one screen at a time. [state] holds the
 * screen to draw; the user's presses call [next], [answer] and [close].
 *
 * An answer in a plan is rated when the user taps one of the four ratings on the answer screen
 * ([rate]), so a user who leaves halfway keeps what was rated. The rating the answer earned (Good
 * when right, Again when wrong or "I don't know") is the one the screen suggests. In a quiz the
 * user started ([isOwnQuiz]) answers are only logged at once: no card and no date changes.
 */
// One method per button on a session screen, plus the steps that build the next screen.
@Suppress("TooManyFunctions")
class SessionController(
    private val steps: List<PlanStep>,
    private val dependencies: SessionDependencies,
    private val isOwnQuiz: Boolean,
    private val plannedSeconds: Int,
) {
    private val mutableState =
        MutableStateFlow(SessionState(frame = null, progress = 0f, secondsLeft = plannedSeconds))
    val state: StateFlow<SessionState> = mutableState

    private val repository = dependencies.repository
    private var snapshotAtStart = CourseSnapshot.Empty
    private var stepIndex = 0
    private var subIndex = 0
    private var shownAtMillis = 0L
    private var lessonStartedAtMillis = 0L
    private var startedAtMillis = 0L
    private var revised = 0
    private var introduced = 0
    private var correct = 0
    private var lastAyahWord: WordKey? = null

    // How long the user took to answer, kept apart from the time spent choosing a rating.
    private var answerMillis = 0L
    private var pace = Pace.Default

    /** Loads the state the questions need and shows the first screen. */
    suspend fun start() {
        snapshotAtStart = repository.snapshot()
        pace = repository.pace()
        startedAtMillis = nowMillis()
        showCurrent()
    }

    /** The user pressed the button that moves on: Next on a reading, new word or answer screen. */
    suspend fun next() {
        when (val frame = mutableState.value.frame) {
            is SessionFrame.NewWord -> finishNewWord(frame)
            is SessionFrame.Reading -> subIndex++
            is SessionFrame.Answer -> {
                // A rated answer moves on through rate(); only a quiz answer has a Next button.
                if (frame.ratingChoices != null) return
                finishAnswer(frame)
            }
            else -> return
        }
        showCurrent()
    }

    /** The user tapped [rating] on the answer screen: it is stored and the session moves on. */
    suspend fun rate(rating: FsrsRating) {
        val frame = mutableState.value.frame as? SessionFrame.Answer ?: return
        if (frame.ratingChoices == null) return
        record(frame.question, rating, outcomeOf(frame.question, frame.chosenIndex), answerMillis)
        finishAnswer(frame)
        showCurrent()
    }

    /** The user chose option [optionIndex] of the open question, or null for "I don't know". */
    suspend fun answer(optionIndex: Int?) {
        val frame = mutableState.value.frame as? SessionFrame.Ask ?: return
        val question = frame.question
        val isRight = optionIndex == question.correctIndex
        if (isRight) correct++
        answerMillis = nowMillis() - shownAtMillis
        val ratingChoices = if (isOwnQuiz) null else ratingChoicesFor(question, isRight)
        if (ratingChoices == null) {
            record(question, FsrsRating.Good, outcomeOf(question, optionIndex), answerMillis)
        }
        question.answerWord?.let { lastAyahWord = it }
        publish(SessionFrame.Answer(question, optionIndex, ratingChoices, frame.label))
    }

    private fun outcomeOf(question: Question, optionIndex: Int?): String =
        when {
            optionIndex == null -> StepOutcome.DONT_KNOW
            optionIndex == question.correctIndex -> StepOutcome.CORRECT
            else -> StepOutcome.WRONG
        }

    private suspend fun ratingChoicesFor(question: Question, isRight: Boolean): RatingChoices {
        val dueByRating =
            when (val subject = question.subject) {
                is ItemRef.Word -> repository.previewWord(subject.rank)
                is ItemRef.Grammar -> repository.previewGrammar(subject.lessonId)
            }
        val earned = if (isRight) FsrsRating.Good else FsrsRating.Again
        return RatingChoices(earned, dueByRating.mapValues { daysUntil(it.value) })
    }

    /**
     * Writes the session log when the user leaves before the end; a finished one is logged already.
     */
    suspend fun close() {
        if (mutableState.value.frame !is SessionFrame.Finished) logSession()
    }

    private suspend fun showCurrent() {
        while (true) {
            val step = steps.getOrNull(stepIndex)
            if (step == null) {
                finish()
                return
            }
            val frame = frameFor(step)
            if (frame != null) {
                publish(frame)
                return
            }
            stepIndex++
            subIndex = 0
        }
    }

    /**
     * The screen for [step] at the current [subIndex], or null when the step has no more screens.
     */
    private suspend fun frameFor(step: PlanStep): SessionFrame? =
        when (step) {
            is PlanStep.NewWord -> newWordFrame(step)
            is PlanStep.Review -> if (subIndex == 0) askFrame(step) else null
            is PlanStep.SetCheck -> setCheckFrame(step)
            is PlanStep.Lesson -> lessonFrame(step)
        }

    @Suppress("ReturnCount") // Guards that skip a step with nothing to show.
    private suspend fun newWordFrame(step: PlanStep.NewWord): SessionFrame? {
        if (subIndex > 0) return null
        val info = dependencies.infoSource.info(step.rank) ?: return null
        val ordinal = steps.filterIsInstance<PlanStep.NewWord>().indexOf(step) + 1
        val total = steps.count { it is PlanStep.NewWord }
        val occurrences = dependencies.frequency.occurrences(step.rank)
        val candidates = occurrences.map { AyahRef(it.surah, it.ayah) }.distinct()
        val ayahs =
            candidates
                .map { it to dependencies.arabic.ayah(it).words.size }
                .sortedBy { it.second }
                .take(NEW_WORD_AYAHS)
                .map { it.first }
        val shown = occurrences.filter { AyahRef(it.surah, it.ayah) in ayahs }
        return SessionFrame.NewWord(info, ayahs, FrameLabel.NewWord(ordinal, total), shown)
    }

    private suspend fun askFrame(step: PlanStep.Review): SessionFrame? {
        val question = questionFor(step.item) ?: return null
        return SessionFrame.Ask(question, confirmWithButton = false, label = FrameLabel.None)
    }

    @Suppress("ReturnCount") // Guards that skip a step with nothing to show.
    private suspend fun setCheckFrame(step: PlanStep.SetCheck): SessionFrame? {
        val rank = step.ranks.getOrNull(subIndex) ?: return null
        val question = questionFor(ItemRef.Word(rank)) ?: return null
        return SessionFrame.Ask(
            question,
            confirmWithButton = true,
            label = FrameLabel.Check(subIndex + 1, step.ranks.size),
        )
    }

    @Suppress("ReturnCount") // Guards that skip a step with nothing to show.
    private suspend fun lessonFrame(step: PlanStep.Lesson): SessionFrame? {
        val lesson = dependencies.content.lesson(step.lessonId) ?: return null
        val text = lesson.text(dependencies.languageCode)
        val readings = text.screens.filter { it.kind == ScreenKind.Reading }
        if (subIndex == 0) lessonStartedAtMillis = nowMillis()
        if (subIndex < readings.size) {
            return SessionFrame.Reading(
                lesson,
                readings[subIndex],
                FrameLabel.LessonScreen(subIndex + 1, readings.size),
            )
        }
        if (subIndex == readings.size && lesson.hasCheck) {
            val question =
                dependencies.factory.forGrammar(lesson, dependencies.languageCode) ?: return null
            return SessionFrame.Ask(question, confirmWithButton = true, label = FrameLabel.None)
        }
        finishLesson(lesson, outcome = null)
        return null
    }

    private suspend fun questionFor(item: ItemRef): Question? =
        when (item) {
            is ItemRef.Word -> {
                val card = snapshotAtStart.wordCards[item.rank]
                val last = card?.lastQuestionType?.let(QuestionType::fromStored)
                dependencies.factory.forWord(
                    item.rank,
                    last,
                    isWeak = card != null && WordProgress.isWeak(card),
                )
            }
            is ItemRef.Grammar ->
                dependencies.content.lesson(item.lessonId)?.let {
                    dependencies.factory.forGrammar(it, dependencies.languageCode)
                }
        }

    /** Logs the answer of a quiz, or rates the answer of a plan with [rating]. */
    private suspend fun record(
        question: Question,
        rating: FsrsRating,
        outcome: String,
        elapsed: Long,
    ) {
        val subject = question.subject
        val step =
            FinishedStep(
                kind =
                    if (steps.getOrNull(stepIndex) is PlanStep.SetCheck) StepKind.NewWord
                    else StepKind.Review,
                itemKind = if (subject is ItemRef.Word) StepItemKind.WORD else StepItemKind.LESSON,
                item =
                    if (subject is ItemRef.Word) subject.rank.toString()
                    else (subject as ItemRef.Grammar).lessonId,
                questionType = question.type,
                outcome = outcome,
                millis = elapsed,
                isOwnQuiz = isOwnQuiz,
            )
        if (isOwnQuiz) {
            repository.logStep(step)
            return
        }
        if (steps.getOrNull(stepIndex) is PlanStep.Review) revised++
        when (subject) {
            is ItemRef.Word -> repository.rateWord(subject.rank, rating, step)
            is ItemRef.Grammar -> repository.rateGrammar(subject.lessonId, rating, step)
        }
    }

    private suspend fun finishNewWord(frame: SessionFrame.NewWord) {
        val elapsed = nowMillis() - shownAtMillis
        repository.meet(frame.info.rank)
        repository.logStep(
            WordCourseRepository.seenStep(StepKind.NewWord, frame.info.rank, elapsed),
        )
        introduced++
        lastAyahWord = frame.info.representative
        advanceStep()
    }

    private suspend fun finishAnswer(frame: SessionFrame.Answer) {
        val step = steps.getOrNull(stepIndex)
        if (step is PlanStep.Lesson) {
            val lesson = dependencies.content.lesson(step.lessonId)
            if (lesson != null) finishLesson(lesson, frame.isCorrect)
            advanceStep()
        } else if (step is PlanStep.SetCheck) {
            subIndex++
        } else {
            advanceStep()
        }
    }

    private suspend fun finishLesson(lesson: CourseLesson, outcome: Boolean?) {
        val millis = nowMillis() - lessonStartedAtMillis
        val outcomeText =
            when (outcome) {
                null -> StepOutcome.SEEN
                true -> StepOutcome.CORRECT
                false -> StepOutcome.WRONG
            }
        val step =
            FinishedStep(StepKind.Lesson, StepItemKind.LESSON, lesson.id, null, outcomeText, millis)
        repository.finishLesson(lesson.id, step)
    }

    private fun advanceStep() {
        stepIndex++
        subIndex = 0
    }

    private suspend fun finish() {
        val summary = summary()
        logSession()
        mutableState.value =
            SessionState(SessionFrame.Finished(summary), progress = 1f, secondsLeft = 0)
    }

    private suspend fun logSession() {
        if (isOwnQuiz) return
        repository.logSession(
            SessionLogEntity(
                startedAt = startedAtMillis,
                plannedSeconds = plannedSeconds,
                elapsedMillis = nowMillis() - startedAtMillis,
                reviews = revised,
                newWords = introduced,
                lessons = steps.count { it is PlanStep.Lesson },
                wordsKnownBefore = snapshotAtStart.wordsKnown,
                wordsMetBefore = snapshotAtStart.wordsMet,
            ),
        )
    }

    private suspend fun summary(): SessionSummary {
        val after = repository.snapshot()
        val tomorrow =
            dependencies.planner.plan(
                after,
                pace,
                dependencies.choices,
                dependencies.clock.now().plus(Duration.ofDays(1)),
                dependencies.clock.zone(),
            )
        return SessionSummary(
            isOwnQuiz = isOwnQuiz,
            wordsRevised = revised,
            newWords = introduced,
            answeredCorrectly = correct,
            wordsMetBefore = snapshotAtStart.wordsMet,
            wordsMetAfter = after.wordsMet,
            wordsKnownBefore = snapshotAtStart.wordsKnown,
            wordsKnownAfter = after.wordsKnown,
            coverage = WordProgress.coverage(after.knownRanks, dependencies.frequency),
            lastAyah = lastAyahSummary(after),
            tomorrowReviews = tomorrow.reviewCount,
            tomorrowNewWords = tomorrow.newWordCount,
            finishedLevel = finishedLevelOf(after),
        )
    }

    // A quiz the user started changes no card, so it never finishes a level.
    private suspend fun finishedLevelOf(after: CourseSnapshot): Int? {
        if (isOwnQuiz) return null
        return LevelComplete.toCelebrate(
                metBefore = snapshotAtStart.wordCards.keys,
                metAfter = after.wordCards.keys,
                formCount = dependencies.frequency.formCount,
                celebrated = dependencies.celebratedLevels(),
            )
            ?.number
    }

    private suspend fun lastAyahSummary(after: CourseSnapshot): LastAyah? {
        val key = lastAyahWord ?: return null
        val ayah = AyahRef(key.surah, key.ayah)
        val known = after.knownRanks.toSet()
        val words =
            dependencies.arabic.ayah(ayah).words.map {
                LastAyahWord(it.key, it.text, dependencies.frequency.rankOf(it.key) in known)
            }
        return LastAyah(ayah, words)
    }

    private fun publish(frame: SessionFrame) {
        shownAtMillis = nowMillis()
        mutableState.value = SessionState(frame, progress(), secondsLeft())
    }

    private fun progress(): Float {
        val total = totalUnits()
        return if (total == 0) 0f else (unitsDone().toFloat() / total).coerceIn(0f, 1f)
    }

    private fun totalUnits(): Int = steps.sumOf(::unitsIn)

    private fun unitsDone(): Int =
        steps.take(stepIndex).sumOf(::unitsIn) +
            subIndex.coerceAtMost(unitsIn(steps.getOrNull(stepIndex)))

    private fun unitsIn(step: PlanStep?): Int =
        when (step) {
            is PlanStep.SetCheck -> step.ranks.size
            null -> 0
            else -> 1
        }

    private fun secondsLeft(): Int {
        val remaining = steps.drop(stepIndex)
        val seconds = remaining.sumOf { step ->
            when (step) {
                is PlanStep.Review -> pace.secondsPerReview
                is PlanStep.NewWord -> pace.secondsPerNewWord
                is PlanStep.SetCheck -> 0.0
                is PlanStep.Lesson -> pace.secondsPerLesson
            }
        }
        return seconds.toInt()
    }

    private fun daysUntil(dueMillis: Long): Int {
        val zone = dependencies.clock.zone()
        val today = dependencies.clock.now().atZone(zone).toLocalDate()
        val due = java.time.Instant.ofEpochMilli(dueMillis).atZone(zone).toLocalDate()
        return Duration.between(today.atStartOfDay(), due.atStartOfDay())
            .toDays()
            .toInt()
            .coerceAtLeast(1)
    }

    private fun nowMillis(): Long = dependencies.clock.now().toEpochMilli()

    private companion object {
        /** A new word is shown with two ayahs, the shortest ones, so they fit the screen. */
        const val NEW_WORD_AYAHS = 2
    }
}
