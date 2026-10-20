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
 * so [databaseFile] may copy the bundled asset the first time it is called.
 */
class SqliteQuranTextRepository(
    private val databaseFile: () -> File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QuranTextRepository {

    private val database: SQLiteDatabase by lazy {
        val file = databaseFile()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private val chapterList: List<Chapter> by lazy { readChapters() }

    override suspend fun chapters(): List<Chapter> = withContext(ioDispatcher) { chapterList }

    override suspend fun wordsOfAyah(ayah: AyahRef): List<Word> =
        withContext(ioDispatcher) {
            val cursor =
                database.rawQuery(
                    "SELECT surah, ayah, position, text_uthmani, code_v1, code_v2, " +
                        "transliteration FROM word WHERE surah = ? AND ayah = ? ORDER BY position",
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
            val print = LayoutPrint.of(script)
            requirePageInRange(page)
            val wordRows = readWordRows(page, print)
            val markerRows = readMarkerRows(page, print)
            val bismillahBySurah = chapterList.associate { it.number to it.bismillahPre }
            val lines = PageLayoutBuilder.build(wordRows, markerRows, bismillahBySurah)
            PageLayout(page, script, lines)
        }

    override suspend fun pageOf(ayah: AyahRef, script: ScriptId): Int =
        withContext(ioDispatcher) {
            val print = LayoutPrint.of(script)
            val cursor =
                database.rawQuery(
                    "SELECT ${print.pageColumn} FROM ayah WHERE surah = ? AND ayah = ?",
                    arrayOf(ayah.surah.toString(), ayah.ayah.toString()),
                )
            cursor.use {
                require(it.moveToFirst()) { "There is no ayah $ayah." }
                it.getInt(0)
            }
        }

    override fun pageCount(script: ScriptId): Int {
        LayoutPrint.of(script)
        return PAGE_COUNT
    }

    override suspend fun ayahsOnPage(page: Int, script: ScriptId): List<AyahRef> =
        withContext(ioDispatcher) {
            val print = LayoutPrint.of(script)
            requirePageInRange(page)
            val cursor =
                database.rawQuery(
                    "SELECT DISTINCT surah, ayah FROM word WHERE ${print.pageColumn} = ? " +
                        "ORDER BY surah, ayah",
                    arrayOf(page.toString()),
                )
            cursor.use {
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
                "SELECT surah, ayah, position, text_uthmani, code_v1, code_v2, transliteration, " +
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

    private fun readMarkerRows(page: Int, print: LayoutPrint): List<PlacedAyahEnd> {
        val cursor =
            database.rawQuery(
                "SELECT surah, ayah, ${print.lineColumn} FROM ayah_marker " +
                    "WHERE ${print.pageColumn} = ?",
                arrayOf(page.toString()),
            )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(PlacedAyahEnd(AyahRef(it.getInt(0), it.getInt(1)), line = it.getInt(2)))
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
            transliteration = if (cursor.isNull(6)) null else cursor.getString(6),
        )

    private fun requirePageInRange(page: Int) {
        require(page in 1..PAGE_COUNT) { "Page must be 1..$PAGE_COUNT but was $page." }
    }

    private companion object {
        const val PAGE_COUNT = 604
        const val WORD_LINE_COLUMN = 7
    }
}

/**
 * Which page and line columns of `word` and `ayah_marker` a script is laid out with. QpcHafs and
 * V4Tajweed use the V2 layout (V4 draws the V2 glyph codes with colour fonts; docs/packs.md).
 */
internal enum class LayoutPrint(val pageColumn: String, val lineColumn: String) {
    V1("page_v1", "line_v1"),
    V2("page_v2", "line_v2");

    companion object {
        fun of(script: ScriptId): LayoutPrint =
            when (script) {
                ScriptId.V1 -> V1
                ScriptId.V2,
                ScriptId.QpcHafs,
                ScriptId.V4Tajweed -> V2
                ScriptId.IndoPak ->
                    throw UnsupportedOperationException(
                        "The IndoPak script is not available yet: no IndoPak layout pack exists.",
                    )
            }
    }
}
