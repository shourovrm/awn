package app.awn.quran.feature.learn.words

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.search.SearchNormaliser

/** One ayah a word appears in, with the word's places in it; a word can appear twice in an ayah. */
data class WordAyah(val ayah: AyahRef, val wordKeys: List<WordKey>)

/** Groups a word's places (in reading order) by ayah, keeping the reading order. */
fun wordAyahsOf(places: List<WordKey>): List<WordAyah> =
    places.groupBy { AyahRef(it.surah, it.ayah) }.map { (ayah, keys) -> WordAyah(ayah, keys) }

/** What one of a word's ayahs can be found by: its surah's names and its texts. */
data class WordAyahSearchText(
    val ayah: AyahRef,
    val surahNames: List<String>,
    val texts: List<String>,
)

/**
 * Filters a word's ayahs by what the user types. A number alone is a surah ("2" is Al-Baqarah, not
 * every reference with a 2 in it); a number with a colon is the start of a reference ("2:1" finds
 * 2:1, 2:10 and 2:105); anything else is looked for in the surah names and the texts, the way the
 * Quran search compares them (spelling variants, marks and case do not matter).
 */
class WordAyahSearch(private val rows: List<WordAyahSearchText>) {

    private val normalisedTexts: List<List<String>> = rows.map { row ->
        (row.surahNames + row.texts).flatMap(SearchNormaliser::normalisedReadings)
    }

    fun matching(query: String): List<AyahRef> {
        val typed = withLatinDigits(query.trim())
        val matches =
            when {
                typed.isEmpty() -> rows.indices.toList()
                typed.all { it.isDigit() } -> rows.indices.filter { inSurah(it, typed.toInt()) }
                referencePattern.matches(typed) ->
                    rows.indices.filter { startsReference(it, typed) }
                else -> rows.indices.filter { containsText(it, typed) }
            }
        return matches.map { rows[it].ayah }
    }

    private fun inSurah(index: Int, surah: Int): Boolean = rows[index].ayah.surah == surah

    private fun startsReference(index: Int, typed: String): Boolean {
        val ayah = rows[index].ayah
        return "${ayah.surah}:${ayah.ayah}".startsWith(typed)
    }

    private fun containsText(index: Int, typed: String): Boolean {
        val queries = SearchNormaliser.normalisedQueries(typed).filter { it.isNotBlank() }
        return normalisedTexts[index].any { text -> queries.any { text.contains(it) } }
    }

    private companion object {
        val referencePattern = Regex("""\d+:\d*""")

        // Bangla and Arabic-Indic digits count as the same numbers ("২:১০" is "2:10").
        fun withLatinDigits(text: String): String =
            text
                .map { character ->
                    val value = Character.digit(character, DECIMAL)
                    if (value >= 0) '0' + value else character
                }
                .joinToString("")

        const val DECIMAL = 10
    }
}
