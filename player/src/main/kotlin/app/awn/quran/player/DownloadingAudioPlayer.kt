package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Wraps another [AudioPlayer] so that [play] first downloads the audio of the range when it is
 * missing, then starts playback. While the download runs, [state] is [PlaybackState.Downloading];
 * [stop] cancels it.
 *
 * What is downloaded follows [downloadAmount] (see [DownloadAmount]). There are no surprise
 * downloads: when the missing audio is over [promptAboveBytes], or the network is metered, [state]
 * becomes [PlaybackState.DownloadPrompt] and nothing is fetched until [answerDownloadPrompt]. Below
 * that the download starts at once.
 *
 * A download that is refused (Wi-Fi only on a metered network) or fails streams the range instead,
 * because streaming plays the same audio. Two failures are shown as [PlaybackState.Error] with a
 * [DownloadProblem]: any failure of a download the user asked for, and having no internet at all
 * (streaming would fail too).
 *
 * [play] returns as soon as the request is accepted; watch [state] for progress. The download runs
 * in [scope], which should outlive the screen that asked for it.
 */
@Suppress("LongParameterList") // The collaborators of one policy; each has a default or is needed.
class DownloadingAudioPlayer(
    private val player: AudioPlayer,
    private val downloads: AudioDownloads,
    quranText: QuranTextRepository,
    private val downloadAmount: suspend () -> DownloadAmount,
    private val scope: CoroutineScope,
    private val runner: AudioDownloadRunner = DirectDownloadRunner(downloads),
    private val network: NetworkStatus = NetworkStatus.Unrestricted,
    private val networkPolicy: DownloadNetworkPolicy = DownloadNetworkPolicy.Always,
    private val promptAboveBytes: Long = DEFAULT_PROMPT_ABOVE_BYTES,
) : AudioPlayer by player {

    private val playlistBuilder = PlaylistBuilder(quranText)
    private val downloadPlan = DownloadPlan(quranText)

    /** What replaces the wrapped player's state while a download is asked about, runs or fails. */
    private val overlay = MutableStateFlow<PlaybackState?>(null)
    private val prompt = MutableStateFlow<DownloadPrompt?>(null)
    private var requestJob: Job? = null
    private var promptAnswer: CompletableDeferred<Boolean>? = null

    override val downloadPrompt: StateFlow<DownloadPrompt?> = prompt

    override val state: Flow<PlaybackState> =
        combine(player.state, overlay) { playback, shown -> shown ?: playback }

    override suspend fun play(reciterId: String, from: AyahRef, to: AyahRef?) {
        requestJob?.cancel()
        overlay.value = null
        // Started only after requestJob is set, so the job can tell whether it is still current.
        val job =
            scope.launch(start = CoroutineStart.LAZY) { downloadThenPlay(reciterId, from, to) }
        requestJob = job
        job.start()
    }

    override fun stop() {
        requestJob?.cancel()
        requestJob = null
        overlay.value = null
        player.stop()
    }

    override fun cancelPending() {
        val pendingJob = requestJob
        if (pendingJob == null || !pendingJob.isActive) return
        // The job ends as soon as the wrapped player has the range, so an active job means that
        // nothing has started to sound for this request yet.
        pendingJob.cancel()
        requestJob = null
        overlay.value = null
    }

    override fun answerDownloadPrompt(download: Boolean) {
        promptAnswer?.complete(download)
    }

    private suspend fun downloadThenPlay(reciterId: String, from: AyahRef, to: AyahRef?) {
        try {
            val range = rangeOrNull(from, to)
            val mayPlay = range == null || downloadMissing(reciterId, range)
            if (mayPlay) player.play(reciterId, from, to)
        } finally {
            // A newer request owns the overlay once it has replaced this job. A shown error stays
            // until the next request or stop.
            val stillCurrent = requestJob === currentCoroutineContext()[Job]
            if (stillCurrent && overlay.value !is PlaybackState.Error) overlay.value = null
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
     * already. Returns true when playback should start now (the audio is there, or will stream) and
     * false when an error is shown instead.
     */
    @Suppress("ReturnCount") // Each return says whether playback may start now.
    private suspend fun downloadMissing(reciterId: String, range: List<AyahRef>): Boolean {
        var askedByUser = false
        try {
            val ayahs = withBasmalah(reciterId, downloadPlan.ayahsFor(downloadAmount(), range))
            val onDisk = downloads.status(reciterId, ayahs)
            if (onDisk.complete) return true
            if (!networkPolicy.mayDownload()) return true
            val missingBytes =
                downloads.estimateBytes(reciterId, ayahs)?.let { it - onDisk.presentBytes }
            val needsPrompt =
                missingBytes != null && (missingBytes > promptAboveBytes || network.isMetered())
            if (needsPrompt) {
                if (!askToDownload(missingBytes)) return true
                askedByUser = true
            }
            player.stop()
            overlay.value = PlaybackState.Downloading(doneBytes = 0L, totalBytes = null)
            runner.run(reciterId, ayahs) {
                overlay.value = PlaybackState.Downloading(it.doneBytes, it.totalBytes)
            }
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: IOException) {
            return handleFailure(failed, askedByUser)
        } catch (
            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            unavailable: RuntimeException,) {
            // No page or audio information for the ayah: the player reports a real problem itself.
            return true
        }
    }

    /** Shows the size and waits for the user's answer; true means download. */
    private suspend fun askToDownload(missingBytes: Long): Boolean {
        val answer = CompletableDeferred<Boolean>()
        promptAnswer = answer
        player.stop()
        prompt.value = DownloadPrompt(missingBytes, network.isMetered())
        try {
            return answer.await()
        } finally {
            promptAnswer = null
            prompt.value = null
        }
    }

    /** Streams instead of failing, except where the user must be told why. */
    private fun handleFailure(failed: IOException, askedByUser: Boolean): Boolean {
        val problem = DownloadProblem.classify(failed, network.isOnline())
        val mustShow = askedByUser || problem == DownloadProblem.NoInternet
        if (!mustShow) return true
        overlay.value = PlaybackState.Error(problem.englishMessage(), problem)
        return false
    }

    /**
     * The audio to fetch together with [ayahs]: a range that starts a surah plays the reciter's
     * Basmalah first (ayah 1:1), so it is fetched with the rest instead of streamed.
     */
    private suspend fun withBasmalah(reciterId: String, ayahs: List<AyahRef>): List<AyahRef> {
        val basmalah = AyahRef(1, 1)
        val startsASurah = ayahs.any {
            it.ayah == 1 && it.surah != 1 && it.surah != SURAH_WITHOUT_BASMALAH
        }
        if (!startsASurah || basmalah in ayahs || !downloads.hasAyahFiles(reciterId)) return ayahs
        return listOf(basmalah) + ayahs
    }

    companion object {
        /** Above this much to fetch (20 MB) the user is asked first. */
        const val DEFAULT_PROMPT_ABOVE_BYTES = 20_000_000L
        private const val SURAH_WITHOUT_BASMALAH = 9
    }
}
