package app.awn.quran.core.data

import app.awn.quran.core.data.packs.PackEntry
import java.io.File
import kotlinx.coroutines.flow.Flow

/** [kind] is one of script, font, translation, timing, model. */
data class InstalledPack(val id: String, val kind: String, val formatVersion: Int, val bytes: Long)

/**
 * What a pack costs in storage. [downloadBytes] is the file to download; [installedBytes] is the
 * space it takes on the phone now, or null when it is not installed.
 */
data class PackSize(val downloadBytes: Long, val installedBytes: Long?)

interface PackRepository {

    /** Emits the installed packs, again after every install or removal. */
    fun installed(): Flow<List<InstalledPack>>

    /**
     * Returns the entries of the data release manifest for [kind] (scripts.json, translations.json
     * or reciters.json); empty when the manifest could not be read.
     */
    suspend fun manifest(kind: String): List<PackEntry>

    /** Returns the sizes of pack [id], or null when no manifest lists it. */
    suspend fun sizeOf(id: String): PackSize?

    /** Downloads, verifies and installs [entry], reporting progress from 0.0 to 1.0. */
    suspend fun install(entry: PackEntry, onProgress: (Float) -> Unit)

    suspend fun remove(id: String)

    /**
     * Returns the font file that draws [script] on [page], or null when the font pack is not
     * installed. [page] is ignored for scripts with a single font.
     */
    fun fontFile(script: ScriptId, page: Int): File?
}
