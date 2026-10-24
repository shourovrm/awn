package app.awn.quran.core.data.translations

import android.database.sqlite.SQLiteDatabase
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationInfo
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.TranslationText
import app.awn.quran.core.data.packs.ManifestCatalog
import app.awn.quran.core.data.packs.PackRecord
import app.awn.quran.core.data.packs.PackStorage
import app.awn.quran.core.data.packs.ReadOnlyDatabases
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads ayah translations and word-by-word meanings from the SQLite packs (docs/packs.md). */
class SqliteTranslationRepository(
    private val catalog: ManifestCatalog,
    private val storage: PackStorage,
    private val databases: ReadOnlyDatabases,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TranslationRepository {

    override suspend fun available(): List<TranslationInfo> =
        withContext(ioDispatcher) {
            catalog.translationPacks
                .filter { it.translationKind in AYAH_TEXT_KINDS }
                .map { record ->
                    TranslationInfo(
                        id = TranslationId(record.entry.id),
                        name = record.displayName,
                        language = record.language.orEmpty(),
                        installed = storage.isInstalled(record),
                    )
                }
        }

    override suspend fun text(id: TranslationId, ayah: AyahRef): TranslationText? =
        withContext(ioDispatcher) {
            val record = packOf(id.id, AYAH_TEXT_KINDS) ?: return@withContext null
            val file = storage.locate(record) ?: return@withContext null
            val cursor =
                databases
                    .open(file)
                    .rawQuery(
                        "SELECT ayah_from, ayah_to, text FROM translation " +
                            "WHERE surah = ? AND ayah = ?",
                        arrayOf(ayah.surah.toString(), ayah.ayah.toString()),
                    )
            cursor.use {
                if (it.moveToFirst()) {
                    TranslationText(ayah, it.getInt(0), it.getInt(1), it.getString(2))
                } else {
                    null
                }
            }
        }

    override suspend fun wordByWord(language: String, ayah: AyahRef): List<String> =
        withContext(ioDispatcher) {
            val record =
                packOf("wbw_$language", setOf(WORD_PACK_KIND)) ?: return@withContext emptyList()
            val file = storage.locate(record) ?: return@withContext emptyList()
            val cursor =
                databases
                    .open(file)
                    .rawQuery(
                        "SELECT text FROM word_translation WHERE surah = ? AND ayah = ? " +
                            "ORDER BY position",
                        arrayOf(ayah.surah.toString(), ayah.ayah.toString()),
                    )
            val meanings = cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        add(it.getString(0))
                    }
                }
            }
            // Only the English source repeats a phrase on each of its words.
            if (language == ENGLISH) PhraseRepeats.collapse(ayah, meanings) else meanings
        }

    override suspend fun wordByWord(
        language: String,
        ayahs: List<AyahRef>,
    ): Map<AyahRef, List<String>> =
        withContext(ioDispatcher) {
            val record = packOf("wbw_$language", setOf(WORD_PACK_KIND))
            val file = record?.let { storage.locate(it) }
            val meaningsByAyah = mutableMapOf<AyahRef, List<String>>()
            if (file != null) {
                val database = databases.open(file)
                ayahs.distinct().chunked(AYAHS_PER_QUERY).forEach { chunk ->
                    meaningsByAyah.putAll(readWordMeanings(database, chunk))
                }
            }
            ayahs.associateWith { ayah ->
                val meanings = meaningsByAyah[ayah].orEmpty()
                // Only the English source repeats a phrase on each of its words.
                if (language == ENGLISH) PhraseRepeats.collapse(ayah, meanings) else meanings
            }
        }

    /** One query for [ayahs]; the rows come ordered, so each ayah's meanings are in word order. */
    private fun readWordMeanings(
        database: SQLiteDatabase,
        ayahs: List<AyahRef>,
    ): Map<AyahRef, List<String>> {
        val placeholders = ayahs.joinToString(",") { "(?, ?)" }
        val arguments = ayahs.flatMap { listOf(it.surah.toString(), it.ayah.toString()) }
        val meaningsByAyah = mutableMapOf<AyahRef, MutableList<String>>()
        database
            .rawQuery(
                "SELECT surah, ayah, text FROM word_translation " +
                    "WHERE (surah, ayah) IN (VALUES $placeholders) ORDER BY surah, ayah, position",
                arguments.toTypedArray(),
            )
            .use {
                while (it.moveToNext()) {
                    val ayah = AyahRef(it.getInt(0), it.getInt(1))
                    meaningsByAyah.getOrPut(ayah) { mutableListOf() }.add(it.getString(2))
                }
            }
        return meaningsByAyah
    }

    override suspend fun wordTransliteration(ayah: AyahRef): List<String> =
        withContext(ioDispatcher) {
            val record =
                packOf(TRANSLITERATION_WORDS_ID, setOf(TRANSLITERATION_WORDS_KIND))
                    ?: return@withContext emptyList()
            val file = storage.locate(record) ?: return@withContext emptyList()
            val cursor =
                databases
                    .open(file)
                    .rawQuery(
                        "SELECT text FROM word_translation WHERE surah = ? AND ayah = ? " +
                            "ORDER BY position",
                        arrayOf(ayah.surah.toString(), ayah.ayah.toString()),
                    )
            cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        add(it.getString(0))
                    }
                }
            }
        }

    private fun packOf(id: String, translationKinds: Set<String>): PackRecord? =
        catalog.translationPacks.firstOrNull {
            it.entry.id == id && it.translationKind in translationKinds
        }

    private companion object {
        /**
         * Packs with one passage per ayah. Catalogue v2 marks transliterations with their own kind;
         * their table is the same as a translation's.
         */
        val AYAH_TEXT_KINDS = setOf("ayah", "transliteration")
        const val WORD_PACK_KIND = "word"

        /** Two bound values per ayah, well under the 999 variables older SQLite builds allow. */
        const val AYAHS_PER_QUERY = 400

        /** The transliteration of each word, which used to be a column of `quran.sqlite`. */
        const val TRANSLITERATION_WORDS_ID = "en_transliteration_words"
        const val TRANSLITERATION_WORDS_KIND = "word_transliteration"
        const val ENGLISH = "en"
    }
}
