package app.awn.quran.feature.learn.course

import android.content.res.AssetManager
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import org.json.JSONArray
import org.json.JSONObject

/** The four courses; [folder] is the folder name in `content/lessons/<language>/`. */
enum class LessonCourse(val folder: String) {
    Basics("basics"),
    Grammar("grammar"),
    Letters("letters"),
    Tajweed("tajweed");

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

    /** A grid of letters, each with its name: the alphabet page of the first letters lesson. */
    data class Letters(val items: List<LetterName>) : LessonBlock

    data class LetterName(val letter: String, val name: String)
}

/**
 * A question of a lesson; [correctIndex] counts from 0. The last three fields belong to practice
 * items: [clip] is the sound id played with the question, [explanation] the line shown after the
 * answer.
 */
data class LessonCheck(
    val question: String,
    val ayah: AyahRef?,
    val highlight: WordKey?,
    val options: List<String>,
    val correctIndex: Int,
    val clip: String? = null,
    val explanation: String? = null,
)

/**
 * One cell of a grid: the Arabic as written, its transliteration in the lesson's language, and the
 * id of the sound that a tap plays (for example `qaida-04/012`).
 */
data class GridCell(val arabic: String, val transliteration: String, val clip: String)

enum class ScreenKind {
    Reading,
    Check,
    Answer,
    Grid,
    Practice,
}

data class LessonScreenData(
    val kind: ScreenKind,
    val title: String,
    val blocks: List<LessonBlock>,
    val check: LessonCheck?,
    val cells: List<GridCell> = emptyList(),
)

/** One language of a lesson. */
data class LessonText(val title: String, val screens: List<LessonScreenData>) {
    val check: LessonCheck?
        get() = screens.firstOrNull { it.kind == ScreenKind.Check }?.check

    val answerBlocks: List<LessonBlock>
        get() = screens.firstOrNull { it.kind == ScreenKind.Answer }?.blocks.orEmpty()

    val readingScreens: List<LessonScreenData>
        get() = screens.filter { it.kind == ScreenKind.Reading }

    /** The pages that teach, reading screens and grids, in the order of the file. */
    val teachingScreens: List<LessonScreenData>
        get() = screens.filter { it.kind == ScreenKind.Reading || it.kind == ScreenKind.Grid }

    /** The questions of the unit's practice pool; each practice screen is one item. */
    val practiceItems: List<LessonCheck>
        get() = screens.filter { it.kind == ScreenKind.Practice }.mapNotNull { it.check }

    /** True when any grid cell or practice item has a sound, so the unit offers the download. */
    val hasClips: Boolean
        get() =
            screens.any { screen -> screen.cells.isNotEmpty() } ||
                practiceItems.any { it.clip != null }

    /** Every check of the lesson with the explanation shown after it, in order. */
    val checks: List<CheckWithAnswer>
        get() = screens.mapIndexedNotNull { index, screen ->
            if (screen.kind != ScreenKind.Check) return@mapIndexedNotNull null
            val check = screen.check ?: return@mapIndexedNotNull null
            val answer = screens.getOrNull(index + 1)?.takeIf { it.kind == ScreenKind.Answer }
            CheckWithAnswer(screen.title, check, answer?.title, answer?.blocks.orEmpty())
        }
}

/** A check and the explanation that follows it. */
data class CheckWithAnswer(
    val title: String,
    val check: LessonCheck,
    val answerTitle: String?,
    val answerBlocks: List<LessonBlock>,
)

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
                "grid" -> ScreenKind.Grid
                "practice" -> ScreenKind.Practice
                else -> error("Unknown lesson screen kind $stored.")
            }
        return LessonScreenData(
            kind = kind,
            title = screen.getString("title"),
            blocks = screen.optJSONArray("blocks")?.objects()?.map(::parseBlock).orEmpty(),
            check = screen.optJSONObject("check")?.let(::parseCheck),
            cells = screen.optJSONArray("cells")?.objects()?.map(::parseCell).orEmpty(),
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
            "letters" ->
                LessonBlock.Letters(
                    block.getJSONArray("items").objects().map {
                        LessonBlock.LetterName(it.getString("letter"), it.getString("name"))
                    },
                )
            else -> error("Unknown lesson block type $type.")
        }

    private fun parseCell(cell: JSONObject) =
        GridCell(
            arabic = cell.getString("arabic"),
            transliteration = cell.getString("transliteration"),
            clip = cell.getString("clip"),
        )

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
            clip = check.optString("clip").takeIf { it.isNotEmpty() },
            explanation = check.optString("explain").takeIf { it.isNotEmpty() },
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
