package app.awn.quran.feature.duas.content

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject

/** Reads `topics.json`, which `tools/topics/build_topics.py` writes and checks. */
object TopicCatalogueParser {

    /** Parses the file's text; a topic in an unknown category fails with the topic's key. */
    fun parse(json: String): TopicCatalogue {
        val root = JSONObject(json)
        val categories =
            root.getJSONArray("categories").objects().map { entry ->
                TopicCategory(key = entry.getString("key"), name = nameOf(entry))
            }
        val categoryKeys = categories.map { it.key }.toSet()
        val topics =
            root.getJSONArray("topics").objects().map { entry ->
                val topic = parseTopic(entry)
                require(topic.categoryKey in categoryKeys) {
                    "Topic ${topic.key} has an unknown category ${topic.categoryKey}."
                }
                topic
            }
        return TopicCatalogue(categories = categories, topics = topics)
    }

    private fun parseTopic(entry: JSONObject): Topic =
        Topic(
            key = entry.getString("key"),
            categoryKey = entry.getString("category"),
            name = nameOf(entry),
            parts = entry.getJSONArray("parts").objects().map(::parsePart),
            duasGroupKey = entry.optString("duas_group").takeIf { it.isNotEmpty() && it != "null" },
        )

    private fun parsePart(entry: JSONObject): TopicPart =
        TopicPart(
            key = entry.getString("key"),
            name = nameOf(entry),
            passages =
                entry.getJSONArray("passages").objects().map { passage ->
                    AyahPassage(
                        surah = passage.getInt("surah"),
                        firstAyah = passage.getInt("first"),
                        lastAyah = passage.getInt("last"),
                    )
                },
        )

    private fun nameOf(entry: JSONObject): LocalName =
        LocalName(english = entry.getString("name_en"), bangla = entry.getString("name_bn"))

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
}

/** Loads the bundled topic list once. */
class TopicCatalogueRepository(private val assets: AssetManager) {
    private val catalogue: TopicCatalogue by lazy {
        TopicCatalogueParser.parse(assets.open(FILE_NAME).bufferedReader().use { it.readText() })
    }

    fun catalogue(): TopicCatalogue = catalogue

    companion object {
        const val FILE_NAME = "topics.json"
    }
}
