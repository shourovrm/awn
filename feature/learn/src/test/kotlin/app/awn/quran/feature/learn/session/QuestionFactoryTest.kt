package app.awn.quran.feature.learn.session

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.learn.course.CourseContentReader
import app.awn.quran.feature.learn.course.CourseLesson
import app.awn.quran.feature.learn.words.WordFrequency
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric supplies org.json, which reads the grammar lesson in the last two tests.
@RunWith(RobolectricTestRunner::class)
class QuestionFactoryTest {

    private fun factory(
        infoSource: WordInfoSource = FakeInfoSource(),
        seed: Int = 1,
    ) = QuestionFactory(FakeFrequency(), infoSource, Random(seed))

    @Test
    fun aWordQuestionHasFourDistinctOptionsAndOneRightAnswer() = runTest {
        repeat(40) { seed ->
            val question =
                checkNotNull(factory(seed = seed).forWord(50, last = null, isWeak = false))

            assertEquals(4, question.options.size)
            assertEquals(4, question.options.toSet().size)
            assertNotNull(question.answerWord)
        }
    }

    @Test
    fun theAnswerMarksEveryPlaceOfTheFormInTheAskedAyah() = runTest {
        // The form stands twice in ayah 2:50 (as ثُمَّ does in 2:28) and once in 3:1.
        val frequency =
            object : WordFrequency by FakeFrequency() {
                override fun occurrences(rank: Int) =
                    listOf(WordKey(2, rank, 1), WordKey(2, rank, 3), WordKey(3, 1, 2))
            }
        val question =
            checkNotNull(
                QuestionFactory(frequency, FakeInfoSource(), Random(1))
                    .forWord(50, last = null, isWeak = false),
            )

        assertEquals(listOf(WordKey(2, 50, 1), WordKey(2, 50, 3)), question.answerOccurrences)
    }

    @Test
    fun theRightOptionIsTheWordsOwnMeaningOrWordInMeaningQuestions() = runTest {
        repeat(40) { seed ->
            val question =
                checkNotNull(factory(seed = seed).forWord(50, last = null, isWeak = false))

            val right = question.options[question.correctIndex]
            when (question.type) {
                QuestionType.Meaning -> assertEquals("meaning 50", right)
                QuestionType.Reverse,
                QuestionType.FillAyah -> assertEquals("{{2:50:1}}", right)
                QuestionType.Grammar -> error("A word is not asked as grammar.")
            }
        }
    }

    @Test
    fun reverseQuestionsShowTheMeaningAndFillQuestionsBlankTheWord() = runTest {
        val seen = mutableMapOf<QuestionType, Question>()
        repeat(80) { seed ->
            val question =
                checkNotNull(factory(seed = seed).forWord(50, last = null, isWeak = false))
            seen[question.type] = question
        }

        assertEquals(
            QuestionPrompt.Markup("meaning 50"),
            seen.getValue(QuestionType.Reverse).prompt,
        )
        assertEquals(
            QuestionPrompt.Markup("{{2:50:1}}"),
            seen.getValue(QuestionType.Meaning).prompt,
        )
        assertEquals(
            QuestionPrompt.AyahWithBlank(AyahRef(2, 50), blankPosition = 1),
            seen.getValue(QuestionType.FillAyah).prompt,
        )
    }

    @Test
    fun theTypeChangesFromTheLastOne() = runTest {
        repeat(60) { seed ->
            val question =
                checkNotNull(factory(seed = seed).forWord(50, QuestionType.Meaning, isWeak = false))

            assertTrue(question.type != QuestionType.Meaning)
        }
    }

    @Test
    fun allTypesAppearOverManyQuestions() = runTest {
        val types = (0 until 100).map { factory(seed = it).forWord(50, null, false)!!.type }.toSet()

        assertEquals(
            setOf(QuestionType.Meaning, QuestionType.Reverse, QuestionType.FillAyah),
            types,
        )
    }

    @Test
    fun aWeakWordIsNeverAskedAsFillTheAyah() = runTest {
        repeat(60) { seed ->
            val question = checkNotNull(factory(seed = seed).forWord(50, null, isWeak = true))

            assertTrue(question.type != QuestionType.FillAyah)
        }
    }

    @Test
    fun aLongAyahIsNotUsedForFillTheAyah() = runTest {
        val source = FakeInfoSource(ayahWordCounts = { 120 })
        repeat(60) { seed ->
            val question = checkNotNull(factory(source, seed).forWord(50, null, isWeak = false))

            assertTrue(question.type != QuestionType.FillAyah)
        }
    }

    @Test
    fun noMeaningInstalledMeansNoQuestion() = runTest {
        val noMeanings = FakeInfoSource(meanings = emptyMap())

        assertNull(factory(noMeanings).forWord(50, null, isWeak = false))
    }

    @Test
    fun aWordWhoseSimilarFormsAllShareItsMeaningHasNoQuestion() = runTest {
        val sameMeaning = FakeInfoSource(meanings = (1..200).associateWith { "the same" })

        assertNull(factory(sameMeaning).forWord(50, null, isWeak = false))
    }

    @Test
    fun distractorsAreFormsOfSimilarRank() = runTest {
        repeat(20) { seed ->
            val question =
                checkNotNull(
                    QuestionFactory(FakeFrequency(), FakeInfoSource(), Random(seed))
                        .forWord(100, null, false),
                )
            val ranks =
                question.options.map { option ->
                    if (option.startsWith("{{")) {
                        WordKey.parse(option.removeSurrounding("{{", "}}")).ayah
                    } else {
                        option.removePrefix("meaning ").toInt()
                    }
                }

            ranks.forEach { assertTrue("rank $it is far from 100", it in 70..130) }
        }
    }

    @Test
    fun aGrammarQuestionShufflesOptionsAndKeepsTheRightOne() {
        val lesson = grammarLesson()

        val answers =
            (0 until 30).map { seed ->
                val question = checkNotNull(factory(seed = seed).forGrammar(lesson, "en"))
                question.options[question.correctIndex]
            }

        assertEquals(setOf("you, more than one"), answers.toSet())
    }

    @Test
    fun aGrammarQuestionCarriesTheAnswerScreenAsItsExplanation() {
        val question = checkNotNull(factory().forGrammar(grammarLesson(), "en"))

        assertTrue(question.explanation.isNotEmpty())
        assertEquals(ItemRef.Grammar("grammar-03-pronoun-endings"), question.subject)
        assertEquals(QuestionType.Grammar, question.type)
    }

    private fun grammarLesson(): CourseLesson {
        val json =
            """
            {"formatVersion":1,"references":[],"lessons":[{"id":"grammar-03-pronoun-endings",
            "course":"grammar","order":3,"introducedBy":["2:21:4"],"examples":[],"reference":"",
            "licence":"x","reviewed":false,"languages":{"en":{"title":"Pronoun endings","screens":[
            {"kind":"check","title":"Check","check":{"question":"Choose","ayah":"2:21",
            "highlight":"2:21:6","options":["them","you, more than one","us","him"],"answer":1}},
            {"kind":"answer","title":"Answer","blocks":[{"type":"paragraph","text":"It is you."}]}
            ]}}}]}
            """
                .trimIndent()
        return CourseContentReader.parse(json).lessons.single()
    }
}
