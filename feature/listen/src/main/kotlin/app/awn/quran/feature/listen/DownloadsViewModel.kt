package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.DownloadNotAllowedException
import app.awn.quran.player.DownloadState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

enum class DownloadMessage {
    None,
    WifiRequired,
    Failed,
}

/**
 * The per-surah download list for the selected reciter. Downloads run in [downloadScope], which the
 * app should keep alive longer than this screen so a download goes on after the user leaves.
 */
class DownloadsViewModel(
    private val downloads: AudioDownloads,
    private val quranText: QuranTextRepository,
    private val settings: ListenSettings,
    private val downloadScope: CoroutineScope,
) : ViewModel() {

    private val mutableChapters = MutableStateFlow<List<Chapter>>(emptyList())
    private val mutableMessage = MutableStateFlow(DownloadMessage.None)

    val chapters: StateFlow<List<Chapter>> = mutableChapters
    val message: StateFlow<DownloadMessage> = mutableMessage

    init {
        viewModelScope.launch { mutableChapters.value = quranText.chapters() }
    }

    /** Emits the download state of [surah] for the reciter selected now and after a change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun downloadState(surah: Int): Flow<DownloadState> =
        settings.selectedReciterId.flatMapLatest { downloads.state(it, surah) }

    fun download(surah: Int) {
        downloadScope.launch {
            mutableMessage.value = DownloadMessage.None
            val reciterId = settings.selectedReciterId.first()
            try {
                downloads.downloadSurah(reciterId, surah)
            } catch (@Suppress("SwallowedException") refused: DownloadNotAllowedException) {
                mutableMessage.value = DownloadMessage.WifiRequired
            } catch (@Suppress("SwallowedException") failure: IOException) {
                mutableMessage.value = DownloadMessage.Failed
            }
        }
    }

    fun dismissMessage() {
        mutableMessage.value = DownloadMessage.None
    }
}
