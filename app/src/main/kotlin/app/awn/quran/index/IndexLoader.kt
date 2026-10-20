package app.awn.quran.index

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.SavedAyah
import app.awn.quran.core.data.ScriptId

/** The print whose page numbers the index shows: the 1421 Madani print, the default. */
val INDEX_SCRIPT = ScriptId.V2

private const val FIRST_WORDS_COUNT = 4

/** The parts of the index that do not change while the app runs. */
class IndexContent(
    val surahGroups: List<JuzGroup>,
    val juzSections: List<JuzSection>,
)

/** Reads the surah list and the juz quarters from the Quran text. */
class IndexLoader(
    private val quranText: QuranTextRepository,
    private val quarterTable: QuarterTable,
    private val script: ScriptId = INDEX_SCRIPT,
) {

    private val surahOfPage = mutableMapOf<Int, Int>()

    suspend fun loadContent(): IndexContent {
        val chapters = quranText.chapters()
        val surahPages = chapters.associate {
            it.number to quranText.pageOf(AyahRef(it.number, 1), script)
        }
        val juzPages =
            JuzStarts.ayahs
                .mapIndexed { index, ayah -> index + 1 to quranText.pageOf(ayah, script) }
                .toMap()
        return IndexContent(
            surahGroups = groupSurahsByJuz(chapters, surahPages, juzPages),
            juzSections = loadJuzSections(juzPages),
        )
    }

    private suspend fun loadJuzSections(juzPages: Map<Int, Int>): List<JuzSection> =
        quarterTable
            .quarters()
            .groupBy { it.juz }
            .toSortedMap()
            .map { (juz, quarters) ->
                JuzSection(
                    juz = juz,
                    startPage = juzPages.getValue(juz),
                    quarters = quarters.sortedBy { it.quarterOfJuz }.map { toRow(it) },
                )
            }

    private suspend fun toRow(quarter: QuarterStart): QuarterRow =
        QuarterRow(
            juz = quarter.juz,
            hizb = hizbOfQuarter(quarter.juz, quarter.quarterOfJuz),
            fraction = fractionOfQuarter(quarter.quarterOfJuz),
            firstAyah = quarter.firstAyah,
            firstWords =
                quranText.wordsOfAyah(quarter.firstAyah).take(FIRST_WORDS_COUNT).joinToString(" ") {
                    it.textUthmani
                },
            page = quranText.pageOf(quarter.firstAyah, script),
        )

    /** The surah that begins [page]'s text, used to name a bookmarked page. */
    suspend fun surahOnPage(page: Int): Int =
        surahOfPage.getOrPut(page) {
            quranText.ayahsOnPage(page, script).firstOrNull()?.surah ?: 1
        }

    suspend fun toAyahMark(saved: SavedAyah): BookmarkEntry.AyahMark =
        BookmarkEntry.AyahMark(
            ayah = saved.ayah,
            page = quranText.pageOf(saved.ayah, script),
            createdAt = saved.createdAt,
        )
}
