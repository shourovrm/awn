package app.awn.quran.core.data.packs

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Decides where each pack lives under `filesDir/packs` and copies bundled packs there on first use.
 *
 * Layout: a database pack is `packs/<file>.sqlite`; a single font is `packs/fonts/<id>/<file>`; a
 * font archive is extracted to `packs/fonts/<id>/pN.ttf`; unfinished downloads sit in
 * `packs/.downloads/`.
 */
class PackStorage(
    filesDirectory: File,
    private val assets: BundledAssets,
    private val catalog: ManifestCatalog,
) {

    val root = File(filesDirectory, "packs")
    val downloadsDirectory = File(root, ".downloads")

    private val bundledNames: Set<String> by lazy {
        catalog.allPacks.map { it.fileName }.filter { assets.exists(it) }.toSet()
    }

    fun isBundled(record: PackRecord): Boolean = record.fileName in bundledNames

    /** Returns where [record] is or will be installed; the file or folder may not exist. */
    fun locationOf(record: PackRecord): File =
        when (record.layout) {
            PackFileLayout.Database -> File(root, record.fileName)
            PackFileLayout.SingleFont -> File(fontsDirectory(record.entry.id), record.fileName)
            PackFileLayout.FontArchive -> fontsDirectory(record.entry.id)
        }

    fun fontsDirectory(packId: String): File = File(File(root, "fonts"), packId)

    /**
     * Returns the installed pack file or folder, copying a bundled pack out of the APK first when
     * needed, or null when [record] is neither installed nor bundled. Blocks on disk IO.
     */
    @Synchronized
    fun locate(record: PackRecord): File? {
        if (isBundled(record)) copyBundledIfNeeded(record)
        val location = locationOf(record)
        val present =
            when (record.layout) {
                PackFileLayout.FontArchive ->
                    location.isDirectory && !location.list().isNullOrEmpty()
                else -> location.isFile
            }
        return if (present) location else null
    }

    fun isInstalled(record: PackRecord): Boolean =
        if (isBundled(record)) true
        else locationOf(record).let { it.exists() && sizeOnDisk(it) > 0 }

    fun sizeOnDisk(location: File): Long =
        if (location.isDirectory) {
            location.listFiles().orEmpty().sumOf { it.length() }
        } else {
            location.length()
        }

    /** Deletes the installed copy of [record] and any unfinished download of it. */
    @Synchronized
    fun delete(record: PackRecord) {
        val location = locationOf(record)
        if (!location.deleteRecursively()) {
            throw IOException("Could not delete ${location.path}.")
        }
        File(downloadsDirectory, "${record.fileName}.part").delete()
    }

    private fun copyBundledIfNeeded(record: PackRecord) {
        val destination = locationOf(record)
        if (destination.isFile && destination.length() == record.bytes) return
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.copy")
        assets.open(record.fileName).use { input ->
            Files.copy(input, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/** Keeps one read-only SQLite handle per pack file, so each query does not reopen the file. */
class ReadOnlyDatabases {

    private val openDatabases = mutableMapOf<String, SQLiteDatabase>()

    @Synchronized
    fun open(file: File): SQLiteDatabase =
        openDatabases.getOrPut(file.path) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        }

    /** Closes the handle for [file], if any; call before deleting or replacing the file. */
    @Synchronized
    fun close(file: File) {
        openDatabases.remove(file.path)?.close()
    }
}
