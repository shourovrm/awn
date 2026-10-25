package app.awn.quran.tracker

/**
 * The heard phonemes no word has taken yet, as symbol numbers with their token onsets. Phonemes are
 * numbered from the start of the run ("absolute" indexes); the onset of every phoneme ever heard
 * stays available by that number, because a held vowel is measured after its word has taken it.
 */
internal class HeardBuffer {

    private var ids = mutableListOf<Int>()
    private val allOnsets = mutableListOf<Float>()
    private var droppedCount = 0
    private var lastId = NO_SYMBOL

    /**
     * How many phonemes at the front were left by the last said word. A word that ends in a merged
     * sound may share it with the next word (wasl, idgham), so its last phoneme stays for one step.
     */
    var carriedCount = 0
        private set

    val size: Int
        get() = ids.size

    /** Heard phonemes that no word has explained, leaving out the one carried over. */
    val unexplainedCount: Int
        get() = size - carriedCount

    /**
     * Adds a heard phoneme unless it repeats the one before (a long vowel arrives as a run of one
     * character, merged like the reference). Returns true when it was added.
     */
    fun add(id: Int, onset: Float): Boolean {
        if (id == lastId) return false
        lastId = id
        ids += id
        allOnsets += onset
        return true
    }

    fun toIntArray(): IntArray = ids.toIntArray()

    fun absoluteIndex(index: Int): Int = droppedCount + index

    /** The onset of the phoneme at [absoluteIndex], or null when it has not been heard yet. */
    fun onsetAt(absoluteIndex: Int): Float? = allOnsets.getOrNull(absoluteIndex)

    /** The onset of the latest heard phoneme, or null when none was heard. */
    fun lastOnset(): Float? = allOnsets.lastOrNull()

    /** Drops the first [count] phonemes; nothing is carried over. */
    fun drop(count: Int) {
        val dropped = count.coerceIn(0, ids.size)
        ids = ids.subList(dropped, ids.size).toMutableList()
        droppedCount += dropped
        carriedCount = 0
    }

    /** Drops everything before [endExclusive] except its last phoneme, which is carried over. */
    fun consumeUntil(endExclusive: Int) {
        if (endExclusive <= 0) return
        drop(endExclusive - 1)
        carriedCount = 1
    }

    fun clear() {
        ids = mutableListOf()
        allOnsets.clear()
        droppedCount = 0
        carriedCount = 0
        lastId = NO_SYMBOL
    }

    private companion object {
        const val NO_SYMBOL = -1
    }
}
