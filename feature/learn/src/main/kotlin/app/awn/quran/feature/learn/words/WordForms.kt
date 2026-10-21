package app.awn.quran.feature.learn.words

import app.awn.quran.core.data.WordKey
import android.content.res.AssetManager
import java.io.DataInputStream
import java.io.InputStream

/** Where the build puts the word forms file in the APK's assets. */
object WordFormsAsset {
    const val PATH = "learn/word_forms.bin"

    fun read(assets: AssetManager): WordForms = assets.open(PATH).use { WordForms.read(it) }
}

/**
 * The Quran's words grouped by written form and ranked by how often the form occurs: rank 1 is the
 * most frequent. A form is the written word without vowel marks and stop signs, built by
 * `tools/build_learn_words.py` because no base-word data with a usable licence exists. Everything
 * the Learn tab needs about frequency goes through this interface, so base-word data can replace
 * the file without touching the planner or the screens.
 */
interface WordFrequency {

    /** The number of forms; ranks run from 1 to this number. */
    val formCount: Int

    /** The number of words in the Quran, counting every occurrence. */
    val wordCount: Int

    /** How many times the form of [rank] occurs in the Quran. */
    fun occurrenceCount(rank: Int): Int

    /** The rank of the form that [key] belongs to. */
    fun rankOf(key: WordKey): Int

    /** The word that stands for the form of [rank] on screens: where it is shown and drawn. */
    fun representative(rank: Int): WordKey

    /** Every occurrence of the form of [rank], in reading order. */
    fun occurrences(rank: Int): List<WordKey>
}

/**
 * [WordFrequency] read from the build's `word_forms.bin` (format in the script). All words are held
 * in two arrays, so loading takes a few milliseconds and about 0.6 MB.
 */
class WordForms private constructor(
    private val surahAyahCounts: IntArray,
    private val ayahStartIndexes: IntArray,
    private val formRanks: ShortArray,
    private val representativeIndexes: IntArray,
) : WordFrequency {

    // Occurrences grouped by form: the words of form r are indexes
    // occurrenceIndexes[occurrenceStarts[r] until occurrenceStarts[r + 1]], in reading order.
    private val occurrenceStarts: IntArray
    private val occurrenceIndexes: IntArray

    // Index into ayahStartIndexes of the first ayah of each surah.
    private val surahFirstAyahOrdinal: IntArray

    override val formCount: Int = representativeIndexes.size
    override val wordCount: Int = formRanks.size

    init {
        val counts = IntArray(formCount + 2)
        for (storedRank in formRanks) counts[rankOfStored(storedRank) + 1]++
        occurrenceStarts = IntArray(formCount + 2)
        for (rank in 1..formCount) occurrenceStarts[rank + 1] = occurrenceStarts[rank] + counts[rank + 1]
        val cursor = occurrenceStarts.copyOf()
        occurrenceIndexes = IntArray(wordCount)
        for (index in 0 until wordCount) {
            val rank = rankOfStored(formRanks[index])
            occurrenceIndexes[cursor[rank]++] = index
        }
        surahFirstAyahOrdinal = IntArray(surahAyahCounts.size + 1)
        for (surahIndex in surahAyahCounts.indices) {
            surahFirstAyahOrdinal[surahIndex + 1] =
                surahFirstAyahOrdinal[surahIndex] + surahAyahCounts[surahIndex]
        }
    }

    override fun occurrenceCount(rank: Int): Int {
        requireRank(rank)
        return occurrenceStarts[rank + 1] - occurrenceStarts[rank]
    }

    override fun rankOf(key: WordKey): Int = rankOfStored(formRanks[indexOf(key)])

    override fun representative(rank: Int): WordKey {
        requireRank(rank)
        return keyOf(representativeIndexes[rank - 1])
    }

    override fun occurrences(rank: Int): List<WordKey> {
        requireRank(rank)
        return (occurrenceStarts[rank] until occurrenceStarts[rank + 1]).map {
            keyOf(occurrenceIndexes[it])
        }
    }

    private fun requireRank(rank: Int) =
        require(rank in 1..formCount) { "Rank must be 1..$formCount but was $rank." }

    private fun indexOf(key: WordKey): Int {
        val surahIndex = key.surah - 1
        require(surahIndex in surahAyahCounts.indices && key.ayah <= surahAyahCounts[surahIndex]) {
            "There is no ayah ${key.surah}:${key.ayah}."
        }
        val ayahOrdinal = surahFirstAyahOrdinal[surahIndex] + key.ayah - 1
        val index = ayahStartIndexes[ayahOrdinal] + key.wordPosition - 1
        require(index < ayahStartIndexes[ayahOrdinal + 1]) { "There is no word $key." }
        return index
    }

    private fun keyOf(index: Int): WordKey {
        // The last ayah whose first word is at or before the index.
        var low = 0
        var high = ayahStartIndexes.size - 2
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (ayahStartIndexes[middle] <= index) low = middle else high = middle - 1
        }
        val surahIndex = surahOfAyahOrdinal(low)
        val ayah = low - surahFirstAyahOrdinal[surahIndex] + 1
        return WordKey(surahIndex + 1, ayah, index - ayahStartIndexes[low] + 1)
    }

    private fun surahOfAyahOrdinal(ordinal: Int): Int {
        var surahIndex = 0
        while (surahFirstAyahOrdinal[surahIndex + 1] <= ordinal) surahIndex++
        return surahIndex
    }

    companion object {
        private const val MAGIC = "AWLW"
        private const val SUPPORTED_VERSION = 1
        private const val SURAH_COUNT = 114
        private const val UNSIGNED_SHORT_MASK = 0xFFFF

        private fun rankOfStored(stored: Short): Int = stored.toInt() and UNSIGNED_SHORT_MASK

        /** Reads the file; throws with the reason when it is not a word forms file of version 1. */
        fun read(stream: InputStream): WordForms {
            val input = DataInputStream(stream.buffered())
            val magic = ByteArray(MAGIC.length).also { input.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == MAGIC) { "Not a word forms file." }
            val version = input.readUnsignedByte()
            require(version == SUPPORTED_VERSION) { "Unsupported word forms version $version." }
            val wordCount = input.readInt()
            val formCount = input.readUnsignedShort()
            val surahAyahCounts = IntArray(SURAH_COUNT) { input.readUnsignedShort() }
            val ayahTotal = surahAyahCounts.sum()
            val ayahStartIndexes = IntArray(ayahTotal + 1)
            for (ordinal in 0 until ayahTotal) {
                ayahStartIndexes[ordinal + 1] = ayahStartIndexes[ordinal] + input.readUnsignedByte()
            }
            require(ayahStartIndexes[ayahTotal] == wordCount) {
                "The ayah word counts add up to ${ayahStartIndexes[ayahTotal]}, not $wordCount."
            }
            val formRanks = ShortArray(wordCount) { input.readShort() }
            val representativeIndexes = IntArray(formCount) { input.readInt() }
            require(input.read() == -1) { "The word forms file has trailing bytes." }
            return WordForms(surahAyahCounts, ayahStartIndexes, formRanks, representativeIndexes)
        }
    }
}
