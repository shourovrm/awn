package app.awn.quran.feature.learn.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.feature.learn.course.CourseChoices
import app.awn.quran.feature.learn.course.CoursePlanner
import app.awn.quran.feature.learn.course.LearningArabic
import app.awn.quran.feature.learn.course.fixtureContent
import app.awn.quran.feature.learn.data.LearnDatabase
import app.awn.quran.feature.learn.progress.LearnClock
import app.awn.quran.feature.learn.words.WordCourseRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionControllerTest {

    private val now = Instant.parse("2026-10-21T08:00:00Z")
    private lateinit var database: LearnDatabase
    private lateinit var repository: WordCourseRepository
    private lateinit var dependencies: SessionDependencies

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, LearnDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val clock = LearnClock(now = { now }, zone = { ZoneOffset.UTC })
        repository = WordCourseRepository(database, clock)
        val frequency = FakeFrequency(formCount = 100)
        val quranText =
            FakeQuranTextRepository(
                wordsByAyah =
                    (1..100)
                        .associate { rank ->
                            AyahRef(2, rank) to
                                (1..4).map { position ->
                                    Word(WordKey(2, rank, position), "w$position", null, null)
                                }
                        }
                        .toMutableMap(),
            )
        val infoSource = FakeInfoSource()
        val choices = CourseChoices(sessionMinutes = 10, includeLetters = false)
        dependencies =
            SessionDependencies(
                repository = repository,
                factory = QuestionFactory(frequency, infoSource, Random(7)),
                content = fixtureContent(),
                frequency = frequency,
                infoSource = infoSource,
                arabic = LearningArabic(quranText, FakePackRepository()),
                planner = CoursePlanner(fixtureContent(), frequency),
                choices = choices,
                languageCode = "en",
                clock = clock,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun controller(steps: List<PlanStep>, isOwnQuiz: Boolean = false) =
        SessionController(steps, dependencies, isOwnQuiz, plannedSeconds = 600)

    private fun SessionController.frame(): SessionFrame = checkNotNull(state.value.frame)

    private suspend fun SessionController.answerRight() {
        val ask = frame() as SessionFrame.Ask
        answer(ask.question.correctIndex)
    }

    private suspend fun SessionController.tapEarnedRating() {
        val answer = frame() as SessionFrame.Answer
        rate(checkNotNull(answer.ratingChoices).earned)
    }

    @Test
    fun newWordsAreMetThenCheckedAndEachCheckGivesTheFirstRating() = runBlocking {
        val session =
            controller(
                listOf(PlanStep.NewWord(1), PlanStep.NewWord(2), PlanStep.SetCheck(listOf(1, 2))),
            )
        session.start()
        assertTrue(session.frame() is SessionFrame.NewWord)
        session.next()
        session.next()

        // The set check asks about both words, the user is right on the first and not sure on the
        // second.
        val first = session.frame() as SessionFrame.Ask
        assertEquals(FrameLabel.Check(1, 2), first.label)
        assertTrue(first.confirmWithButton)
        session.answer(first.question.correctIndex)
        session.tapEarnedRating()
        session.answer(null)
        session.tapEarnedRating()

        val snapshot = repository.snapshot()
        assertEquals(2, snapshot.wordsMet)
        assertEquals(1, snapshot.wordCards.getValue(1).reps)
        assertEquals(0, snapshot.wordCards.getValue(1).lapses)
        assertTrue(snapshot.wordCards.getValue(1).due > now.toEpochMilli())
        assertTrue(session.frame() is SessionFrame.Finished)
    }

    @Test
    fun anAnswerOfAPlanOffersFourRatingsAndStoresNothingBeforeATap() = runBlocking {
        repository.meet(5)
        val before = repository.snapshot().wordCards.getValue(5)
        val session = controller(listOf(PlanStep.Review(ItemRef.Word(5))))
        session.start()

        val ask = session.frame() as SessionFrame.Ask
        assertTrue(!ask.confirmWithButton)
        session.answer((ask.question.correctIndex + 1) % ask.question.options.size)

        val answer = session.frame() as SessionFrame.Answer
        val choices = checkNotNull(answer.ratingChoices)
        assertTrue(!answer.isCorrect)
        assertEquals(FsrsRating.Again, choices.earned)
        assertEquals(FsrsRating.entries.toSet(), choices.daysByRating.keys)
        assertTrue(choices.daysByRating.values.all { it >= 1 })
        assertEquals(before, repository.snapshot().wordCards.getValue(5))
        assertEquals(0, database.wordDao().wordOutcomes().size)
        session.next()
        assertTrue(session.frame() is SessionFrame.Answer)
    }

    @Test
    fun aRightAnswerEarnsGoodAndLongerRatingsReturnLater() = runBlocking {
        repository.meet(5)
        val session = controller(listOf(PlanStep.Review(ItemRef.Word(5))))
        session.start()
        session.answerRight()

        val choices = checkNotNull((session.frame() as SessionFrame.Answer).ratingChoices)
        assertEquals(FsrsRating.Good, choices.earned)
        val days = choices.daysByRating
        assertTrue(days.getValue(FsrsRating.Again) <= days.getValue(FsrsRating.Hard))
        assertTrue(days.getValue(FsrsRating.Hard) <= days.getValue(FsrsRating.Good))
        assertTrue(days.getValue(FsrsRating.Good) <= days.getValue(FsrsRating.Easy))
    }

    @Test
    fun tappingARatingStoresThatRatingAndMovesOn() = runBlocking {
        repository.meet(5)
        val dueIfEasy = repository.previewWord(5).getValue(FsrsRating.Easy)
        val session = controller(listOf(PlanStep.Review(ItemRef.Word(5))))
        session.start()
        session.answerRight()

        session.rate(FsrsRating.Easy)

        val card = repository.snapshot().wordCards.getValue(5)
        assertEquals(1, card.reps)
        assertEquals(dueIfEasy, card.due)
        assertEquals(1, database.wordDao().wordOutcomes().size)
        assertTrue(session.frame() is SessionFrame.Finished)
    }

    @Test
    fun aQuizTheUserStartedMovesNoCardAndOffersNoRatings() = runBlocking {
        repository.meet(5)
        val before = repository.snapshot().wordCards.getValue(5)
        val session = controller(listOf(PlanStep.Review(ItemRef.Word(5))), isOwnQuiz = true)
        session.start()

        session.answerRight()

        assertEquals(before, repository.snapshot().wordCards.getValue(5))
        assertNull((session.frame() as SessionFrame.Answer).ratingChoices)
        assertEquals(1, database.wordDao().wordOutcomes().size)
        session.next()
        assertTrue(session.frame() is SessionFrame.Finished)
    }

    @Test
    fun theNewWordScreenMarksEveryPlaceOfTheWordInTheShownAyahs() = runBlocking {
        val session = controller(listOf(PlanStep.NewWord(3)))
        session.start()

        val frame = session.frame() as SessionFrame.NewWord
        assertEquals(listOf(WordKey(2, 3, 1)), frame.occurrences)
    }

    @Test
    fun leavingAQuizWritesNoSessionLog() = runBlocking {
        repository.meet(5)
        val session = controller(listOf(PlanStep.Review(ItemRef.Word(5))), isOwnQuiz = true)
        session.start()
        session.answerRight()
        session.next()
        session.close()

        assertNull(repository.latestSession())
    }

    @Test
    fun aLessonWithACheckIsFinishedAndItsGrammarCardIsCreated() = runBlocking {
        val session = controller(listOf(PlanStep.Lesson("grammar-03")))
        session.start()
        assertTrue(session.frame() is SessionFrame.Reading)
        session.next()

        session.answerRight()
        session.tapEarnedRating()

        val snapshot = repository.snapshot()
        assertTrue("grammar-03" in snapshot.doneLessonIds)
        assertNotNull(snapshot.grammarCards["grammar-03"])
        assertTrue(session.frame() is SessionFrame.Finished)
    }

    @Test
    fun aLessonWithoutACheckIsFinishedAfterItsLastScreen() = runBlocking {
        val session = controller(listOf(PlanStep.Lesson("basics-01")))
        session.start()
        session.next()

        assertTrue("basics-01" in repository.snapshot().doneLessonIds)
        assertTrue(session.frame() is SessionFrame.Finished)
    }

    @Test
    fun theEndSummaryCountsWhatWasDoneAndNamesTheLastAyah() = runBlocking {
        val session = controller(listOf(PlanStep.NewWord(3), PlanStep.SetCheck(listOf(3))))
        session.start()
        session.next()
        session.answerRight()
        session.tapEarnedRating()

        val summary = (session.frame() as SessionFrame.Finished).summary
        assertEquals(1, summary.newWords)
        assertEquals(0, summary.wordsMetBefore)
        assertEquals(1, summary.wordsMetAfter)
        assertEquals(AyahRef(2, 3), summary.lastAyah!!.ayah)
        assertEquals(4, summary.lastAyah!!.words.size)
        assertEquals(1, repository.latestSession()!!.newWords)
    }

    @Test
    fun progressGrowsAsStepsFinish() = runBlocking {
        repository.meet(1)
        repository.meet(2)
        val session =
            controller(listOf(PlanStep.Review(ItemRef.Word(1)), PlanStep.Review(ItemRef.Word(2))))
        session.start()
        val atStart = session.state.value.progress

        session.answerRight()
        session.tapEarnedRating()

        assertEquals(0f, atStart, 0f)
        assertEquals(0.5f, session.state.value.progress, 0f)
    }
}
