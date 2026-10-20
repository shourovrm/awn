package app.awn.quran.feature.learn.content

import android.content.res.AssetManager
import org.json.JSONObject

/**
 * One line shown on the Learn screen: an ayah, or a hadith graded sahih in Sahih al-Bukhari or
 * Sahih Muslim. [url] and [grade] are set for hadith only.
 */
data class Snippet(
    val id: String,
    val arabic: String,
    val reference: String,
    val textEnglish: String,
    val textBangla: String,
    val url: String?,
    val grade: String?,
) {
    fun rendering(language: LearnLanguage): String =
        if (language == LearnLanguage.Bangla) textBangla else textEnglish
}

/** Reads `assets/snippets.json` and picks one snippet per calendar day. */
class SnippetRepository(private val assets: AssetManager) {

    fun all(): List<Snippet> {
        val text = assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(text)
    }

    companion object {
        const val ASSET_PATH = "snippets.json"

        fun parse(json: String): List<Snippet> {
            val entries = JSONObject(json).getJSONArray("snippets")
            return (0 until entries.length()).map { index ->
                val entry = entries.getJSONObject(index)
                Snippet(
                    id = entry.getString("id"),
                    arabic = entry.getString("arabic"),
                    reference = entry.getString("reference"),
                    textEnglish = entry.getString("text_en"),
                    textBangla = entry.getString("text_bn"),
                    url = entry.optStringOrNull("url"),
                    grade = entry.optStringOrNull("grade"),
                )
            }
        }

        /**
         * The snippet for a calendar day. Consecutive days give consecutive entries, so no snippet
         * repeats before the list is used up.
         */
        fun forDay(snippets: List<Snippet>, epochDay: Long): Snippet? =
            if (snippets.isEmpty()) null
            else snippets[Math.floorMod(epochDay, snippets.size.toLong()).toInt()]
    }
}
