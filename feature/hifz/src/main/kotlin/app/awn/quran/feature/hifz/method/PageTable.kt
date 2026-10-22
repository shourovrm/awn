package app.awn.quran.feature.hifz.method

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Quarters
import app.awn.quran.core.data.ScriptId

/**
 * Which pagination a set of page numbers follows. A hafiz memorises the page images of one print,
 * so a page number only means something together with its print. [storedName] is saved in the
 * database, so it never changes.
 */
enum class PageLayoutFamily(val storedName: String) {
    /** The 1421 print: QPC Hafs, V2 and the tajweed colour print share its 604 pages. */
    Madani1421("madani_1421"),

    /** The 1405 print (V1): 604 pages, some of them break at other ayahs than 1421. */
    Madani1405("madani_1405"),

    /** The IndoPak print of 15 lines a page: 610 pages. */
    IndoPak15Lines("indopak_15"),

    /** The IndoPak print of 16 lines a page: 548 pages. */
    IndoPak16Lines("indopak_16");

    companion object {
        fun fromStoredName(name: String): PageLayoutFamily =
            entries.firstOrNull { it.storedName == name }
                ?: error("Unknown page layout '$name' in the hifz database.")

        /** The pagination the user reads in [script]. */
        fun of(script: ScriptId): PageLayoutFamily =
            when (script) {
                ScriptId.V1 -> Madani1405
                ScriptId.V2,
                ScriptId.QpcHafs,
                ScriptId.V4Tajweed -> Madani1421
                ScriptId.IndoPak15 -> IndoPak15Lines
                ScriptId.IndoPak16 -> IndoPak16Lines
            }

        /** The script whose pages the repository reads for this family. */
        fun scriptFor(family: PageLayoutFamily): ScriptId =
            when (family) {
                Madani1405 -> ScriptId.V1
                Madani1421 -> ScriptId.QpcHafs
                IndoPak15Lines -> ScriptId.IndoPak15
                IndoPak16Lines -> ScriptId.IndoPak16
            }
    }
}

/**
 * The ayahs printed on every page of one print, in reading order. An ayah that runs over a page
 * break is listed on both pages. The page that an ayah belongs to for lessons is the page where it
 * starts ([startPageOf]).
 */
class PageTable(
    private val ayahsByPage: List<List<AyahRef>>,
    private val juzByPage: List<Int>,
) {
    init {
        require(ayahsByPage.size == juzByPage.size) {
            "Every page needs a juz: ${ayahsByPage.size} pages, ${juzByPage.size} juz entries."
        }
    }

    val pageCount: Int
        get() = ayahsByPage.size

    private val startPageByAyah: Map<AyahRef, Int>
    private val endPageByAyah: Map<AyahRef, Int>

    /** Every ayah in reading order. */
    val allAyahs: List<AyahRef>

    /** The ayahs that start on each page (index page - 1), which partition [allAyahs]. */
    private val ownAyahsByPage: List<List<AyahRef>>

    init {
        val starts = LinkedHashMap<AyahRef, Int>()
        val ends = HashMap<AyahRef, Int>()
        ayahsByPage.forEachIndexed { index, ayahs ->
            ayahs.forEach { ayah ->
                starts.putIfAbsent(ayah, index + 1)
                ends[ayah] = index + 1
            }
        }
        startPageByAyah = starts
        endPageByAyah = ends
        allAyahs = starts.keys.sorted()
        ownAyahsByPage =
            List(ayahsByPage.size) { index ->
                ayahsByPage[index].filter { starts.getValue(it) == index + 1 }
            }
    }

    fun ayahsOn(page: Int): List<AyahRef> = ayahsByPage[requirePage(page) - 1]

    /** The ayahs that start on [page]; empty for a page filled by an ayah from before it. */
    fun ayahsStartingOn(page: Int): List<AyahRef> = ownAyahsByPage[requirePage(page) - 1]

    fun startPageOf(ayah: AyahRef): Int =
        startPageByAyah[ayah] ?: error("Ayah $ayah is on no page of this print.")

    fun endPageOf(ayah: AyahRef): Int =
        endPageByAyah[ayah] ?: error("Ayah $ayah is on no page of this print.")

    /** The juz of the first ayah on [page]. */
    fun juzOf(page: Int): Int = juzByPage[requirePage(page) - 1]

    /** The pages of [juz], which are consecutive. */
    fun pagesOfJuz(juz: Int): IntRange {
        val pages = (1..pageCount).filter { juzByPage[it - 1] == juz }
        require(pages.isNotEmpty()) { "No page of this print starts in juz $juz." }
        return pages.first()..pages.last()
    }

    /** The pages that hold any ayah of [surah]. */
    fun pagesOfSurah(surah: Int): IntRange {
        val ayahs = allAyahs.filter { it.surah == surah }
        require(ayahs.isNotEmpty()) { "Surah $surah is not in this print." }
        return startPageOf(ayahs.first())..endPageOf(ayahs.last())
    }

    /** The ayah after [ayah] in reading order, null after the last one. */
    fun ayahAfter(ayah: AyahRef): AyahRef? {
        val index = allAyahs.binarySearch(ayah)
        require(index >= 0) { "Ayah $ayah is not in this print." }
        return allAyahs.getOrNull(index + 1)
    }

    private fun requirePage(page: Int): Int {
        require(page in 1..pageCount) { "Page must be 1..$pageCount but was $page." }
        return page
    }

    companion object {
        /**
         * Builds a table from the ayahs of every page, finding each page's juz from its first ayah.
         */
        fun fromPages(ayahsByPage: List<List<AyahRef>>): PageTable {
            var previousJuz = 1
            val juzByPage = ayahsByPage.map { ayahs ->
                // A page with no ayah of its own (not expected in a Quran print) keeps the
                // juz before it.
                ayahs.firstOrNull()?.let { previousJuz = Quarters.containing(it).juz }
                previousJuz
            }
            return PageTable(ayahsByPage, juzByPage)
        }
    }
}
