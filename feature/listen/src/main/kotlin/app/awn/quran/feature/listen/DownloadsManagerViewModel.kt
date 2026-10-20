package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.player.AudioDownloadRunner
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.AudioStatus
import app.awn.quran.player.DirectDownloadRunner
import app.awn.quran.player.DownloadPlan
import app.awn.quran.player.DownloadProblem
import app.awn.quran.player.DownloadProgress
import app.awn.quran.player.NetworkStatus
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A set of ayahs that can be downloaded or deleted as one item of the manager. */
sealed interface ItemScope {
    data class Surah(val number: Int) : ItemScope

    data class Juz(val number: Int) : ItemScope

    data class Range(val from: AyahRef, val to: AyahRef) : ItemScope

    data object Everything : ItemScope
}

enum class ItemFailure {
    None,
    WifiRequired,
    Failed,
}

/**
 * What the manager knows about one item of one reciter. [status] and [estimateBytes] are null until
 * read; [progress] is set while the item downloads.
 */
data class ItemState(
    val status: AudioStatus? = null,
    val estimateBytes: Long? = null,
    val progress: DownloadProgress? = null,
    val failure: ItemFailure = ItemFailure.None,
    /** The named cause behind [failure], when there is one. */
    val problem: DownloadProblem? = null,
)

data class ManagerReciter(val id: String, val name: String, val usedBytes: Long)

/**
 * The downloads manager: for each reciter, audio per surah, per juz, for a range or everything,
 * each with its size before downloading and a delete action. Downloads run in [downloadScope],
 * which should outlive the screen so that leaving it does not stop them.
 */
@Suppress("TooManyFunctions") // Range setters plus one function per item action.
class DownloadsManagerViewModel(
    private val downloads: AudioDownloads,
    private val reciters: ReciterRepository,
    private val quranText: QuranTextRepository,
    private val downloadScope: CoroutineScope,
    private val runner: AudioDownloadRunner = DirectDownloadRunner(downloads),
    private val network: NetworkStatus = NetworkStatus.Unrestricted,
) : ViewModel() {

    private val plan = DownloadPlan(quranText)
    private val reciterList = MutableStateFlow<List<Reciter>>(emptyList())
    private val mutableChapters = MutableStateFlow<List<Chapter>>(emptyList())
    private val mutableItems = MutableStateFlow<Map<String, ItemState>>(emptyMap())
    private val mutableRange =
        MutableStateFlow(RangeSelection(from = AyahRef(1, 1), to = AyahRef(1, 7)))
    private val jobs = mutableMapOf<String, Job>()

    val chapters: StateFlow<List<Chapter>> = mutableChapters

    /** Item states keyed by [itemKey]. */
    val items: StateFlow<Map<String, ItemState>> = mutableItems

    /** The range the "Range" item stands for. */
    val range: StateFlow<RangeSelection> = mutableRange

    @OptIn(ExperimentalCoroutinesApi::class)
    val reciterRows: StateFlow<List<ManagerReciter>> =
        reciterList
            .flatMapLatest { list ->
                if (list.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(list.map { downloads.usedBytes(it.id) }) { sizes ->
                        list.mapIndexed { index, reciter ->
                            ManagerReciter(reciter.id, reciter.name, sizes[index])
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            reciterList.value = reciters.reciters()
            mutableChapters.value = quranText.chapters()
        }
    }

    fun setRangeFromSurah(surah: Int) = mutableRange.update { it.withFrom(AyahRef(surah, 1)) }

    fun setRangeFromAyah(ayahNumber: Int) = mutableRange.update {
        it.withFrom(AyahRef(it.from.surah, ayahNumber))
    }

    fun setRangeToSurah(surah: Int) = mutableRange.update {
        it.withTo(AyahRef(surah, versesIn(surah)))
    }

    fun setRangeToAyah(ayahNumber: Int) = mutableRange.update {
        it.withTo(AyahRef(it.to.surah, ayahNumber))
    }

    /** Reads how much of [scope] [reciterId] has on disk and what downloading the rest takes. */
    fun load(reciterId: String, scope: ItemScope) {
        viewModelScope.launch {
            val key = itemKey(reciterId, scope)
            val known = mutableItems.value[key]
            val alreadyRead =
                known?.status != null && (known.status.complete || known.estimateBytes != null)
            if (alreadyRead) return@launch
            val ayahs = ayahsOf(scope)
            val status = downloads.status(reciterId, ayahs)
            changeItem(key) { it.copy(status = status) }
            if (!status.complete) {
                val estimate = downloads.estimateBytes(reciterId, ayahs)
                changeItem(key) { it.copy(estimateBytes = estimate) }
            }
        }
    }

    fun download(reciterId: String, scope: ItemScope) {
        val key = itemKey(reciterId, scope)
        if (jobs[key]?.isActive == true) return
        jobs[key] = downloadScope.launch { runDownload(reciterId, scope, key) }
    }

    fun cancel(reciterId: String, scope: ItemScope) {
        jobs[itemKey(reciterId, scope)]?.cancel()
    }

    fun delete(reciterId: String, scope: ItemScope) {
        viewModelScope.launch {
            if (scope == ItemScope.Everything) {
                downloads.remove(reciterId)
            } else {
                downloads.removeAyahs(reciterId, ayahsOf(scope))
            }
            refreshStatus(reciterId, scope, itemKey(reciterId, scope))
        }
    }

    private suspend fun runDownload(reciterId: String, scope: ItemScope, key: String) {
        changeItem(key) {
            it.copy(
                progress = DownloadProgress(0L, null),
                failure = ItemFailure.None,
                problem = null,
            )
        }
        try {
            runner.run(reciterId, ayahsOf(scope)) { progress ->
                changeItem(key) { it.copy(progress = progress) }
            }
        } catch (failed: IOException) {
            val problem = DownloadProblem.classify(failed, network.isOnline())
            val failure =
                if (problem == DownloadProblem.WifiOnly) {
                    ItemFailure.WifiRequired
                } else {
                    ItemFailure.Failed
                }
            changeItem(key) { it.copy(failure = failure, problem = problem) }
        } finally {
            withContext(NonCancellable) {
                refreshStatus(reciterId, scope, key)
                changeItem(key) { it.copy(progress = null) }
            }
        }
    }

    private suspend fun refreshStatus(reciterId: String, scope: ItemScope, key: String) {
        val status = downloads.status(reciterId, ayahsOf(scope))
        changeItem(key) { it.copy(status = status) }
    }

    private suspend fun ayahsOf(scope: ItemScope): List<AyahRef> =
        when (scope) {
            is ItemScope.Surah -> plan.surahAyahs(scope.number)
            is ItemScope.Juz -> plan.juzAyahs(scope.number)
            is ItemScope.Range -> plan.rangeAyahs(scope.from, scope.to)
            ItemScope.Everything -> plan.allAyahs()
        }

    private fun changeItem(key: String, change: (ItemState) -> ItemState) {
        mutableItems.update { it + (key to change(it[key] ?: ItemState())) }
    }

    private fun versesIn(surah: Int): Int =
        mutableChapters.value.firstOrNull { it.number == surah }?.versesCount ?: 1

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The key of an item in [items]. */
        fun itemKey(reciterId: String, scope: ItemScope): String = "$reciterId|$scope"
    }
}
