package app.awn.quran.feature.learn.course

import android.content.res.AssetManager
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import org.json.JSONArray
import org.json.JSONObject

/** The three courses; [folder] is the folder name in `content/lessons/<language>/`. */
enum class LessonCourse(val folder: String) {
    Basics("basics"),
    Grammar("grammar"),
    Letters("letters");

    companion object {
        fun ofFolder(folder: String): LessonCourse = entries.first { it.folder == folder }
    }
}

/** A piece of a lesson screen. Text is lesson markup: see [LessonMarkup]. */
sealed interface LessonBlock {
    data class Paragraph(val text: String) : LessonBlock

    data class Bullets(val items: List<String>) : LessonBlock

    data class Table(val header: List<String>, val rows: List<List<String>>) : LessonBlock

    /** An ayah drawn with its translation. */
    data class Ayah(val ayah: AyahRef) : LessonBlock

    /** Parts that make a word: `A + B = C`, each with a gloss. */
    data class Equation(val parts: List<Term>, val result: Term) : LessonBlock

    data class Term(val text: String, val gloss: String)
}

/** The question that ends a lesson; [correctIndex] counts from 0. */
data class LessonCheck(
    val question: String,
    val ayah: AyahRef?,
    val highlight: WordKey?,
    val options: List<String>,
    val correctIndex: Int,
)

enum class ScreenKind {
    Reading,
    Check,
    Answer,
}

data class LessonScreenData(
    val kind: ScreenKind,
    val title: String,
    val blocks: List<LessonBlock>,
    val check: LessonCheck?,
)

/** One language of a lesson. */
data class LessonText(val title: String, val screens: List<LessonScreenData>) {
    val check: LessonCheck?
        get() = screens.firstNotNullOfOrNull { it.check }

    val answerBlocks: List<LessonBlock>
        get() = screens.firstOrNull { it.kind == ScreenKind.Answer }?.blocks.orEmpty()
}

/**
 * One lesson of the path. [introducedBy] lists the words whose base form brings a grammar lesson
 * up; it is empty for the other courses. [reviewed] is false until a named reviewer has checked it.
 */
data class CourseLesson(
    val id: String,
    val course: LessonCourse,
    val order: Int,
    val introducedBy: List<WordKey>,
    val licence: String,
    val reviewed: Boolean,
    private val languages: Map<String, LessonText>,
) {
    /** The text in [languageCode], or in English when the lesson has not been translated. */
    fun text(languageCode: String): LessonText =
        languages[languageCode] ?: checkNotNull(languages[ENGLISH]) { "$id has no English text." }

    /** True when the lesson ends with a question, which makes it a grammar point to revise. */
    val hasCheck: Boolean
        get() = text(ENGLISH).check != null

    companion object {
        const val ENGLISH = "en"
    }
}

/** A work the lessons follow, for the Sources screen. */
data class LessonReference(val label: String, val url: String)

data class CourseContent(val lessons: List<CourseLesson>, val references: List<LessonReference>) {
    fun lesson(id: String): CourseLesson? = lessons.firstOrNull { it.id == id }

    fun inCourse(course: LessonCourse): List<CourseLesson> =
        lessons.filter { it.course == course }.sortedBy { it.order }
}

/** Reads `assets/learn/lessons.json`, which `tools/build_learn_content.py` writes at build time. */
// One small parser per lesson part.
@Suppress("TooManyFunctions")
object CourseContentReader {
    const val ASSET_PATH = "learn/lessons.json"
    private const val SUPPORTED_FORMAT = 1

    fun read(assets: AssetManager): CourseContent =
        parse(assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() })

    fun parse(json: String): CourseContent {
        val root = JSONObject(json)
        val format = root.getInt("formatVersion")
        require(format == SUPPORTED_FORMAT) { "Unsupported lesson data format $format." }
        return CourseContent(
            lessons = root.getJSONArray("lessons").objects().map(::parseLesson),
            references =
                root.getJSONArray("references").objects().map {
                    LessonReference(it.getString("label"), it.getString("url"))
                },
        )
    }

    private fun parseLesson(lesson: JSONObject): CourseLesson {
        val languages = lesson.getJSONObject("languages")
        return CourseLesson(
            id = lesson.getString("id"),
            course = LessonCourse.ofFolder(lesson.getString("course")),
            order = lesson.getInt("order"),
            introducedBy = lesson.getJSONArray("introducedBy").strings().map(WordKey::parse),
            licence = lesson.getString("licence"),
            reviewed = lesson.getBoolean("reviewed"),
            languages =
                languages.keys().asSequence().associateWith { code ->
                    parseText(languages.getJSONObject(code))
                },
        )
    }

    private fun parseText(text: JSONObject) =
        LessonText(
            title = text.getString("title"),
            screens = text.getJSONArray("screens").objects().map(::parseScreen),
        )

    private fun parseScreen(screen: JSONObject): LessonScreenData {
        val kind =
            when (val stored = screen.getString("kind")) {
                "screen" -> ScreenKind.Reading
                "check" -> ScreenKind.Check
                "answer" -> ScreenKind.Answer
                else -> error("Unknown lesson screen kind $stored.")
            }
        return LessonScreenData(
            kind = kind,
            title = screen.getString("title"),
            blocks = screen.optJSONArray("blocks")?.objects()?.map(::parseBlock).orEmpty(),
            check = screen.optJSONObject("check")?.let(::parseCheck),
        )
    }

    private fun parseBlock(block: JSONObject): LessonBlock =
        when (val type = block.getString("type")) {
            "paragraph" -> LessonBlock.Paragraph(block.getString("text"))
            "bullets" -> LessonBlock.Bullets(block.getJSONArray("items").strings())
            "table" ->
                LessonBlock.Table(
                    header = block.getJSONArray("header").strings(),
                    rows = block.getJSONArray("rows").arrays().map { it.strings() },
                )
            "ayah" -> LessonBlock.Ayah(parseAyah(block.getString("key")))
            "equation" ->
                LessonBlock.Equation(
                    parts = block.getJSONArray("parts").objects().map(::parseTerm),
                    result = parseTerm(block.getJSONObject("result")),
                )
            else -> error("Unknown lesson block type $type.")
        }

    private fun parseTerm(term: JSONObject) =
        LessonBlock.Term(term.getString("text"), term.getString("gloss"))

    private fun parseCheck(check: JSONObject) =
        LessonCheck(
            question = check.getString("question"),
            ayah = check.optString("ayah").takeIf { it.isNotEmpty() }?.let(::parseAyah),
            highlight =
                check.optString("highlight").takeIf { it.isNotEmpty() }?.let(WordKey::parse),
            options = check.getJSONArray("options").strings(),
            correctIndex = check.getInt("answer"),
        )

    private fun parseAyah(key: String): AyahRef {
        val parts = key.split(':')
        require(parts.size == 2) { "An ayah key looks like 2:255 but was '$key'." }
        return AyahRef(parts[0].toInt(), parts[1].toInt())
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.arrays(): List<JSONArray> = (0 until length()).map { getJSONArray(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
