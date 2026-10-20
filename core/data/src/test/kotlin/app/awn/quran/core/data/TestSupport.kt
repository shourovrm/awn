package app.awn.quran.core.data

import android.database.sqlite.SQLiteDatabase
import app.awn.quran.core.data.packs.BundledAssets
import app.awn.quran.core.data.packs.ManifestCatalog
import app.awn.quran.core.data.packs.PackDownloader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/** The packs that `:core:data:fetchBundledPacks` leaves in the module's assets folder. */
val realBundledPacksDirectory = File("src/main/assets/packs")

/** Serves bundled files from a folder, the way the APK assets folder does. */
class DirectoryBundledAssets(private val directory: File) : BundledAssets {

    override fun exists(fileName: String): Boolean = File(directory, fileName).isFile

    override fun open(fileName: String): InputStream {
        val file = File(directory, fileName)
        if (!file.isFile) throw FileNotFoundException("No bundled test file ${file.path}.")
        return file.inputStream()
    }
}

/** Serves bundled files from memory. */
class MapBundledAssets(private val files: Map<String, ByteArray>) : BundledAssets {

    override fun exists(fileName: String): Boolean = fileName in files

    override fun open(fileName: String): InputStream =
        files[fileName]?.inputStream()
            ?: throw FileNotFoundException("No bundled test file $fileName.")
}

fun realManifestCatalog(): ManifestCatalog =
    ManifestCatalog(DirectoryBundledAssets(realBundledPacksDirectory))

/** Creates a pack database with a `pack_info` table and runs [statements] on it. */
fun createPackDatabase(file: File, formatVersion: Int, vararg statements: String) {
    file.parentFile?.mkdirs()
    val database = SQLiteDatabase.openOrCreateDatabase(file, null)
    try {
        database.execSQL("CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        database.execSQL("INSERT INTO pack_info VALUES ('format_version', '$formatVersion')")
        statements.forEach(database::execSQL)
    } finally {
        database.close()
    }
}

fun sha256Of(file: File): String = PackDownloader.sha256Of(file)

@Suppress("LongParameterList") // Mirrors the manifest fields.
/** One manifest entry for a synthetic pack file; [extraFields] are raw JSON members. */
fun manifestEntry(
    id: String,
    kind: String,
    file: File,
    relativePath: String,
    formatVersion: Int = 1,
    extraFields: String = "",
): String =
    """{"format_version":$formatVersion,"file":"$relativePath","sha256":"${sha256Of(file)}",
      |"bytes":${file.length()},"id":"$id","kind":"$kind","name":"Name of $id",
      |"credit":"Credit for $id","source_url":"https://example.org/$id"$extraFields}"""
        .trimMargin()
