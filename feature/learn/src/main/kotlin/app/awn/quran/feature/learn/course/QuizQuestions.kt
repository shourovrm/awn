package app.awn.quran.feature.learn.course

import org.json.JSONArray
import org.json.JSONObject

/**
 * A question of a quiz screen. Arabic in the options and item lists is lesson markup, see
 * [LessonMarkup]. [explanation] is shown once the user has answered.
 */
sealed interface QuizQuestion {
    val explanation: String

    /** Tap the option that is right; [correctIndex] counts from 0. */
    data class Pick(
        val question: String,
        val options: List<String>,
        val correctIndex: Int,
        override val explanation: String,
    ) : QuizQuestion {
        fun isRight(index: Int): Boolean = index == correctIndex
    }

    /** Play [clip], then tap the option that is right. */
    data class Listen(
        val clip: String,
        val options: List<String>,
        val correctIndex: Int,
        override val explanation: String,
    ) : QuizQuestion {
        val clipIds: List<String>
            get() = clip.split(CLIP_SEPARATOR)

        fun isRight(index: Int): Boolean = index == correctIndex
    }

    /** Tap the name of the letter [shown]. */
    data class Name(
        val shown: String,
        val options: List<String>,
        val correctIndex: Int,
        override val explanation: String,
    ) : QuizQuestion {
        fun isRight(index: Int): Boolean = index == correctIndex
    }

    /** Tap the [items] in the order they are listed. */
    data class Order(val question: String, val items: List<String>, override val explanation: String) :
        QuizQuestion {
        fun isRight(tapped: List<String>): Boolean = tapped == items
    }

    /** Put each item in the group it belongs to. */
    data class Sort(val question: String, val groups: List<QuizGroup>, override val explanation: String) :
        QuizQuestion {
        val allItems: List<String>
            get() = groups.flatMap { it.items }

        fun isRightGroup(item: String, groupIndex: Int): Boolean = item in groups[groupIndex].items
    }

    /** Join each left item to the right item it goes with. */
    data class Match(val question: String, val pairs: List<QuizPair>, override val explanation: String) :
        QuizQuestion {
        fun isPair(left: String, right: String): Boolean =
            pairs.any { it.left == left && it.right == right }
    }

    /** Tap every item that fits; [answers] are the items that fit. */
    data class Find(
        val question: String,
        val items: List<String>,
        val answers: List<String>,
        override val explanation: String,
    ) : QuizQuestion {
        fun isAnswer(item: String): Boolean = item in answers

        fun isComplete(found: Set<String>): Boolean = found.containsAll(answers)
    }
}

/** A sort group: its label and the items that belong in it. */
data class QuizGroup(val label: String, val items: List<String>)

/** A match pair: the left item and the text it goes with. */
data class QuizPair(val left: String, val right: String)

/** Reads the `questions` of a quiz screen, as `tools/build_learn_content.py` writes them. */
internal fun parseQuizQuestions(questions: JSONArray): List<QuizQuestion> =
    questions.objects().map(::parseQuizQuestion)

private fun parseQuizQuestion(question: JSONObject): QuizQuestion {
    val explanation = question.getString("explain")
    return when (val type = question.getString("type")) {
        "pick" ->
            QuizQuestion.Pick(
                question = question.getString("question"),
                options = question.getJSONArray("options").strings(),
                correctIndex = question.getInt("answer"),
                explanation = explanation,
            )
        "listen" ->
            QuizQuestion.Listen(
                clip = question.getString("clip"),
                options = question.getJSONArray("options").strings(),
                correctIndex = question.getInt("answer"),
                explanation = explanation,
            )
        "name" ->
            QuizQuestion.Name(
                shown = question.getString("show"),
                options = question.getJSONArray("options").strings(),
                correctIndex = question.getInt("answer"),
                explanation = explanation,
            )
        "order" ->
            QuizQuestion.Order(
                question = question.getString("question"),
                items = question.getJSONArray("items").strings(),
                explanation = explanation,
            )
        "sort" ->
            QuizQuestion.Sort(
                question = question.getString("question"),
                groups = question.getJSONArray("groups").objects().map(::parseQuizGroup),
                explanation = explanation,
            )
        "match" ->
            QuizQuestion.Match(
                question = question.getString("question"),
                pairs = question.getJSONArray("pairs").objects().map(::parseQuizPair),
                explanation = explanation,
            )
        "find" ->
            QuizQuestion.Find(
                question = question.getString("question"),
                items = question.getJSONArray("items").strings(),
                answers = question.getJSONArray("answers").strings(),
                explanation = explanation,
            )
        else -> error("Unknown quiz question type $type in the lesson data.")
    }
}

private fun parseQuizGroup(group: JSONObject) =
    QuizGroup(label = group.getString("label"), items = group.getJSONArray("items").strings())

private fun parseQuizPair(pair: JSONObject) =
    QuizPair(left = pair.getString("left"), right = pair.getString("right"))
