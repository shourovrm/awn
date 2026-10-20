package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Wraps another [AudioPlayer] so that [play] first downloads the audio of the range when it is
 * missing, then starts playback. While the download runs, [state] is [PlaybackState.Downloading];
 * [stop] cancels it.
 *
 * What is downloaded follows [downloadAmount] (see [DownloadAmount]). When the download is refused
 * (Wi-Fi only on a metered network) or fails, the range is streamed instead, because streaming
 * plays the same audio and works wherever the download would have.
 *
 * [play] returns as soon as the request is accepted; watch [state] for progress. The download runs
 * in [scope], which should outlive the screen that asked for it.
 */
class DownloadingAudioPlayer(
    private val player: AudioPlayer,
    private val downloads: AudioDownloads,
    quranText: QuranTextRepository,
    private val downloadAmount: suspend () -> DownloadAmount,
    private val scope: CoroutineScope,
) : AudioPlayer by player {

    private val playlistBuilder = PlaylistBuilder(quranText)
    private val downloadPlan = DownloadPlan(quranText)
    private val downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    private var requestJob: Job? = null

    override val state: Flow<PlaybackState> =
        combine(player.state, downloadProgress) { playback, progress ->
            if (progress == null) {
                playback
            } else {
                PlaybackState.Downloading(progress.doneBytes, progress.totalBytes)
            }
        }

    override suspend fun play(reciterId: String, from: AyahRef, to: AyahRef?) {
        requestJob?.cancel()
        downloadProgress.value = null
        // Started only after requestJob is set, so the job can tell whether it is still current.
        val job =
            scope.launch(start = CoroutineStart.LAZY) { downloadThenPlay(reciterId, from, to) }
        requestJob = job
        job.start()
    }

    override fun stop() {
        requestJob?.cancel()
        requestJob = null
        downloadProgress.value = null
        player.stop()
    }

    private suspend fun downloadThenPlay(reciterId: String, from: AyahRef, to: AyahRef?) {
        try {
            val range = rangeOrNull(from, to)
            if (range != null) downloadMissing(reciterId, range)
            player.play(reciterId, from, to)
        } finally {
            // A newer request owns the progress once it has replaced this job.
            if (requestJob === currentCoroutineContext()[Job]) downloadProgress.value = null
        }
    }

    /** Returns the ayahs of the range, or null for a bad range, which the player then reports. */
    private suspend fun rangeOrNull(from: AyahRef, to: AyahRef?): List<AyahRef>? =
        try {
            playlistBuilder.build(from, to)
        } catch (@Suppress("SwallowedException") badRange: IllegalArgumentException) {
            null
        }

    /**
     * Downloads what [downloadAmount] asks for from the start of [range] unless it is on disk
     * already. A refused or failed download is not an error: the audio streams instead.
     */
    private suspend fun downloadMissing(reciterId: String, range: List<AyahRef>) {
        try {
            val ayahs = downloadPlan.ayahsFor(downloadAmount(), range)
            if (downloads.status(reciterId, ayahs).complete) return
            player.stop()
            downloadProgress.value = DownloadProgress(doneBytes = 0L, totalBytes = null)
            downloads.download(reciterId, ayahs) { downloadProgress.value = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("SwallowedException") refusedOrFailed: IOException) {
            // Playback streams what could not be downloaded.
        } catch (
            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            unavailable: RuntimeException,) {
            // No page or audio information for the ayah: the player reports a real problem itself.
        }
    }
}
