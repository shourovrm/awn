package app.awn.quran.feature.learn.content

import android.content.res.AssetManager
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** The two languages of the lesson texts. */
enum class LearnLanguage {
    English,
    Bangla;

    companion object {
        fun of(locale: Locale): LearnLanguage = if (locale.language == "bn") Bangla else English
    }
}

/** Words [wordPositions] of one ayah that carry the rule of the lesson. */
data class LessonExample(
    val surah: Int,
    val ayah: Int,
    val wordPositions: List<Int>,
    val noteEnglish: String,
    val noteBangla: String,
) {
    fun note(language: LearnLanguage): String =
        if (language == LearnLanguage.Bangla) noteBangla else noteEnglish
}

/**
 * One tajweed lesson. Paragraphs of [bodyEnglish] and [bodyBangla] are separated by a blank line. A
 * lesson is a draft until a named reviewer has checked it.
 */
data class Lesson(
    val id: String,
    val titleEnglish: String,
    val titleBangla: String,
    val bodyEnglish: String,
    val bodyBangla: String,
    val examples: List<LessonExample>,
    val reviewedBy: String?,
    val reviewedOn: String?,
) {
    val isDraft: Boolean
        get() = reviewedBy == null

    fun title(language: LearnLanguage): String =
        if (language == LearnLanguage.Bangla) titleBangla else titleEnglish

    fun paragraphs(language: LearnLanguage): List<String> {
        val body = if (language == LearnLanguage.Bangla) bodyBangla else bodyEnglish
        return body.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** Reads the lessons in `assets/lessons/<id>.json`. */
class LessonRepository(private val assets: AssetManager) {

    /** All lessons in teaching order. Throws when an asset is missing or malformed. */
    fun all(): List<Lesson> = LESSON_IDS.map(::load)

    fun load(id: String): Lesson {
        val text =
            assets.open("lessons/$id.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(text)
    }

    companion object {
        /** The order of the guide: where the mouth makes each letter first, then the rules. */
        val LESSON_IDS =
            listOf(
                "makharij",
                "sifat",
                "noon-sakinah-tanween",
                "meem-sakinah",
                "ghunnah",
                "qalqalah",
                "madd-natural",
                "madd-extended",
                "raa",
                "lam-of-allah",
                "waqf-signs",
                "hamzat-al-wasl",
            )

        fun parse(json: String): Lesson {
            val root = JSONObject(json)
            return Lesson(
                id = root.getString("id"),
                titleEnglish = root.getString("title_en"),
                titleBangla = root.getString("title_bn"),
                bodyEnglish = root.getString("body_en"),
                bodyBangla = root.getString("body_bn"),
                examples = parseExamples(root.getJSONArray("examples")),
                reviewedBy = root.optStringOrNull("reviewed_by"),
                reviewedOn = root.optStringOrNull("reviewed_on"),
            )
        }

        private fun parseExamples(array: JSONArray): List<LessonExample> =
            (0 until array.length()).map { index ->
                val example = array.getJSONObject(index)
                val positions = example.getJSONArray("word_positions")
                LessonExample(
                    surah = example.getInt("surah"),
                    ayah = example.getInt("ayah"),
                    wordPositions = (0 until positions.length()).map { positions.getInt(it) },
                    noteEnglish = example.getString("note_en"),
                    noteBangla = example.getString("note_bn"),
                )
            }
    }
}

/** Returns null for a JSON null or a missing key, where `optString` would return "null". */
internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else getString(name)
