package app.awn.quran.core.data.quran

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey

/**
 * The page layout stored in `indopak.sqlite` (docs/packs.md, "IndoPak"): the column names that hold
 * the page and line of each word and ayah-end marker, and the size of the layout. The file also
 * holds a 16-line layout (`page_16`), which the app does not offer.
 */
internal enum class IndoPakPrint(
    val script: ScriptId,
    val pageColumn: String,
    val lineColumn: String,
    val pageCount: Int,
    val linesPerPage: Int,
) {
    Lines15(ScriptId.IndoPak15, "page_15", "line_15", pageCount = 610, linesPerPage = 15);

    companion object {
        /** The layout of [script], or null when [script] is not an IndoPak print. */
        fun of(script: ScriptId): IndoPakPrint? = entries.firstOrNull { it.script == script }
    }
}

/**
 * Reads pages of the IndoPak layout pack. The pack holds the Nastaleeq text of each word but not
 * the Uthmani text, so [uthmaniTextByPosition] supplies it from `quran.sqlite` for every ayah on
 * the page; the other screens (index, continue card) read it from the word.
 */
internal class IndoPakLayoutReader(
    private val database: () -> SQLiteDatabase,
    private val uthmaniTextByPosition: (AyahRef) -> Map<Int, String>,
) {

    fun pageLayout(
        page: Int,
        print: IndoPakPrint,
        bismillahPreBySurah: Map<Int, Boolean>,
    ): PageLayout {
        requirePageInRange(page, print)
        val words = readWords(page, print)
        val ayahEnds = readAyahEnds(page, print)
        val lines =
            PageLayoutBuilder.build(
                words = words,
                ayahEnds = ayahEnds,
                bismillahPreBySurah = bismillahPreBySurah,
                placement = HeaderPlacement(print.linesPerPage, HeaderOverflow.ExtraLinesAbove),
            )
        return PageLayout(page, print.script, lines)
    }

    /** The page that holds the first word of [ayah]. */
    fun pageOf(ayah: AyahRef, print: IndoPakPrint): Int {
        val cursor =
            database()
                .rawQuery(
                    "SELECT ${print.pageColumn} FROM word " +
                        "WHERE surah = ? AND ayah = ? AND position = 1",
                    arrayOf(ayah.surah.toString(), ayah.ayah.toString()),
                )
        return cursor.use {
            require(it.moveToFirst()) { "There is no ayah $ayah." }
            it.getInt(0)
        }
    }

    fun ayahsOnPage(page: Int, print: IndoPakPrint): List<AyahRef> {
        requirePageInRange(page, print)
        val cursor =
            database()
                .rawQuery(
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

    private fun readWords(page: Int, print: IndoPakPrint): List<PlacedWord> {
        val cursor =
            database()
                .rawQuery(
                    "SELECT surah, ayah, position, text_nastaleeq, ${print.lineColumn} " +
                        "FROM word WHERE ${print.pageColumn} = ? ORDER BY surah, ayah, position",
                    arrayOf(page.toString()),
                )
        val rows = cursor.use { readWordRows(it) }
        val uthmaniByAyah = rows.map { it.ayah }.distinct().associateWith(uthmaniTextByPosition)
        return rows.map { row ->
            val uthmaniText = uthmaniByAyah.getValue(row.ayah)[row.position]
            checkNotNull(uthmaniText) { "quran.sqlite has no word ${row.ayah}:${row.position}." }
            val word =
                Word(
                    key = WordKey(row.ayah.surah, row.ayah.ayah, row.position),
                    textUthmani = uthmaniText,
                    codeV1 = null,
                    codeV2 = null,
                    textNastaleeq = row.textNastaleeq,
                )
            PlacedWord(word, row.line)
        }
    }

    @Suppress("MagicNumber") // Column positions of the SELECT list in readWords.
    private fun readWordRows(cursor: Cursor): List<IndoPakWordRow> = buildList {
        while (cursor.moveToNext()) {
            add(
                IndoPakWordRow(
                    ayah = AyahRef(cursor.getInt(0), cursor.getInt(1)),
                    position = cursor.getInt(2),
                    textNastaleeq = cursor.getString(3),
                    line = cursor.getInt(4),
                ),
            )
        }
    }

    @Suppress("MagicNumber") // Column positions of the SELECT list below.
    private fun readAyahEnds(page: Int, print: IndoPakPrint): List<PlacedAyahEnd> {
        val cursor =
            database()
                .rawQuery(
                    "SELECT surah, ayah, ${print.lineColumn}, end_sign FROM ayah_marker " +
                        "WHERE ${print.pageColumn} = ?",
                    arrayOf(page.toString()),
                )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        PlacedAyahEnd(
                            ayah = AyahRef(it.getInt(0), it.getInt(1)),
                            line = it.getInt(2),
                            glyphCode = it.getString(3),
                        ),
                    )
                }
            }
        }
    }

    private fun requirePageInRange(page: Int, print: IndoPakPrint) {
        require(page in 1..print.pageCount) {
            "Page must be 1..${print.pageCount} in ${print.script} but was $page."
        }
    }
}

private class IndoPakWordRow(
    val ayah: AyahRef,
    val position: Int,
    val textNastaleeq: String,
    val line: Int,
)
