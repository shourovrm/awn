package app.awn.quran.core.data.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.Word

/** A word with the line slot it is printed on. */
internal data class PlacedWord(val word: Word, val line: Int)

/** The end sign of an ayah with the line slot it is printed on. */
internal data class PlacedAyahEnd(val ayah: AyahRef, val line: Int)

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
     * there. Some pages (V1 page 77, for example) put a surah with a bismillah on line 2 and the
     * word table leaves only line 1 free, so the banner and the bismillah share that slot.
     *
     * [bismillahPreBySurah] maps a surah number to whether it has a separate bismillah line.
     */
    fun build(
        words: List<PlacedWord>,
        ayahEnds: List<PlacedAyahEnd>,
        bismillahPreBySurah: Map<Int, Boolean>,
    ): List<PageLine> {
        val itemsByLine = sortedMapOf<Int, MutableList<OrderedItem>>()

        for ((word, line) in words) {
            val key = word.key
            itemsByLine
                .getOrPut(line) { mutableListOf() }
                .add(
                    OrderedItem(
                        key.surah,
                        key.ayah,
                        WORD_RANK,
                        key.wordPosition,
                        LineItem.WordItem(word),
                    ),
                )
        }
        for ((ayah, line) in ayahEnds) {
            itemsByLine
                .getOrPut(line) { mutableListOf() }
                .add(OrderedItem(ayah.surah, ayah.ayah, AYAH_END_RANK, 0, LineItem.AyahEnd(ayah)))
        }

        val firstWordsOfSurahs = words.filter {
            it.word.key.ayah == 1 && it.word.key.wordPosition == 1
        }
        for ((word, line) in firstWordsOfSurahs) {
            val surah = word.key.surah
            val hasBismillahLine = bismillahPreBySurah[surah] == true
            val bannerLine = if (hasBismillahLine) maxOf(line - 2, 1) else line - 1
            check(bannerLine >= 1 && bannerLine < line) {
                "Surah $surah starts on line $line, which leaves no room for its banner."
            }
            itemsByLine
                .getOrPut(bannerLine) { mutableListOf() }
                .add(OrderedItem(surah, 1, HEADER_RANK, 0, LineItem.SurahHeader(surah)))
            if (hasBismillahLine) {
                itemsByLine
                    .getOrPut(line - 1) { mutableListOf() }
                    .add(OrderedItem(surah, 1, HEADER_RANK, 1, LineItem.Basmala(surah)))
            }
        }

        val lastLine = itemsByLine.lastKey()
        return (1..lastLine).map { line ->
            val ordered = itemsByLine[line].orEmpty().sortedWith(READING_ORDER)
            PageLine(line, ordered.map { it.item })
        }
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

    private const val HEADER_RANK = 0
    private const val WORD_RANK = 1
    private const val AYAH_END_RANK = 2
}
