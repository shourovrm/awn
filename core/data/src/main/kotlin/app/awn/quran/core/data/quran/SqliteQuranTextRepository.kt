package app.awn.quran.core.data.quran

import android.database.sqlite.SQLiteDatabase
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions") // Interface members plus one private reader per table.
/**
 * Reads `quran.sqlite` (pack format in docs/packs.md). The file is opened read-only on first use,
 * so [databaseFile] may copy the bundled asset the first time it is called. The IndoPak layouts
 * come from `indopak.sqlite`: [indoPakDatabase] returns it, or null while it is not installed.
 */
class SqliteQuranTextRepository(
    private val databaseFile: () -> File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val indoPakDatabase: () -> SQLiteDatabase? = { null },
) : QuranTextRepository {

    private val database: SQLiteDatabase by lazy {
        val file = databaseFile()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private val chapterList: List<Chapter> by lazy { readChapters() }

    private val indoPakReader =
        IndoPakLayoutReader(
            database = ::requireIndoPakDatabase,
            uthmaniTextByPosition = ::uthmaniTextByPosition,
        )

    override suspend fun chapters(): List<Chapter> = withContext(ioDispatcher) { chapterList }

    override suspend fun wordsOfAyah(ayah: AyahRef): List<Word> =
        withContext(ioDispatcher) {
            val cursor =
                database.rawQuery(
                    "SELECT surah, ayah, position, text_uthmani, code_v1, code_v2 " +
                        "FROM word WHERE surah = ? AND ayah = ? ORDER BY position",
                    arrayOf(ayah.surah.toString(), ayah.ayah.toString()),
                )
            cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        add(readWord(it))
                    }
                }
            }
        }

    override suspend fun pageLayout(page: Int, script: ScriptId): PageLayout =
        withContext(ioDispatcher) {
            val indoPakPrint = IndoPakPrint.of(script)
            if (indoPakPrint == null) {
                madaniPageLayout(page, script)
            } else {
                indoPakReader.pageLayout(page, indoPakPrint, bismillahPreBySurah())
            }
        }

    override suspend fun pageOf(ayah: AyahRef, script: ScriptId): Int =
        withContext(ioDispatcher) {
            val indoPakPrint = IndoPakPrint.of(script)
            if (indoPakPrint == null) {
                madaniPageOf(ayah, script)
            } else {
                indoPakReader.pageOf(ayah, indoPakPrint)
            }
        }

    override fun pageCount(script: ScriptId): Int = IndoPakPrint.of(script)?.pageCount ?: PAGE_COUNT

    override suspend fun ayahsOnPage(page: Int, script: ScriptId): List<AyahRef> =
        withContext(ioDispatcher) {
            val indoPakPrint = IndoPakPrint.of(script)
            if (indoPakPrint == null) {
                madaniAyahsOnPage(page, script)
            } else {
                indoPakReader.ayahsOnPage(page, indoPakPrint)
            }
        }

    private fun madaniPageLayout(page: Int, script: ScriptId): PageLayout {
        val print = LayoutPrint.of(script)
        requirePageInRange(page)
        val wordRows = readWordRows(page, print)
        val markerRows = readMarkerRows(page, print)
        val nextPageStarts =
            if (page < PAGE_COUNT) readNextPageStarts(page + 1, print) else emptyList()
        val lines =
            PageLayoutBuilder.build(wordRows, markerRows, bismillahPreBySurah(), nextPageStarts)
        return PageLayout(page, script, lines)
    }

    private fun bismillahPreBySurah(): Map<Int, Boolean> = chapterList.associate {
        it.number to it.bismillahPre
    }

    private fun requireIndoPakDatabase(): SQLiteDatabase =
        indoPakDatabase()
            ?: throw UnsupportedOperationException("The IndoPak layouts are not installed.")

    /** The Uthmani text of each word of [ayah], keyed by word position. */
    private fun uthmaniTextByPosition(ayah: AyahRef): Map<Int, String> {
        val cursor =
            database.rawQuery(
                "SELECT position, text_uthmani FROM word WHERE surah = ? AND ayah = ?",
                arrayOf(ayah.surah.toString(), ayah.ayah.toString()),
            )
        return cursor.use {
            buildMap {
                while (it.moveToNext()) {
                    put(it.getInt(0), it.getString(1))
                }
            }
        }
    }

    private fun madaniPageOf(ayah: AyahRef, script: ScriptId): Int {
        val print = LayoutPrint.of(script)
        val cursor =
            database.rawQuery(
                "SELECT ${print.pageColumn} FROM ayah WHERE surah = ? AND ayah = ?",
                arrayOf(ayah.surah.toString(), ayah.ayah.toString()),
            )
        return cursor.use {
            require(it.moveToFirst()) { "There is no ayah $ayah." }
            it.getInt(0)
        }
    }

    private fun madaniAyahsOnPage(page: Int, script: ScriptId): List<AyahRef> {
        val print = LayoutPrint.of(script)
        requirePageInRange(page)
        val cursor =
            database.rawQuery(
                "SELECT DISTINCT surah, ayah FROM word WHERE ${print.pageColumn} = ? " +
                    "ORDER BY surah, ayah",
                arrayOf(page.toString()),
            )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(AyahRef(it.getInt(0), it.getInt(1)))
                }
            }
        }
    }

    @Suppress("MagicNumber") // Column positions of the SELECT list just above.
    private fun readChapters(): List<Chapter> {
        val cursor =
            database.rawQuery(
                "SELECT number, name_arabic, name_simple, name_bangla, verses_count, " +
                    "revelation_place, bismillah_pre FROM chapter ORDER BY number",
                null,
            )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        Chapter(
                            number = it.getInt(0),
                            nameArabic = it.getString(1),
                            nameSimple = it.getString(2),
                            nameBangla = if (it.isNull(3)) null else it.getString(3),
                            versesCount = it.getInt(4),
                            revelationPlace = it.getString(5),
                            bismillahPre = it.getInt(6) == 1,
                        ),
                    )
                }
            }
        }
    }

    private fun readWordRows(page: Int, print: LayoutPrint): List<PlacedWord> {
        val cursor =
            database.rawQuery(
                "SELECT surah, ayah, position, text_uthmani, code_v1, code_v2, " +
                    "${print.lineColumn} FROM word WHERE ${print.pageColumn} = ? " +
                    "ORDER BY surah, ayah, position",
                arrayOf(page.toString()),
            )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(PlacedWord(readWord(it), line = it.getInt(WORD_LINE_COLUMN)))
                }
            }
        }
    }

    /**
     * The first words of surahs that start on line 1 or 2 of [page]: their banner is on the page
     * before.
     */
    private fun readNextPageStarts(page: Int, print: LayoutPrint): List<PlacedWord> {
        val cursor =
            database.rawQuery(
                "SELECT surah, ayah, position, text_uthmani, code_v1, code_v2, " +
                    "${print.lineColumn} FROM word WHERE ${print.pageColumn} = ? " +
                    "AND ayah = 1 AND position = 1 AND ${print.lineColumn} <= 2",
                arrayOf(page.toString()),
            )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(PlacedWord(readWord(it), line = it.getInt(WORD_LINE_COLUMN)))
                }
            }
        }
    }

    private fun readMarkerRows(page: Int, print: LayoutPrint): List<PlacedAyahEnd> {
        // The end sign is printed with the glyph that follows the ayah's last word in the page
        // font.
        val cursor =
            database.rawQuery(
                "SELECT m.surah, m.ayah, m.${print.lineColumn}, " +
                    "(SELECT w.${print.codeColumn} FROM word w WHERE w.surah = m.surah " +
                    "AND w.ayah = m.ayah ORDER BY w.position DESC LIMIT 1) " +
                    "FROM ayah_marker m WHERE m.${print.pageColumn} = ?",
                arrayOf(page.toString()),
            )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val lastWordCode =
                        if (it.isNull(MARKER_LAST_CODE_COLUMN)) null
                        else it.getString(MARKER_LAST_CODE_COLUMN)
                    add(
                        PlacedAyahEnd(
                            AyahRef(it.getInt(0), it.getInt(1)),
                            line = it.getInt(MARKER_LINE_COLUMN),
                            glyphCode = endSignCode(lastWordCode, print),
                        ),
                    )
                }
            }
        }
    }

    @Suppress("MagicNumber") // Column positions of the SELECT lists that feed this function.
    private fun readWord(cursor: android.database.Cursor): Word =
        Word(
            key = WordKey(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2)),
            textUthmani = cursor.getString(3),
            codeV1 = cursor.getString(4),
            codeV2 = cursor.getString(5),
        )

    private fun requirePageInRange(page: Int) {
        require(page in 1..PAGE_COUNT) { "Page must be 1..$PAGE_COUNT but was $page." }
    }

    private companion object {
        const val PAGE_COUNT = 604
        const val WORD_LINE_COLUMN = 6
        const val MARKER_LINE_COLUMN = 2
        const val MARKER_LAST_CODE_COLUMN = 3
    }
}

/**
 * Which page and line columns of `word` and `ayah_marker` of `quran.sqlite` a script is laid out
 * with; the IndoPak prints have no entry (see [IndoPakPrint]). QpcHafs and V4Tajweed use the V2
 * layout (V4 draws the V2 glyph codes with colour fonts; docs/packs.md).
 */
internal enum class LayoutPrint(
    val pageColumn: String,
    val lineColumn: String,
    val codeColumn: String,
) {
    V1("page_v1", "line_v1", "code_v1"),
    V2("page_v2", "line_v2", "code_v2");

    companion object {
        fun of(script: ScriptId): LayoutPrint =
            when (script) {
                ScriptId.V1 -> V1
                ScriptId.V2,
                ScriptId.QpcHafs,
                ScriptId.V4Tajweed -> V2
                ScriptId.IndoPak15 ->
                    throw IllegalArgumentException(
                        "$script is laid out by indopak.sqlite, not by quran.sqlite.",
                    )
            }
    }
}

/**
 * The page fonts allot glyph codes in reading order, so the end sign of an ayah has the code after
 * the one of its last word (checked for all 6,236 ayahs in V2 and for all but 41 in V1). Those 41
 * end with the shared glyph U+FBB1 and print their sign as U+FBD3. Returns null without a code.
 */
internal fun endSignCode(lastWordCode: String?, print: LayoutPrint): String? {
    val lastGlyph = lastWordCode?.lastOrNull() ?: return null
    val isSharedV1Glyph = print == LayoutPrint.V1 && lastGlyph.code == V1_SHARED_GLYPH
    val endCodePoint = if (isSharedV1Glyph) V1_SHARED_GLYPH_END_SIGN else lastGlyph.code + 1
    return endCodePoint.toChar().toString()
}

private const val V1_SHARED_GLYPH = 0xFBB1
private const val V1_SHARED_GLYPH_END_SIGN = 0xFBD3
