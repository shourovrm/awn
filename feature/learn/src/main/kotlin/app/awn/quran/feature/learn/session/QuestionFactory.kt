package app.awn.quran.feature.learn.session

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.learn.course.CourseLesson
import app.awn.quran.feature.learn.course.LessonCheck
import app.awn.quran.feature.learn.words.WordFrequency
import kotlin.random.Random

/**
 * Builds the questions of a session. A word gets a random type each time it comes back; the
 * question for a grammar point is the check at the end of its lesson, with its options shuffled.
 */
class QuestionFactory(
    private val frequency: WordFrequency,
    private val infoSource: WordInfoSource,
    private val random: Random = Random.Default,
) {

    /**
     * Returns a question about the form of [rank], or null when no meaning is installed for it or
     * for any similar form. [last] is the type this word was asked in last time. A word the user
     * often forgets ([isWeak]) is asked only in the two recognition types, which are the easiest.
     */
    @Suppress("ReturnCount") // A word without a meaning or distractors has no question.
    suspend fun forWord(rank: Int, last: QuestionType?, isWeak: Boolean): Question? {
        val target = infoSource.info(rank) ?: return null
        val distractors = distractorsFor(target)
        if (distractors.isEmpty()) return null
        val type = QuestionTypeChoice.choose(availableTypes(target, isWeak), last, random)
        val optionInfos = (distractors + target).shuffled(random)
        val prompt =
            when (type) {
                QuestionType.Meaning -> QuestionPrompt.Markup(wordMarkup(target.representative))
                QuestionType.Reverse -> QuestionPrompt.Markup(target.meaning)
                QuestionType.FillAyah ->
                    QuestionPrompt.AyahWithBlank(
                        ayah = AyahRef(target.representative.surah, target.representative.ayah),
                        blankPosition = target.representative.wordPosition,
                    )
                QuestionType.Grammar -> error("A word is never asked as a grammar question.")
            }
        val options = optionInfos.map {
            if (type == QuestionType.Meaning) it.meaning else wordMarkup(it.representative)
        }
        return Question(
            type = type,
            subject = ItemRef.Word(rank),
            prompt = prompt,
            options = options,
            correctIndex = optionInfos.indexOfFirst { it.rank == rank },
            answerWord = target.representative,
            answerOccurrences = occurrencesInAyahOf(target.representative, rank),
        )
    }

    /** The check of grammar lesson [lesson] as a question, or null when the lesson has no check. */
    fun forGrammar(lesson: CourseLesson, languageCode: String): Question? {
        val text = lesson.text(languageCode)
        val check: LessonCheck = text.check ?: return null
        val order = check.options.indices.shuffled(random)
        return Question(
            type = QuestionType.Grammar,
            subject = ItemRef.Grammar(lesson.id),
            prompt = QuestionPrompt.LessonCheck(check.question, check.ayah, check.highlight),
            options = order.map { check.options[it] },
            correctIndex = order.indexOf(check.correctIndex),
            answerWord = null,
            explanation = text.answerBlocks,
        )
    }

    // The same form often stands twice in one ayah (ثُمَّ in 2:28); marking one of them looks
    // wrong.
    private fun occurrencesInAyahOf(word: WordKey, rank: Int): List<WordKey> =
        frequency.occurrences(rank).filter { it.surah == word.surah && it.ayah == word.ayah }

    private suspend fun distractorsFor(target: WordInfo): List<WordInfo> {
        val candidates =
            DistractorChoice.candidateRanks(target.rank, frequency.formCount).mapNotNull {
                infoSource.info(it)
            }
        return DistractorChoice.choose(target, candidates, DISTRACTOR_COUNT, random)
    }

    private fun availableTypes(target: WordInfo, isWeak: Boolean): List<QuestionType> {
        val types = mutableListOf(QuestionType.Meaning, QuestionType.Reverse)
        val fitsAnAyah = target.ayahWordCount in MINIMUM_AYAH_WORDS..MAXIMUM_AYAH_WORDS
        if (fitsAnAyah && !isWeak) types += QuestionType.FillAyah
        return types
    }

    private fun wordMarkup(key: WordKey): String = "{{$key}}"

    companion object {
        /** Three wrong answers and the right one. */
        const val DISTRACTOR_COUNT = 3

        /** An ayah of one or two words has nothing left to read once a word is hidden. */
        const val MINIMUM_AYAH_WORDS = 3

        /** A longer ayah would not fit on the question screen beside four answers. */
        const val MAXIMUM_AYAH_WORDS = 30
    }
}
