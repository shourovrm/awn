package app.awn.quran.core.data.fakes

import app.awn.quran.core.data.InstalledPack
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.PackSize
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.packs.PackEntry
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory packs for tests. [install] records the pack without downloading and reports
 * progress 1. [fontFiles] decides what [fontFile] returns; scripts without an entry have no font
 * installed.
 */
class FakePackRepository(
    val manifests: MutableMap<String, List<PackEntry>> = mutableMapOf(),
    val fontFiles: MutableMap<ScriptId, File> = mutableMapOf(),
    val sizes: MutableMap<String, PackSize> = mutableMapOf(),
) : PackRepository {

    private val installedState = MutableStateFlow<List<InstalledPack>>(emptyList())

    override fun installed(): Flow<List<InstalledPack>> = installedState

    override suspend fun manifest(kind: String): List<PackEntry> = manifests[kind].orEmpty()

    override suspend fun sizeOf(id: String): PackSize? = sizes[id]

    override suspend fun install(entry: PackEntry, onProgress: (Float) -> Unit) {
        onProgress(1f)
        val installedPack =
            InstalledPack(entry.id, entry.kind.name.lowercase(), entry.formatVersion, 0L)
        installedState.value = installedState.value.filterNot { it.id == entry.id } + installedPack
    }

    override suspend fun remove(id: String) {
        installedState.value = installedState.value.filterNot { it.id == id }
    }

    override fun fontFile(script: ScriptId, page: Int): File? = fontFiles[script]
}
