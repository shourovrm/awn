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
    val source: SnippetSource,
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
                    source = parseSource(entry.getJSONObject("source")),
                    textEnglish = entry.getString("text_en"),
                    textBangla = entry.getString("text_bn"),
                    url = entry.optStringOrNull("url"),
                    grade = entry.optStringOrNull("grade"),
                )
            }
        }

        private fun parseSource(source: JSONObject): SnippetSource =
            when (val type = source.getString("type")) {
                "quran" -> SnippetSource.Ayah(source.getInt("surah"), source.getInt("ayah"))
                "hadith" ->
                    SnippetSource.Hadith(
                        HadithCollection.fromKey(source.getString("collection")),
                        source.getString("number"),
                    )
                else -> error("Unknown snippet source type '$type' in $ASSET_PATH.")
            }

        /**
         * The position of the day's snippet in a list of [snippetCount]. Consecutive days give
         * consecutive entries, so no snippet repeats before the list is used up.
         */
        fun indexForDay(snippetCount: Int, epochDay: Long): Int =
            Math.floorMod(epochDay, snippetCount.toLong()).toInt()

        /** The snippet for a calendar day, or null when there are none. */
        fun forDay(snippets: List<Snippet>, epochDay: Long): Snippet? =
            if (snippets.isEmpty()) null else snippets[indexForDay(snippets.size, epochDay)]
    }
}

/** Returns null for a JSON null or a missing key, where `optString` would return "null". */
private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else getString(name)
