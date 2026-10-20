package app.awn.quran.core.data.downloads

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.CatalogueItem
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.core.data.packs.PackSpace
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One row of the catalogue: the item, and what is happening to it right now. [transfer] is null
 * when nothing is queued, running or failed.
 */
data class CatalogueRow(val item: CatalogueItem, val transfer: PackTransfer?) {

    /** True while the item is queued or downloading. */
    val isBusy: Boolean
        get() = transfer is PackTransfer.Waiting || transfer is PackTransfer.Running
}

/** What the user can do with a catalogue row right now. */
enum class ItemAction {
    Install,
    Update,
    Cancel,
    Remove,
}

/**
 * Returns the actions of [row] in the order the screen offers them. A queued or running download
 * can only be cancelled; an item that ships inside the app can only be updated, never removed.
 */
fun actionsOf(row: CatalogueRow): List<ItemAction> =
    when {
        row.isBusy -> listOf(ItemAction.Cancel)
        !row.item.isInstalled -> listOf(ItemAction.Install)
        else ->
            buildList {
                if (row.item.hasUpdate) add(ItemAction.Update)
                if (row.item.canRemove) add(ItemAction.Remove)
            }
    }

/** Everything the catalogue screens draw. [freeBytes] is the space left on the phone. */
data class CatalogueState(
    val rows: List<CatalogueRow> = emptyList(),
    val freeBytes: Long = 0L,
    val isLoaded: Boolean = false,
) {
    /** The space the installed items take, bundled ones included: they are copied to the phone. */
    val spaceUsedBytes: Long
        get() = rows.sumOf { it.item.installedBytes ?: 0L }

    fun rowsIn(group: CatalogueGroup): List<CatalogueRow> = rows.filter { it.item.group == group }

    fun rowFor(id: String): CatalogueRow? = rows.firstOrNull { it.item.id == id }
}

/**
 * The state and the actions of the download catalogue, shared by the Reading settings and the
 * Downloads page. It joins the items the manifests list with the running transfers, checks the free
 * space before it asks [downloads] to start, and keeps a failure that happened before any download
 * started (not enough space) so the row can show the numbers.
 */
class CatalogueModel(
    private val packs: PackRepository,
    private val downloads: PackDownloads,
    private val scope: CoroutineScope,
) {
    private val startProblems = MutableStateFlow<Map<String, PackTransfer.Failed>>(emptyMap())

    // Ids asked for whose work item WorkManager has not reported yet; they show as waiting so the
    // row does not flick back to its download button for a moment.
    private val pendingIds = MutableStateFlow<Set<String>>(emptySet())

    /** The items, read again after every install and removal. */
    private val items = MutableStateFlow<List<CatalogueItem>>(emptyList())
    private val isLoaded = MutableStateFlow(false)

    val state: StateFlow<CatalogueState> =
        combine(
                items,
                downloads.transfers.onStart { emit(emptyMap()) },
                startProblems,
                pendingIds,
                isLoaded,
            ) { catalogueItems, transfers, problems, pending, loaded ->
                CatalogueState(
                    rows =
                        catalogueItems.map { item ->
                            val isDone = item.isInstalled && !item.hasUpdate
                            val waiting =
                                PackTransfer.Waiting.takeIf { item.id in pending && !isDone }
                            CatalogueRow(item, transfers[item.id] ?: problems[item.id] ?: waiting)
                        },
                    freeBytes = packs.freeBytes(),
                    isLoaded = loaded,
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, CatalogueState())

    init {
        scope.launch {
            packs
                .installed()
                .map { packs.catalogue() }
                .collect {
                    items.value = it
                    isLoaded.value = true
                }
        }
    }

    /** Starts installing, or updating, [id]; reports a start problem when the space is short. */
    fun install(id: String) {
        val item = items.value.firstOrNull { it.id == id } ?: return
        val free = packs.freeBytes()
        if (PackSpace.shortfall(item.requiredFreeBytes, free) > 0) {
            startProblems.update {
                it +
                    (id to
                        PackTransfer.Failed(
                            PackFailureReason.NoSpace,
                            item.requiredFreeBytes,
                            free,
                        ))
            }
            return
        }
        startProblems.update { it - id }
        pendingIds.update { it + id }
        downloads.request(id)
    }

    /** Stops a running or queued install of [id]. */
    fun cancel(id: String) {
        startProblems.update { it - id }
        pendingIds.update { it - id }
        downloads.cancel(id)
    }

    /** Deletes the installed copy of [id]; bundled items cannot be removed. */
    fun remove(id: String) {
        val item = items.value.firstOrNull { it.id == id } ?: return
        if (!item.canRemove) return
        scope.launch {
            try {
                downloads.cancel(id)
                pendingIds.update { it - id }
                packs.remove(id)
                startProblems.update { it - id }
            } catch (error: IOException) {
                // The files could not be deleted; the row shows a plain failure.
                startProblems.update {
                    it + (id to PackTransfer.Failed(PackFailureReason.of(error)))
                }
            }
        }
    }
}
