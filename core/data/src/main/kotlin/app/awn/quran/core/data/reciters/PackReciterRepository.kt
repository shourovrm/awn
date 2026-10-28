package app.awn.quran.core.data.reciters

import android.database.sqlite.SQLiteDatabase
import app.awn.quran.core.data.AyahAudio
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.WordTiming
import app.awn.quran.core.data.packs.ManifestCatalog
import app.awn.quran.core.data.packs.PackStorage
import app.awn.quran.core.data.packs.ReadOnlyDatabases
import app.awn.quran.core.data.packs.ReciterRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds reciters from `reciters.json` and attaches word timings from the timing packs that are
 * installed (docs/packs.md).
 */
class PackReciterRepository(
    private val catalog: ManifestCatalog,
    private val storage: PackStorage,
    private val databases: ReadOnlyDatabases,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReciterRepository {

    override suspend fun reciters(): List<Reciter> =
        catalog.reciters.map { record ->
            Reciter(
                id = record.id,
                name = record.name,
                surahUrlPattern = record.surahUrlPattern,
                ayahUrlPattern = record.ayahUrlPattern,
                wordByWord = record.wordByWord,
                timingPack = record.timingPack?.entry?.id,
            )
        }

    @Suppress("ReturnCount") // Unknown reciter and no audio source are separate null cases.
    override suspend fun ayahAudio(reciterId: String, ayah: AyahRef): AyahAudio? {
        val reciter = catalog.reciters.firstOrNull { it.id == reciterId } ?: return null
        val pattern = reciter.ayahUrlPattern ?: return wholeSurahAyahAudio(reciter, ayah)
        val url = fillPattern(pattern, ayah)
        val timing = withContext(ioDispatcher) { readTiming(reciter, ayah) }
        return AyahAudio(ayah, url, timing?.durationMs, timing?.words.orEmpty())
    }

    /**
     * A reciter with one file per surah has no per-ayah file, so the timing pack is the only place
     * that says which surah file holds the ayah and where the ayah starts and ends in it. Null when
     * the pack is missing or has no row for the ayah, and the caller then has no audio to play.
     */
    private suspend fun wholeSurahAyahAudio(reciter: ReciterRecord, ayah: AyahRef): AyahAudio? {
        val timing = withContext(ioDispatcher) { readTiming(reciter, ayah) } ?: return null
        val surahUrl =
            checkNotNull(timing.url) {
                "Timing pack of reciter ${reciter.id} has no url for ${ayah.surah}:${ayah.ayah}."
            }
        val byteRange = withContext(ioDispatcher) { readByteRange(reciter, ayah) }
        return AyahAudio(
            ayah = ayah,
            url = surahUrl,
            durationMs = timing.durationMs,
            words = timing.words,
            startMs = timing.startMs,
            isInWholeSurahFile = true,
            startByte = byteRange?.startByte,
            endByte = byteRange?.endByte,
            startByteMs = byteRange?.startByteMs,
        )
    }

    /** The open timing pack of [reciter], or null when it has none or it is not installed. */
    private fun openTimingPack(reciter: ReciterRecord): SQLiteDatabase? {
        val file = reciter.timingPack?.let(storage::locate) ?: return null
        return databases.open(file)
    }

    @Suppress("ReturnCount") // Each missing pack or row is a separate "no timing" case.
    private fun readTiming(reciter: ReciterRecord, ayah: AyahRef): AyahTiming? {
        val database = openTimingPack(reciter) ?: return null
        val arguments = arrayOf(ayah.surah.toString(), ayah.ayah.toString())

        val row = readAyahRow(database, arguments) ?: return null
        return AyahTiming(
            url = row.url,
            startMs = row.offsetMs,
            durationMs = row.durationMs?.plus(row.offsetMs),
            words = readWords(database, arguments, row.offsetMs),
        )
    }

    /**
     * Reads the byte range of [ayah] in its surah file. Packs built before the `ayah_bytes` table
     * existed have no table, so the table is looked up in `sqlite_master` first; a missing table or
     * row gives null, while a failing query still throws.
     */
    @Suppress("ReturnCount") // Each missing pack, table or row is a separate null case.
    private fun readByteRange(reciter: ReciterRecord, ayah: AyahRef): AyahByteRange? {
        val database = openTimingPack(reciter) ?: return null
        if (!hasTable(database, AYAH_BYTES_TABLE)) return null

        val arguments = arrayOf(ayah.surah.toString(), ayah.ayah.toString())
        val cursor =
            database.rawQuery(
                "SELECT start_byte, end_byte, start_byte_ms FROM ayah_bytes " +
                    "WHERE surah = ? AND ayah = ?",
                arguments,
            )
        return cursor.use {
            if (!it.moveToFirst()) return null
            AyahByteRange(
                startByte = it.getLong(0),
                endByte = if (it.isNull(1)) null else it.getLong(1),
                startByteMs = if (it.isNull(2)) null else it.getInt(2),
            )
        }
    }

    private fun hasTable(database: SQLiteDatabase, tableName: String): Boolean {
        val cursor =
            database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(tableName),
            )
        return cursor.use { it.moveToFirst() }
    }

    private fun readAyahRow(database: SQLiteDatabase, arguments: Array<String>): AyahRow? {
        val cursor =
            database.rawQuery(
                "SELECT url, duration_ms, offset_ms FROM ayah_audio WHERE surah = ? AND ayah = ?",
                arguments,
            )
        return cursor.use {
            if (!it.moveToFirst()) return null
            AyahRow(
                url = if (it.isNull(0)) null else it.getString(0),
                durationMs = if (it.isNull(1)) null else it.getInt(1),
                offsetMs = it.getInt(2),
            )
        }
    }

    /** Word times are stored from the ayah start, so [offsetMs] moves them to the file start. */
    private fun readWords(
        database: SQLiteDatabase,
        arguments: Array<String>,
        offsetMs: Int,
    ): List<WordTiming> {
        val cursor =
            database.rawQuery(
                "SELECT position, start_ms, end_ms FROM segment WHERE surah = ? AND ayah = ? " +
                    "ORDER BY position",
                arguments,
            )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(WordTiming(it.getInt(0), it.getInt(1) + offsetMs, it.getInt(2) + offsetMs))
                }
            }
        }
    }

    /**
     * One `ayah_audio` row. [url] is null when the pack gives none, [offsetMs] is where the ayah
     * starts in its file and [durationMs] is the ayah's length, or null when it is unknown.
     */
    private data class AyahRow(val url: String?, val durationMs: Int?, val offsetMs: Int)

    /**
     * One `ayah_bytes` row: [startByte] is the first byte kept for the ayah, [endByte] is null for
     * the last ayah of a surah, and [startByteMs] is the time of [startByte] in the file.
     */
    private data class AyahByteRange(val startByte: Long, val endByte: Long?, val startByteMs: Int?)

    /**
     * What the timing pack says about one ayah. [startMs] and [durationMs] are in milliseconds from
     * the start of the file at [url]; [durationMs] is the end of the ayah there, or null when the
     * pack does not know it. [words] are shifted to the same origin.
     */
    private data class AyahTiming(
        val url: String?,
        val startMs: Int,
        val durationMs: Int?,
        val words: List<WordTiming>,
    )

    private fun fillPattern(pattern: String, ayah: AyahRef): String =
        pattern
            .replace("{SSS}", ayah.surah.toString().padStart(PADDED_DIGITS, '0'))
            .replace("{AAA}", ayah.ayah.toString().padStart(PADDED_DIGITS, '0'))

    private companion object {
        const val PADDED_DIGITS = 3

        /** Packs built before this table existed lack it; its absence means no byte range. */
        const val AYAH_BYTES_TABLE = "ayah_bytes"
    }
}
