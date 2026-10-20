package app.awn.quran.core.data.search

import android.database.sqlite.SQLiteDatabase
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.packs.ManifestCatalog
import app.awn.quran.core.data.packs.PackStorage
import app.awn.quran.core.data.packs.ReadOnlyDatabases
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads every ayah text for [TextSearch] with one query per pack. Per-ayah repository calls would
 * need about 6,236 queries per text, so search reads the tables whole instead.
 */
class SqliteSearchTexts(
    private val quranDatabaseFile: () -> File,
    private val catalog: ManifestCatalog,
    private val storage: PackStorage,
    private val databases: ReadOnlyDatabases,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** The Arabic text first, then every installed ayah translation in manifest order. */
    suspend fun load(): List<SearchableTexts> =
        withContext(ioDispatcher) { listOf(arabic()) + installedTranslations() }

    private fun arabic(): SearchableTexts {
        val database =
            SQLiteDatabase.openDatabase(
                quranDatabaseFile().path,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
        return database.use {
            read(it, SearchSource.Arabic, "SELECT surah, ayah, text_uthmani FROM ayah")
        }
    }

    private fun installedTranslations(): List<SearchableTexts> =
        catalog.translationPacks
            .filter { it.translationKind == AYAH_PACK_KIND }
            .mapNotNull { record ->
                val file = storage.locate(record) ?: return@mapNotNull null
                read(
                    databases.open(file),
                    SearchSource.Translation(TranslationId(record.entry.id)),
                    // A passage shared by several ayahs is stored once per ayah; only its first
                    // ayah is searched so one passage is not listed several times.
                    "SELECT surah, ayah, text FROM translation WHERE ayah = ayah_from",
                )
            }

    private fun read(database: SQLiteDatabase, source: SearchSource, sql: String): SearchableTexts {
        val ayahs = ArrayList<AyahRef>(AYAH_COUNT)
        val texts = ArrayList<String>(AYAH_COUNT)
        database.rawQuery("$sql ORDER BY surah, ayah", null).use { cursor ->
            while (cursor.moveToNext()) {
                ayahs += AyahRef(cursor.getInt(0), cursor.getInt(1))
                texts += cursor.getString(2)
            }
        }
        return SearchableTexts(source, ayahs, texts)
    }

    private companion object {
        const val AYAH_PACK_KIND = "ayah"
        const val AYAH_COUNT = 6236
    }
}
