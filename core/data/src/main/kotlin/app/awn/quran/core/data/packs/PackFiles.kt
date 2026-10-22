package app.awn.quran.core.data.packs

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File
import java.util.zip.ZipInputStream

/** Checks that run on a downloaded file before it is installed. */
internal object PackFiles {

    private val FONT_MEMBER_NAME = Regex("p[0-9]{1,3}\\.ttf")

    /**
     * A clip is `<unit>/<number>.opus`; the pattern leaves no room for `..` or an absolute path.
     */
    private val AUDIO_MEMBER_NAME = Regex("[a-z0-9-]+/[a-z0-9-]+\\.opus")

    /**
     * Returns the `format_version` row of a SQLite pack's `pack_info` table. Throws
     * [PackInstallException] when the file is not a pack database.
     */
    @Suppress("NestedBlockDepth") // Three nested resource scopes: database, cursor, parse.
    fun databaseFormatVersion(file: File, displayName: String): Int =
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use {
                database ->
                database
                    .rawQuery("SELECT value FROM pack_info WHERE key = 'format_version'", null)
                    .use { cursor ->
                        if (!cursor.moveToFirst()) {
                            throw PackInstallException(
                                displayName,
                                "pack_info has no format_version",
                            )
                        }
                        cursor.getString(0).toInt()
                    }
            }
        } catch (error: SQLiteException) {
            throw PackInstallException(displayName, "not a readable pack database", error)
        } catch (error: NumberFormatException) {
            throw PackInstallException(displayName, "format_version is not a number", error)
        }

    /**
     * Extracts the `pN.ttf` members of [archive] into [targetDirectory], which must not exist yet,
     * and returns how many files were written. Any other member name is rejected, which also rules
     * out path traversal.
     */
    @Suppress("LoopWithTooManyJumpStatements") // End of archive and directory entries.
    fun extractFontArchive(archive: File, targetDirectory: File, displayName: String): Int {
        check(targetDirectory.mkdirs()) { "Could not create ${targetDirectory.path}." }
        var extractedCount = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val member = zip.nextEntry ?: break
                if (member.isDirectory) continue
                if (!FONT_MEMBER_NAME.matches(member.name)) {
                    throw PackInstallException(
                        displayName,
                        "unexpected member '${member.name}' in the font archive",
                    )
                }
                File(targetDirectory, member.name).outputStream().use { zip.copyTo(it) }
                extractedCount += 1
            }
        }
        return extractedCount
    }

    /**
     * Extracts the `<unit>/<clip>.opus` members of [archive] into [targetDirectory], which must not
     * exist yet, and returns how many files were written. Any other member name is rejected, which
     * also rules out path traversal.
     */
    @Suppress("LoopWithTooManyJumpStatements") // End of archive and directory entries.
    fun extractAudioArchive(archive: File, targetDirectory: File, displayName: String): Int {
        check(targetDirectory.mkdirs()) { "Could not create ${targetDirectory.path}." }
        var extractedCount = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val member = zip.nextEntry ?: break
                if (member.isDirectory) continue
                if (!AUDIO_MEMBER_NAME.matches(member.name)) {
                    throw PackInstallException(
                        displayName,
                        "unexpected member '${member.name}' in the sound archive",
                    )
                }
                val clipFile = File(targetDirectory, member.name)
                clipFile.parentFile?.mkdirs()
                clipFile.outputStream().use { zip.copyTo(it) }
                extractedCount += 1
            }
        }
        return extractedCount
    }
}
