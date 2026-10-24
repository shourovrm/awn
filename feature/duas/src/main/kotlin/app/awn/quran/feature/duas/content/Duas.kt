package app.awn.quran.feature.duas.content

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey

/** A name written for each app language. */
data class LocalName(val english: String, val bangla: String)

/** How the dua opens; the Rabbana and Rabbi filters on the Duas screen read this. */
enum class DuaOpening {
    /** "Our Lord", رَبَّنَا. */
    Rabbana,

    /** "My Lord", رَبِّ. */
    Rabbi,

    /** "O Allah", ٱللَّهُمَّ. */
    Allahumma,

    /** Any other opening, such as a dua that starts with the request itself. */
    Other,
}

/** A themed group of duas, such as forgiveness or parents. */
data class DuaGroup(val key: String, val name: LocalName)

/**
 * One dua: the words from [start] to [end] inclusive, in one surah. A dua can begin inside an
 * ayah (2:201 starts at word 4, after "and among them is he who says") and can run over several
 * ayahs (20:25 to 20:28). Word positions are the 1-based positions of the bundled Quran text.
 */
data class Dua(
    val id: String,
    val start: WordKey,
    val end: WordKey,
    val groupKey: String,
    val opening: DuaOpening,
    /** The key of the prophet who makes this dua in the Quran, or null. */
    val prophetKey: String?,
    /** True for the ten short duas offered to a new user first. */
    val isStarter: Boolean,
) {
    init {
        require(start.surah == end.surah) { "Dua $id spans two surahs." }
        require(start.ayah < end.ayah || (start.ayah == end.ayah && start.wordPosition <= end.wordPosition)) {
            "Dua $id ends before it starts."
        }
    }

    /** Every ayah the dua touches, in order; a card shows all of them. */
    val ayahs: List<AyahRef>
        get() = (start.ayah..end.ayah).map { ayah -> AyahRef(start.surah, ayah) }
}

/** The whole duas collection as read from the bundled `duas.json`. */
data class DuaCatalogue(
    val groups: List<DuaGroup>,
    val duas: List<Dua>,
    val prophets: Map<String, LocalName>,
) {
    fun duasIn(groupKey: String): List<Dua> = duas.filter { it.groupKey == groupKey }

    val starters: List<Dua>
        get() = duas.filter { it.isStarter }
}
