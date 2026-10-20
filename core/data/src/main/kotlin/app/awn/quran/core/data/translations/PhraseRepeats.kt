package app.awn.quran.core.data.translations

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey

/**
 * The English word-by-word source (Quran.com API v4) gives a multi-word phrase to every word of the
 * phrase: 9:3 words 2 and 3 ("min", "Allah") both come with "from Allah", and 1,225 such repeats
 * sit in surahs 9 to 58. Shown under each word the phrase reads as a duplicate. [collapse] keeps
 * the phrase under its first word and leaves the following words empty, so the reader shows it
 * once.
 *
 * The one genuine repeat in the pack is 4:24 words 15 and 16 (two different words that both mean
 * "that"); it is kept as it is. Found by comparing adjacent rows of `wbw_en.sqlite` (see data.md).
 */
internal object PhraseRepeats {

    private val genuineRepeats = setOf(WordKey(4, 24, 16))

    /** Returns [meanings] (one per word position, from 1) with repeated phrase parts emptied. */
    fun collapse(ayah: AyahRef, meanings: List<String>): List<String> =
        meanings.mapIndexed { index, meaning ->
            val isRepeat = index > 0 && meaning == meanings[index - 1]
            val key = WordKey(ayah.surah, ayah.ayah, index + 1)
            if (isRepeat && key !in genuineRepeats) "" else meaning
        }
}
