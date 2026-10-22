package app.awn.quran.core.data.fakes

import app.awn.quran.core.data.CatalogueItem
import app.awn.quran.core.data.InstalledPack
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.PackSize
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.packs.PackEntry
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory packs for tests. [install] records the pack without downloading and reports
 * progress 1. [fontFiles] decides what [fontFile] returns; scripts without an entry have no font
 * installed.
 */
class FakePackRepository(
    val manifests: MutableMap<String, List<PackEntry>> = mutableMapOf(),
    val fontFiles: MutableMap<ScriptId, File> = mutableMapOf(),
    val sizes: MutableMap<String, PackSize> = mutableMapOf(),
    /** The rows [catalogue] returns; [install] and [remove] flip their installed state. */
    val catalogueItems: MutableList<CatalogueItem> = mutableListOf(),
    var freeBytesValue: Long = Long.MAX_VALUE,
    /** The files [learnAudioFile] returns, by clip id. */
    val learnAudioFiles: MutableMap<String, File> = mutableMapOf(),
    /** When set, [install] throws it instead of installing. */
    var installFailure: Throwable? = null,
) : PackRepository {

    private var installedPacks = emptyList<InstalledPack>()

    // Counts every install and removal, so collectors run again even when the list is equal.
    private val changeCounter = MutableStateFlow(0)

    override fun installed(): Flow<List<InstalledPack>> = changeCounter.map { installedPacks }

    override suspend fun manifest(kind: String): List<PackEntry> = manifests[kind].orEmpty()

    override suspend fun sizeOf(id: String): PackSize? = sizes[id]

    override suspend fun catalogue(): List<CatalogueItem> = catalogueItems.toList()

    override fun freeBytes(): Long = freeBytesValue

    override fun learnAudioFile(clipId: String): File? = learnAudioFiles[clipId]

    override suspend fun install(entry: PackEntry, onProgress: (Float) -> Unit) {
        installFailure?.let { throw it }
        onProgress(1f)
        updateCatalogue(entry.id) {
            it.copy(
                isInstalled = true,
                hasUpdate = false,
                installedBytes = it.installedBytes ?: it.downloadBytes,
            )
        }
        val installedPack =
            InstalledPack(entry.id, entry.kind.name.lowercase(), entry.formatVersion, 0L)
        installedPacks = installedPacks.filterNot { it.id == entry.id } + installedPack
        changeCounter.value++
    }

    override suspend fun remove(id: String) {
        updateCatalogue(id) { it.copy(isInstalled = false, installedBytes = null) }
        installedPacks = installedPacks.filterNot { it.id == id }
        changeCounter.value++
    }

    private fun updateCatalogue(id: String, change: (CatalogueItem) -> CatalogueItem) {
        val index = catalogueItems.indexOfFirst { it.id == id }
        if (index >= 0) catalogueItems[index] = change(catalogueItems[index])
    }

    override fun fontFile(script: ScriptId, page: Int): File? = fontFiles[script]
}
