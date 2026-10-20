package app.awn.quran.core.data

/**
 * The one key shared by scripts, timings, translations, tracking colours and bookmarks: surah 1 to
 * 114, ayah from 1, word position from 1 within the ayah.
 */
data class WordKey(val surah: Int, val ayah: Int, val wordPosition: Int) : Comparable<WordKey> {

    init {
        require(surah in FIRST_SURAH..LAST_SURAH) { "Surah must be 1..114 but was $surah." }
        require(ayah >= 1) { "Ayah must be 1 or more but was $ayah." }
        require(wordPosition >= 1) { "Word position must be 1 or more but was $wordPosition." }
    }

    override fun compareTo(other: WordKey): Int =
        compareValuesBy(this, other, WordKey::surah, WordKey::ayah, WordKey::wordPosition)

    override fun toString(): String = "$surah:$ayah:$wordPosition"

    companion object {
        const val FIRST_SURAH = 1
        const val LAST_SURAH = 114
        private const val PART_COUNT = 3

        /** Reads the `surah:ayah:word` form that [toString] writes. */
        fun parse(text: String): WordKey {
            val parts = text.split(':')
            require(parts.size == PART_COUNT) { "A word key looks like 2:255:3 but was '$text'." }
            val numbers = parts.map {
                it.toIntOrNull()
                    ?: throw IllegalArgumentException("'$it' in '$text' is not a number.")
            }
            return WordKey(numbers[0], numbers[1], numbers[2])
        }
    }
}
