package app.awn.quran.core.data

import android.content.Context
import app.awn.quran.core.data.bookmarks.RoomBookmarkRepository
import app.awn.quran.core.data.database.AwnDatabase
import app.awn.quran.core.data.packs.AndroidBundledAssets
import app.awn.quran.core.data.packs.FilePackRepository
import app.awn.quran.core.data.packs.ManifestCatalog
import app.awn.quran.core.data.packs.PackStorage
import app.awn.quran.core.data.packs.ReadOnlyDatabases
import app.awn.quran.core.data.quran.SqliteQuranTextRepository
import app.awn.quran.core.data.reciters.PackReciterRepository
import app.awn.quran.core.data.search.SearchRepository
import app.awn.quran.core.data.search.SqliteSearchTexts
import app.awn.quran.core.data.search.TextSearch
import app.awn.quran.core.data.translations.SqliteTranslationRepository

/**
 * The real repositories, wired together. Create one instance per process; the app module holds it
 * and hands the interfaces to the features.
 */
class CoreData(context: Context) {

    private val applicationContext = context.applicationContext
    private val catalog = ManifestCatalog(AndroidBundledAssets(applicationContext.assets))
    private val storage =
        PackStorage(
            applicationContext.filesDir,
            AndroidBundledAssets(applicationContext.assets),
            catalog,
        )
    private val databases = ReadOnlyDatabases()

    val quranText: QuranTextRepository =
        SqliteQuranTextRepository(
            databaseFile = {
                val record =
                    requireNotNull(catalog.findPack(QURAN_TEXT_PACK_ID)) {
                        "scripts.json has no '$QURAN_TEXT_PACK_ID' entry."
                    }
                checkNotNull(storage.locate(record)) {
                    "The Quran text pack is missing; the build did not bundle quran.sqlite."
                }
            },
        )

    val translations: TranslationRepository =
        SqliteTranslationRepository(catalog, storage, databases)

    /** Searches the Arabic text and the installed translations; reads them on the first query. */
    val search: SearchRepository =
        TextSearch(
            loadTexts =
                SqliteSearchTexts(
                    quranDatabaseFile = { checkNotNull(storage.locate(quranTextRecord())) },
                    catalog = catalog,
                    storage = storage,
                    databases = databases,
                )::load,
        )

    private fun quranTextRecord() =
        requireNotNull(catalog.findPack(QURAN_TEXT_PACK_ID)) {
            "scripts.json has no '$QURAN_TEXT_PACK_ID' entry."
        }

    val reciters: ReciterRepository = PackReciterRepository(catalog, storage, databases)

    val packs: PackRepository = FilePackRepository(catalog, storage, databases)

    val bookmarks: BookmarkRepository by lazy {
        RoomBookmarkRepository(AwnDatabase.open(applicationContext))
    }

    private companion object {
        const val QURAN_TEXT_PACK_ID = "quran-text"
    }
}
