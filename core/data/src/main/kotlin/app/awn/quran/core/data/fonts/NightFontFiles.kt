package app.awn.quran.core.data.fonts

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the night copies of colour page fonts on disk. A copy is the font with [CpalNightPatch]
 * applied, written next to the original in a [NIGHT_DIRECTORY_NAME] folder, so removing the font
 * pack removes the copies with it.
 */
object NightFontFiles {
    const val NIGHT_DIRECTORY_NAME = "night"

    /** Sources found to have no second palette, with the modified time they had then. */
    private val plainSources = ConcurrentHashMap<String, Long>()
    private val writeLock = Any()

    /**
     * Returns the font file to load at night for [source]: its night copy, written now if it is
     * missing or older than [source], or [source] itself when the font has no night palette.
     *
     * @throws IOException when the copy cannot be written.
     */
    @Suppress("ReturnCount") // Each exit is a distinct case: plain font, current copy, new copy.
    fun nightFileFor(source: File): File {
        if (plainSources[source.path] == source.lastModified()) return source
        val nightFile = File(File(source.parentFile, NIGHT_DIRECTORY_NAME), source.name)
        if (isCurrent(nightFile, source)) return nightFile

        synchronized(writeLock) {
            // Another thread may have written it while this one waited for the lock.
            if (isCurrent(nightFile, source)) return nightFile
            val patched = CpalNightPatch.patched(source.readBytes())
            if (patched == null) {
                plainSources[source.path] = source.lastModified()
                return source
            }
            writeAtomically(nightFile, patched)
            return nightFile
        }
    }

    private fun isCurrent(nightFile: File, source: File): Boolean =
        nightFile.isFile && nightFile.lastModified() >= source.lastModified()

    /** Writes to a temporary file and renames it, so a reader never sees half a font. */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val directory = checkNotNull(target.parentFile)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create ${directory.path}.")
        }
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Could not move the night font into ${target.path}.")
        }
    }
}
