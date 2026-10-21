package app.awn.quran.feature.read.page

import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.downloads.CatalogueModel
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.core.data.downloads.PackTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The page types a reader can choose, in the order the Mushaf settings page shows them: the 1421
 * print first because it is the default.
 */
val pageTypeScripts: List<ScriptId> = listOf(ScriptId.V2, ScriptId.V1, ScriptId.V4Tajweed)

/**
 * The state of the font of one page type. [downloadBytes] is the size to download and
 * [installedBytes] the space taken on the phone (null until installed); both are null while the
 * manifest is still being read. [progress] is 0 to 1 while a download is queued or runs and
 * otherwise null. [failure] says why the last download failed, with the numbers for a full phone.
 */
data class FontPackStatus(
    val script: ScriptId,
    val isInstalled: Boolean,
    val downloadBytes: Long? = null,
    val installedBytes: Long? = null,
    val progress: Float? = null,
    val failure: PackTransfer.Failed? = null,
) {
    val failed: Boolean
        get() = failure != null
}

/**
 * Tracks, installs and removes the page fonts through the download catalogue. An install runs in
 * the background, so a font keeps downloading when the user leaves the app. The installed list is
 * read again after every change, so a page that was drawn in the fallback font switches to its own
 * font as soon as the download ends.
 */
class FontPackController(
    packs: PackRepository,
    downloads: PackDownloads,
    scope: CoroutineScope,
) {
    private val catalogue = CatalogueModel(packs, downloads, scope)

    /** The status of each page type; the fallback Hafs font is bundled and not listed. */
    val statuses: StateFlow<Map<ScriptId, FontPackStatus>> =
        catalogue.state
            .map { state ->
                pageTypeScripts.associateWith { script ->
                    statusOf(script, state.rowFor(script.packId))
                }
            }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                pageTypeScripts.associateWith { FontPackStatus(it, isInstalled = false) },
            )

    /** Downloads and installs the font of [script]; progress and failure show in [statuses]. */
    fun install(script: ScriptId) = catalogue.install(script.packId)

    /** Stops the running or queued download of the font of [script]. */
    fun cancel(script: ScriptId) = catalogue.cancel(script.packId)

    /** Deletes the font of [script]. */
    fun remove(script: ScriptId) = catalogue.remove(script.packId)

    private fun statusOf(script: ScriptId, row: CatalogueRow?): FontPackStatus {
        if (row == null) return FontPackStatus(script, isInstalled = false)
        val item = row.item
        return FontPackStatus(
            script = script,
            isInstalled = item.isInstalled,
            downloadBytes = item.downloadBytes,
            installedBytes = if (item.isInstalled) item.installedBytes else null,
            progress = progressOf(row.transfer),
            failure = row.transfer as? PackTransfer.Failed,
        )
    }

    private fun progressOf(transfer: PackTransfer?): Float? =
        when (transfer) {
            PackTransfer.Waiting -> 0f
            is PackTransfer.Running -> transfer.progress
            is PackTransfer.Failed,
            null -> null
        }
}
