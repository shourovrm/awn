package app.awn.quran.core.data.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.Word

/** A word with the line slot it is printed on. */
internal data class PlacedWord(val word: Word, val line: Int)

/** The end sign of an ayah with the line slot it is printed on. */
internal data class PlacedAyahEnd(val ayah: AyahRef, val line: Int, val glyphCode: String? = null)

/** Turns the rows of one page into numbered lines. */
internal object PageLayoutBuilder {

    /**
     * Returns one [PageLine] for every slot from 1 to the last used slot, including slots that hold
     * only a surah header or a bismillah.
     *
     * The word table has no rows for header and bismillah slots, so they are placed from the rule
     * the mushaf follows: a surah whose first word is on line L has its bismillah on line L - 1
     * when the chapter has `bismillah_pre`, and its name banner on the slot above that. Surah 1 has
     * no `bismillah_pre` because its bismillah is its first ayah, so only the banner is added
     * there.
     *
     * When the slots above the first word run off the top of the page, they are on the bottom of
     * the previous page. A surah starting on line 2 has its bismillah on line 1 and its banner on
     * line 15 of the previous page; one starting on line 1 has banner and bismillah on lines 14 and
     * 15 of the previous page. [nextPageStarts] holds the first words of the surahs that start on
     * line 1 or 2 of the next page, so this page can draw the banner that belongs to it.
     *
     * [bismillahPreBySurah] maps a surah number to whether it has a separate bismillah line.
     */
    fun build(
        words: List<PlacedWord>,
        ayahEnds: List<PlacedAyahEnd>,
        bismillahPreBySurah: Map<Int, Boolean>,
        nextPageStarts: List<PlacedWord> = emptyList(),
    ): List<PageLine> {
        val itemsByLine = sortedMapOf<Int, MutableList<OrderedItem>>()
        addWordsAndEnds(itemsByLine, words, ayahEnds)

        val firstWordsOfSurahs = words.filter {
            it.word.key.ayah == 1 && it.word.key.wordPosition == 1
        }
        for ((word, line) in firstWordsOfSurahs) {
            for ((slot, item) in headerSlots(word, line, bismillahPreBySurah)) {
                // A slot above the top of the page is on the previous page.
                if (slot >= 1) addItem(itemsByLine, slot, item)
            }
        }
        for ((word, line) in nextPageStarts) {
            for ((slot, item) in headerSlots(word, line, bismillahPreBySurah)) {
                // Slot 0 of the next page is this page's last line, slot -1 the one before.
                if (slot <= 0) addItem(itemsByLine, LAST_SLOT + slot, item)
            }
        }

        val lastLine = itemsByLine.lastKey()
        return (1..lastLine).map { line ->
            val ordered = itemsByLine[line].orEmpty().sortedWith(READING_ORDER)
            PageLine(line, ordered.map { it.item })
        }
    }

    private fun addWordsAndEnds(
        itemsByLine: MutableMap<Int, MutableList<OrderedItem>>,
        words: List<PlacedWord>,
        ayahEnds: List<PlacedAyahEnd>,
    ) {
        for ((word, line) in words) {
            val key = word.key
            val item =
                OrderedItem(
                    key.surah,
                    key.ayah,
                    WORD_RANK,
                    key.wordPosition,
                    LineItem.WordItem(word),
                )
            addItem(itemsByLine, line, item)
        }
        for ((ayah, line, glyphCode) in ayahEnds) {
            val item =
                OrderedItem(
                    ayah.surah,
                    ayah.ayah,
                    AYAH_END_RANK,
                    0,
                    LineItem.AyahEnd(ayah, glyphCode),
                )
            addItem(itemsByLine, line, item)
        }
    }

    /**
     * The banner, and the bismillah when the chapter has one, of the surah whose first word is on
     * [firstWordLine]: the bismillah is on the line above the word and the banner above that. The
     * slot numbers may be 0 or less when they run off the top of the page.
     */
    private fun headerSlots(
        firstWord: Word,
        firstWordLine: Int,
        bismillahPreBySurah: Map<Int, Boolean>,
    ): List<Pair<Int, OrderedItem>> {
        val surah = firstWord.key.surah
        val bannerItem = OrderedItem(surah, 1, HEADER_RANK, 0, LineItem.SurahHeader(surah))
        if (bismillahPreBySurah[surah] != true) return listOf(firstWordLine - 1 to bannerItem)
        val bismillahItem = OrderedItem(surah, 1, HEADER_RANK, 1, LineItem.Basmala(surah))
        return listOf(firstWordLine - 2 to bannerItem, firstWordLine - 1 to bismillahItem)
    }

    private fun addItem(
        itemsByLine: MutableMap<Int, MutableList<OrderedItem>>,
        line: Int,
        item: OrderedItem,
    ) {
        itemsByLine.getOrPut(line) { mutableListOf() }.add(item)
    }

    /**
     * An end sign belongs after the last word of its ayah and before any word of the next ayah, so
     * items sort by ayah first and by kind second.
     */
    private data class OrderedItem(
        val surah: Int,
        val ayah: Int,
        val rank: Int,
        val position: Int,
        val item: LineItem,
    )

    private val READING_ORDER =
        compareBy<OrderedItem>({ it.surah }, { it.ayah }, { it.rank }, { it.position })

    private const val LAST_SLOT = 15
    private const val HEADER_RANK = 0
    private const val WORD_RANK = 1
    private const val AYAH_END_RANK = 2
}
