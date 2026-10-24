package app.awn.quran.feature.duas.content

import android.content.res.AssetManager
import app.awn.quran.core.data.WordKey
import org.json.JSONObject

/** Reads `duas.json`. */
object DuaCatalogueParser {
    /** Parses the file's text; an unknown group, opening or prophet key fails with the dua id. */
    fun parse(json: String): DuaCatalogue {
        val root = JSONObject(json)
        val groups = parseGroups(root)
        val prophets = parseProphets(root)
        val duas = parseDuas(root, groups.map { it.key }.toSet(), prophets.keys)
        return DuaCatalogue(groups = groups, duas = duas, prophets = prophets)
    }

    private fun parseGroups(root: JSONObject): List<DuaGroup> {
        val entries = root.getJSONArray("groups")
        return (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            DuaGroup(key = entry.getString("key"), name = parseName(entry))
        }
    }

    private fun parseProphets(root: JSONObject): Map<String, LocalName> {
        val entries = root.getJSONObject("prophets")
        return entries.keys().asSequence().associateWith { key ->
            parseName(entries.getJSONObject(key))
        }
    }

    private fun parseName(entry: JSONObject): LocalName =
        LocalName(english = entry.getString("name_en"), bangla = entry.getString("name_bn"))

    private fun parseDuas(
        root: JSONObject,
        groupKeys: Set<String>,
        prophetKeys: Set<String>,
    ): List<Dua> {
        val entries = root.getJSONArray("duas")
        return (0 until entries.length()).map { index ->
            parseDua(entries.getJSONObject(index), groupKeys, prophetKeys)
        }
    }

    private fun parseDua(
        entry: JSONObject,
        groupKeys: Set<String>,
        prophetKeys: Set<String>,
    ): Dua {
        val id = entry.getString("id")
        val surah = entry.getInt("surah")
        val groupKey = entry.getString("group")
        require(groupKey in groupKeys) { "Dua $id has the unknown group '$groupKey'." }
        val opening = openingFromKey(id, entry.getString("opening"))
        val prophetKey = if (entry.isNull("prophet")) null else entry.getString("prophet")
        require(prophetKey == null || prophetKey in prophetKeys) {
            "Dua $id has the unknown prophet '$prophetKey'."
        }
        return Dua(
            id = id,
            start = parseWordKey(entry.getJSONObject("start"), surah),
            end = parseWordKey(entry.getJSONObject("end"), surah),
            groupKey = groupKey,
            opening = opening,
            prophetKey = prophetKey,
            isStarter = entry.getBoolean("starter"),
        )
    }

    private fun openingFromKey(id: String, key: String): DuaOpening =
        when (key) {
            "rabbana" -> DuaOpening.Rabbana
            "rabbi" -> DuaOpening.Rabbi
            "allahumma" -> DuaOpening.Allahumma
            "other" -> DuaOpening.Other
            else -> throw IllegalArgumentException("Dua $id has the unknown opening '$key'.")
        }

    private fun parseWordKey(entry: JSONObject, surah: Int): WordKey =
        WordKey(surah = surah, ayah = entry.getInt("ayah"), wordPosition = entry.getInt("word"))
}

/** Loads the bundled collection once. */
class DuaCatalogueRepository(private val assets: AssetManager) {
    private val catalogue: DuaCatalogue by lazy {
        DuaCatalogueParser.parse(assets.open(FILE_NAME).bufferedReader().use { it.readText() })
    }

    fun catalogue(): DuaCatalogue = catalogue

    companion object {
        const val FILE_NAME = "duas.json"
    }
}
