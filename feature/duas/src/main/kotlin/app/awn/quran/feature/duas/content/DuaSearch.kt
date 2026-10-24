package app.awn.quran.feature.duas.content

/** The texts one dua can be found by. [translation] is null when no translation is installed. */
data class DuaSearchEntry(
    val dua: Dua,
    val arabic: String,
    val translation: String?,
    val groupName: String,
)

/** Which text matched, so the result row can show that text. */
enum class DuaMatchField {
    Arabic,
    Translation,
    Group,
}

data class DuaSearchHit(val dua: Dua, val field: DuaMatchField)

/** Finds duas by Arabic without harakat, translation words, or group name; the content slice writes it. */
object DuaSearch {
    fun search(query: String, entries: List<DuaSearchEntry>): List<DuaSearchHit> = TODO("content slice")
}
