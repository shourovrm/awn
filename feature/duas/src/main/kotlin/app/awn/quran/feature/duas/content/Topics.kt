package app.awn.quran.feature.duas.content

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey

/** A run of whole ayahs inside one surah: one card of a topic. */
data class AyahPassage(val surah: Int, val firstAyah: Int, val lastAyah: Int) {
    init {
        require(firstAyah in 1..lastAyah) { "Passage $surah:$firstAyah-$lastAyah is out of order." }
    }

    val first: AyahRef
        get() = AyahRef(surah, firstAyah)
}

/** One part of a topic, such as "Be good to your parents", with its passages in order. */
data class TopicPart(val key: String, val name: LocalName, val passages: List<AyahPassage>)

/** A wider group of topics, such as Family. */
data class TopicCategory(val key: String, val name: LocalName)

/** One topic, such as Parents; [duasGroupKey] is the matching group in Duas, if there is one. */
data class Topic(
    val key: String,
    val categoryKey: String,
    val name: LocalName,
    val parts: List<TopicPart>,
    val duasGroupKey: String?,
) {
    val passageCount: Int
        get() = parts.sumOf { it.passages.size }
}

/** The whole Ayahs by topic list as read from the bundled `topics.json`. */
data class TopicCatalogue(val categories: List<TopicCategory>, val topics: List<Topic>) {

    /**
     * The topics as a card collection, so they share the dua cards: each part is a group (named
     * "Topic · Part", which search also matches) and each passage a card of whole ayahs.
     */
    fun asCardCatalogue(): DuaCatalogue {
        val groups = mutableListOf<DuaGroup>()
        val cards = mutableListOf<Dua>()
        topics.forEach { topic ->
            topic.parts.forEach { part ->
                val groupKey = partGroupKey(topic, part)
                groups +=
                    DuaGroup(
                        key = groupKey,
                        name =
                            LocalName(
                                english = "${topic.name.english} · ${part.name.english}",
                                bangla = "${topic.name.bangla} · ${part.name.bangla}",
                            ),
                    )
                part.passages.forEach { passage -> cards += passage.asCard(groupKey) }
            }
        }
        return DuaCatalogue(groups = groups, duas = cards, prophets = emptyMap())
    }

    companion object {
        /**
         * The card group of [part] of [topic]; part keys repeat across topics, so both are in it.
         */
        fun partGroupKey(topic: Topic, part: TopicPart): String = "${topic.key}/${part.key}"

        /**
         * A word position past the last word of any ayah: a card that ends here covers its last
         * ayah to the end, so every word of every ayah of the passage is on the card.
         */
        const val WHOLE_AYAH_END = 9_999
    }
}

private fun AyahPassage.asCard(groupKey: String): Dua =
    Dua(
        id = "topic:$groupKey:$surah:$firstAyah",
        start = WordKey(surah, firstAyah, 1),
        end = WordKey(surah, lastAyah, TopicCatalogue.WHOLE_AYAH_END),
        groupKey = groupKey,
        opening = DuaOpening.Other,
        prophetKey = null,
        isStarter = false,
    )
