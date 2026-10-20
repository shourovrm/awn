package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId

/** How much audio to fetch when the audio at the start of a range to play is missing. */
enum class DownloadAmount {
    /** The ayahs on the page where playback starts, from the start ayah on. */
    Page,

    /** Every ayah of the surah where playback starts. */
    Surah,

    /** Every ayah of the juz where playback starts. */
    Juz,
}

/** Start of each of the 30 juz in the Madani print. */
object JuzTable {

    val firstAyahs: List<AyahRef> =
        listOf(
            AyahRef(1, 1),
            AyahRef(2, 142),
            AyahRef(2, 253),
            AyahRef(3, 93),
            AyahRef(4, 24),
            AyahRef(4, 148),
            AyahRef(5, 82),
            AyahRef(6, 111),
            AyahRef(7, 88),
            AyahRef(8, 41),
            AyahRef(9, 93),
            AyahRef(11, 6),
            AyahRef(12, 53),
            AyahRef(15, 1),
            AyahRef(17, 1),
            AyahRef(18, 75),
            AyahRef(21, 1),
            AyahRef(23, 1),
            AyahRef(25, 21),
            AyahRef(27, 56),
            AyahRef(29, 46),
            AyahRef(33, 31),
            AyahRef(36, 28),
            AyahRef(39, 32),
            AyahRef(41, 47),
            AyahRef(46, 1),
            AyahRef(51, 31),
            AyahRef(58, 1),
            AyahRef(67, 1),
            AyahRef(78, 1),
        )

    const val COUNT = 30

    /** Returns the juz (1..30) that holds [ayah]. */
    fun juzOf(ayah: AyahRef): Int = firstAyahs.indexOfLast { it <= ayah } + 1
}

/**
 * Works out which ayahs to download before a range plays, by the "Download amount" setting. The
 * amount is counted from where playback starts; ayahs beyond it stream when they are reached.
 * [script] decides which print's page the start ayah is on (the print only moves 56 ayahs).
 */
class DownloadPlan(
    private val quranText: QuranTextRepository,
    private val script: ScriptId = ScriptId.V2,
) {

    private val playlistBuilder = PlaylistBuilder(quranText)

    /**
     * Returns the ayahs to fetch for [amount] when [range] starts at its first ayah, in reading
     * order: the range's ayahs that sit on the starting page, the whole starting surah, or the
     * whole starting juz.
     */
    suspend fun ayahsFor(amount: DownloadAmount, range: List<AyahRef>): List<AyahRef> {
        val start = range.firstOrNull() ?: return emptyList()
        return when (amount) {
            DownloadAmount.Page -> {
                val page = quranText.pageOf(start, script)
                val onPage = quranText.ayahsOnPage(page, script).toSet()
                range.filter { it in onPage }
            }
            DownloadAmount.Surah -> surahAyahs(start.surah)
            DownloadAmount.Juz -> juzAyahs(JuzTable.juzOf(start))
        }
    }

    /** Returns every ayah of [surah]. */
    suspend fun surahAyahs(surah: Int): List<AyahRef> =
        playlistBuilder.build(AyahRef(surah, 1), null)

    /** Returns every ayah of [juz] (1..30). */
    suspend fun juzAyahs(juz: Int): List<AyahRef> {
        require(juz in 1..JuzTable.COUNT) { "No such juz: $juz" }
        val first = JuzTable.firstAyahs[juz - 1]
        val last =
            if (juz == JuzTable.COUNT) {
                lastAyahOfSurah(LAST_SURAH)
            } else {
                ayahBefore(JuzTable.firstAyahs[juz])
            }
        return playlistBuilder.build(first, last)
    }

    /** Returns every ayah from [from] to [to] inclusive, across surah boundaries. */
    suspend fun rangeAyahs(from: AyahRef, to: AyahRef): List<AyahRef> =
        playlistBuilder.build(from, to)

    /** Returns every ayah of the Quran. */
    suspend fun allAyahs(): List<AyahRef> =
        playlistBuilder.build(AyahRef(1, 1), lastAyahOfSurah(LAST_SURAH))

    private suspend fun ayahBefore(ayah: AyahRef): AyahRef =
        if (ayah.ayah > 1) AyahRef(ayah.surah, ayah.ayah - 1) else lastAyahOfSurah(ayah.surah - 1)

    private suspend fun lastAyahOfSurah(surah: Int): AyahRef {
        val chapter = quranText.chapters().first { it.number == surah }
        return AyahRef(surah, chapter.versesCount)
    }

    private companion object {
        const val LAST_SURAH = 114
    }
}
