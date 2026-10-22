package app.awn.quran.feature.learn.session

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.learn.course.LessonBlock
import kotlin.random.Random

/** How a question asks. The text values are stored with the quiz history, so never change them. */
enum class QuestionType(val stored: String) {
    /** The Arabic word is shown; choose its meaning. */
    Meaning("meaning"),

    /** The meaning is shown; choose the Arabic word. */
    Reverse("reverse"),

    /** An ayah with one word left out; choose the word. */
    FillAyah("fill_ayah"),

    /** The check of a grammar lesson. */
    Grammar("grammar");

    companion object {
        fun fromStored(stored: String): QuestionType = entries.first { it.stored == stored }
    }
}

/** What the top of a question shows. Text is lesson markup: see [LessonMarkup]. */
sealed interface QuestionPrompt {
    data class Markup(val text: String) : QuestionPrompt

    /** [ayah] with the word at [blankPosition] hidden. */
    data class AyahWithBlank(val ayah: AyahRef, val blankPosition: Int) : QuestionPrompt

    /** A lesson check: [question] over [ayah], with [highlight] marked when the lesson sets it. */
    data class LessonCheck(val question: String, val ayah: AyahRef?, val highlight: WordKey?) :
        QuestionPrompt
}

/**
 * One question. [options] are lesson markup (a meaning is plain text, an Arabic word is
 * `{{surah:ayah:word}}`). [answerWord] is the word shown inside its ayah after the user answers;
 * [explanation] is the answer screen of a grammar lesson.
 */
data class Question(
    val type: QuestionType,
    val subject: ItemRef,
    val prompt: QuestionPrompt,
    val options: List<String>,
    val correctIndex: Int,
    val answerWord: WordKey?,
    val explanation: List<LessonBlock> = emptyList(),
) {
    init {
        require(options.size >= 2) { "A question needs at least two options." }
        require(correctIndex in options.indices) {
            "The correct option $correctIndex does not exist."
        }
    }
}

/** What the question factory knows about one form: its representative word and its meaning. */
data class WordInfo(
    val rank: Int,
    val representative: WordKey,
    val meaning: String,
    /** Letters of the form without marks; distractors of a similar length look alike. */
    val letterCount: Int,
    /** Words in the ayah of [representative], which decides whether the ayah fits a question. */
    val ayahWordCount: Int,
)

/** Looks up [WordInfo]; null when the form has no meaning on this phone. */
interface WordInfoSource {
    suspend fun info(rank: Int): WordInfo?
}

/** Picks the type of the next question for an item. */
object QuestionTypeChoice {

    /**
     * Returns a random type from [available]. The type used the last time for this item is left out
     * when another is available, so the same word is not asked the same way twice in a row.
     */
    fun choose(available: List<QuestionType>, last: QuestionType?, random: Random): QuestionType {
        require(available.isNotEmpty()) { "No question type is available." }
        val fresh = available.filter { it != last }
        return (fresh.ifEmpty { available }).random(random)
    }
}
