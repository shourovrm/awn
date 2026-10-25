package app.awn.quran.tracker

/**
 * The loaded words as numbers. Every character of a reference phoneme and of a heard token gets one
 * symbol number, so heard and expected phonemes compare as integers. A run of one repeated
 * character is merged into one inside each word: the model card says madd lengths are unreliable,
 * and a long vowel is written as a run of one character.
 */
internal class ReferenceTables {

    private val symbolIds = mutableMapOf<String, Int>()
    private val symbolsById = mutableListOf<String>()

    var words: List<ReferenceWord> = emptyList()
        private set

    /** The merged phonemes of every word, one after the other. */
    var expectedIds = IntArray(0)
        private set

    /** Where each word starts in [expectedIds]; one more entry holds the end of the last word. */
    var wordStartOffsets = IntArray(1)
        private set

    /** The merged length of each word. */
    var wordLengths = IntArray(0)
        private set

    // Counts ayahs from 1 in the order they were loaded, so ayahs of two surahs stay apart.
    private var ayahNumberOfWord = IntArray(0)

    // For every word, the position in ReferenceWord.phonemes of each merged phoneme.
    private var originalPositions = emptyList<IntArray>()

    val size: Int
        get() = words.size

    /** Forgets the symbol numbers too; only [AlignmentTracker.load] may, as heard ones use them. */
    fun clearSymbols() {
        symbolIds.clear()
        symbolsById.clear()
    }

    fun idOf(symbol: String): Int =
        symbolIds.getOrPut(symbol) {
            symbolsById += symbol
            symbolsById.size - 1
        }

    fun symbolOf(id: Int): String = symbolsById[id]

    /** Builds the tables for [newWords], keeping the symbol numbers already given out. */
    fun rebuild(newWords: List<ReferenceWord>) {
        words = newWords
        val positions = mutableListOf<IntArray>()
        val flattened = mutableListOf<Int>()
        wordStartOffsets = IntArray(newWords.size + 1)
        wordLengths = IntArray(newWords.size)
        ayahNumberOfWord = IntArray(newWords.size)
        var ayahSequence = 0
        for ((wordIndex, word) in newWords.withIndex()) {
            val previousKey = newWords.getOrNull(wordIndex - 1)?.key
            val startsNewAyah =
                previousKey == null ||
                    word.key.surah != previousKey.surah ||
                    word.key.ayah != previousKey.ayah
            if (startsNewAyah) ayahSequence++
            ayahNumberOfWord[wordIndex] = ayahSequence
            wordStartOffsets[wordIndex] = flattened.size
            val ids = word.phonemes.map { idOf(it) }
            val runStarts = ids.indices.filter { it == 0 || ids[it - 1] != ids[it] }
            positions += runStarts.toIntArray()
            flattened += runStarts.map { ids[it] }
            wordLengths[wordIndex] = runStarts.size
        }
        originalPositions = positions
        wordStartOffsets[newWords.size] = flattened.size
        expectedIds = flattened.toIntArray()
    }

    /** The merged phonemes of the words [firstWord] to [lastWord], both included. */
    fun idsOfWords(firstWord: Int, lastWord: Int): IntArray =
        expectedIds.copyOfRange(wordStartOffsets[firstWord], wordStartOffsets[lastWord + 1])

    /** The word that holds position [offset] of [expectedIds]. */
    fun wordAtOffset(offset: Int): Int {
        var low = 0
        var high = words.size - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (wordStartOffsets[middle] <= offset) low = middle else high = middle - 1
        }
        return low
    }

    /** The position in [ReferenceWord.phonemes] of merged phoneme [mergedPosition] of [word]. */
    fun originalPosition(word: Int, mergedPosition: Int): Int =
        originalPositions[word][mergedPosition]

    /** The merged position of original phoneme [phonemeIndex] of [word], or -1. */
    fun mergedPosition(word: Int, phonemeIndex: Int): Int =
        originalPositions[word].indexOf(phonemeIndex)

    /** The loaded order of the ayah of [word]: 1 for the first ayah loaded. */
    fun ayahSequenceOf(word: Int): Int = ayahNumberOfWord[word]
}
