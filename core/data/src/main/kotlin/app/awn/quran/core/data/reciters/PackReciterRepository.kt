package app.awn.quran.core.data.reciters

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

    @Suppress("ReturnCount") // Unknown reciter and no ayah files are separate null cases.
    override suspend fun ayahAudio(reciterId: String, ayah: AyahRef): AyahAudio? {
        val reciter = catalog.reciters.firstOrNull { it.id == reciterId } ?: return null
        val pattern = reciter.ayahUrlPattern ?: return null
        val url = fillPattern(pattern, ayah)
        val timing = withContext(ioDispatcher) { readTiming(reciter, ayah) }
        return AyahAudio(ayah, url, timing?.durationMs, timing?.words.orEmpty())
    }

    @Suppress("ReturnCount") // Each missing pack, row or table is a separate "no timing" case.
    private fun readTiming(reciter: ReciterRecord, ayah: AyahRef): AyahTiming? {
        val packRecord = reciter.timingPack ?: return null
        val file = storage.locate(packRecord) ?: return null
        val database = databases.open(file)
        val arguments = arrayOf(ayah.surah.toString(), ayah.ayah.toString())

        val audioCursor =
            database.rawQuery(
                "SELECT duration_ms, offset_ms FROM ayah_audio WHERE surah = ? AND ayah = ?",
                arguments,
            )
        val (durationMs, offsetMs) =
            audioCursor.use {
                if (!it.moveToFirst()) return null
                val duration = if (it.isNull(0)) null else it.getInt(0)
                duration to it.getInt(1)
            }

        val segmentCursor =
            database.rawQuery(
                "SELECT position, start_ms, end_ms FROM segment WHERE surah = ? AND ayah = ? " +
                    "ORDER BY position",
                arguments,
            )
        val words = segmentCursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(WordTiming(it.getInt(0), it.getInt(1) + offsetMs, it.getInt(2) + offsetMs))
                }
            }
        }
        return AyahTiming(durationMs?.plus(offsetMs), words)
    }

    private data class AyahTiming(val durationMs: Int?, val words: List<WordTiming>)

    private fun fillPattern(pattern: String, ayah: AyahRef): String =
        pattern
            .replace("{SSS}", ayah.surah.toString().padStart(PADDED_DIGITS, '0'))
            .replace("{AAA}", ayah.ayah.toString().padStart(PADDED_DIGITS, '0'))

    private companion object {
        const val PADDED_DIGITS = 3
    }
}
