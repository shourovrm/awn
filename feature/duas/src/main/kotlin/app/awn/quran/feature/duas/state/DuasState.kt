package app.awn.quran.feature.duas.state

import app.awn.quran.feature.duas.card.DuaCardData
import app.awn.quran.feature.duas.card.DuaWordTile
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.content.DuaCatalogue
import app.awn.quran.feature.duas.content.DuaSearchHit
import app.awn.quran.feature.duas.data.DuaProgress

/**
 * The duas the card screen pages through. [groupKey] is the group they were opened from, null for
 * the ten starters; [duaIds] are in reading order and [startIndex] is the card shown first.
 */
data class DuaDeck(val groupKey: String?, val duaIds: List<String>, val startIndex: Int)

/** Everything the Duas screens draw. */
data class DuasState(
    /** Null until the bundled collection has been read. */
    val catalogue: DuaCatalogue? = null,
    val progress: DuaProgress = DuaProgress(),
    val filter: DuasFilter = DuasFilter.All,
    val query: String = "",
    val searchHits: List<DuaSearchHit> = emptyList(),
    val deck: DuaDeck? = null,
    /** The card texts of every dua, by dua id; empty while they are being read. */
    val cards: Map<String, DuaCardData> = emptyMap(),
    /** Word tiles by dua id, read when a card first shows them. */
    val wordTiles: Map<String, List<DuaWordTile>> = emptyMap(),
) {
    /** The duas of the open deck, in order. */
    val deckDuas: List<Dua>
        get() {
            val all = catalogue?.duas.orEmpty().associateBy { it.id }
            return deck?.duaIds.orEmpty().mapNotNull { all[it] }
        }
}
