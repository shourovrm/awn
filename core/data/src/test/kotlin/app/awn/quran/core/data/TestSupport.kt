package app.awn.quran.core.data

import android.database.sqlite.SQLiteDatabase
import app.awn.quran.core.data.packs.BundledAssets
import app.awn.quran.core.data.packs.ManifestCatalog
import app.awn.quran.core.data.packs.PackDownloader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/** The files that ship in the APK; `:core:data:fetchBundledPacks` leaves them here. */
val realBundledPacksDirectory = File("src/main/assets/packs")

/** The translations and word packs the APK no longer holds; `:core:data:fetchTestPacks`. */
val realDownloadedPacksDirectory = File("build/test-packs")

/**
 * Serves bundled files from folders, the way the APK assets folder does; the first folder that
 * holds a file wins.
 */
class DirectoryBundledAssets(private vararg val directories: File) : BundledAssets {

    private fun find(fileName: String): File? =
        directories.map { File(it, fileName) }.firstOrNull { it.isFile }

    override fun exists(fileName: String): Boolean = find(fileName) != null

    override fun open(fileName: String): InputStream {
        val file =
            find(fileName)
                ?: throw FileNotFoundException(
                    "No bundled test file $fileName in ${directories.joinToString { it.path }}.",
                )
        return file.inputStream()
    }
}

/**
 * The real manifests, text, font and every real pack, served as if all of them shipped in the app.
 * That keeps the tests that read real packs independent of what the APK really holds.
 */
fun realPackAssets(): BundledAssets =
    DirectoryBundledAssets(realBundledPacksDirectory, realDownloadedPacksDirectory)

/** Serves bundled files from memory. */
class MapBundledAssets(private val files: Map<String, ByteArray>) : BundledAssets {

    override fun exists(fileName: String): Boolean = fileName in files

    override fun open(fileName: String): InputStream =
        files[fileName]?.inputStream()
            ?: throw FileNotFoundException("No bundled test file $fileName.")
}

fun realManifestCatalog(): ManifestCatalog = ManifestCatalog(realPackAssets())

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
