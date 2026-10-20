package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository

/** Turns "from ayah A to ayah B" into the list of ayahs to play, using the verse counts. */
class PlaylistBuilder(private val quranText: QuranTextRepository) {

    private var chaptersByNumber: Map<Int, Chapter>? = null

    /**
     * Returns every ayah from [from] to [to] inclusive, crossing surah boundaries. A null [to]
     * means the end of the surah of [from]. Throws [IllegalArgumentException] for an ayah that does
     * not exist or a [to] before [from].
     */
    suspend fun build(from: AyahRef, to: AyahRef?): List<AyahRef> {
        val lastAyah = to ?: AyahRef(from.surah, verseCount(from.surah))
        require(from <= lastAyah) { "Range ends before it starts: $from to $lastAyah" }
        requireExists(from)
        requireExists(lastAyah)

        val ayahs = mutableListOf<AyahRef>()
        for (surah in from.surah..lastAyah.surah) {
            val firstInSurah = if (surah == from.surah) from.ayah else 1
            val lastInSurah = if (surah == lastAyah.surah) lastAyah.ayah else verseCount(surah)
            for (ayahNumber in firstInSurah..lastInSurah) ayahs += AyahRef(surah, ayahNumber)
        }
        return ayahs
    }

    private suspend fun requireExists(ayah: AyahRef) {
        require(ayah.ayah in 1..verseCount(ayah.surah)) { "No such ayah: $ayah" }
    }

    private suspend fun verseCount(surah: Int): Int {
        val chapters =
            chaptersByNumber
                ?: quranText.chapters().associateBy { it.number }.also { chaptersByNumber = it }
        val chapter = requireNotNull(chapters[surah]) { "No such surah: $surah" }
        return chapter.versesCount
    }
}
