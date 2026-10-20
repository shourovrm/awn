package app.awn.quran.feature.read.page

import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ScriptId
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The page types a reader can choose, in the order the Mushaf settings page shows them: the 1421
 * print first because it is the default.
 */
val pageTypeScripts: List<ScriptId> = listOf(ScriptId.V2, ScriptId.V1, ScriptId.V4Tajweed)

/**
 * The state of the font pack of one page type. [downloadBytes] is the size to download and
 * [installedBytes] the space taken on the phone (null until installed); both are null while the
 * manifest is still being read. [progress] is 0 to 1 while a download runs and otherwise null.
 */
data class FontPackStatus(
    val script: ScriptId,
    val isInstalled: Boolean,
    val downloadBytes: Long? = null,
    val installedBytes: Long? = null,
    val progress: Float? = null,
    val failed: Boolean = false,
)

/**
 * Tracks, installs and removes the page font packs. It reads the installed list again after every
 * change, so a page that was drawn in the fallback font switches to its own font as soon as the
 * download ends.
 */
class FontPackController(
    private val packs: PackRepository,
    private val scope: CoroutineScope,
) {
    private val mutableStatuses =
        MutableStateFlow(pageTypeScripts.associateWith { FontPackStatus(it, isInstalled = false) })

    /** The status of each page type; the fallback Hafs font is bundled and not listed. */
    val statuses: StateFlow<Map<ScriptId, FontPackStatus>> = mutableStatuses

    init {
        scope.launch { packs.installed().collect { refreshInstalled(it.map { pack -> pack.id }) } }
    }

    /**
     * Downloads and installs the font pack of [script]; progress and failure show in [statuses].
     */
    fun install(script: ScriptId) {
        if (mutableStatuses.value[script]?.progress != null) return
        scope.launch {
            val entry = packs.manifest(MANIFEST_SCRIPTS).firstOrNull { it.id == script.packId }
            if (entry == null) {
                change(script) { it.copy(failed = true) }
                return@launch
            }
            change(script) { it.copy(progress = 0f, failed = false) }
            try {
                packs.install(entry) { progress -> change(script) { it.copy(progress = progress) } }
                change(script) { it.copy(progress = null) }
            } catch (_: IOException) {
                // A dropped connection or a failed check shows as a failed state; the user retries.
                change(script) { it.copy(progress = null, failed = true) }
            }
        }
    }

    /** Deletes the font pack of [script]. */
    fun remove(script: ScriptId) {
        scope.launch {
            try {
                packs.remove(script.packId)
            } catch (_: IOException) {
                change(script) { it.copy(failed = true) }
            }
        }
    }

    private suspend fun refreshInstalled(installedIds: List<String>) {
        for (script in pageTypeScripts) {
            val size = packs.sizeOf(script.packId)
            val isInstalled = script.packId in installedIds
            change(script) {
                it.copy(
                    isInstalled = isInstalled,
                    downloadBytes = size?.downloadBytes,
                    installedBytes = if (isInstalled) size?.installedBytes else null,
                )
            }
        }
    }

    private fun change(script: ScriptId, transform: (FontPackStatus) -> FontPackStatus) {
        mutableStatuses.update { statuses ->
            val current = statuses.getValue(script)
            statuses + (script to transform(current))
        }
    }

    private companion object {
        const val MANIFEST_SCRIPTS = "scripts"
    }
}
