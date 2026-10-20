package app.awn.quran.player.fakes

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.AudioStatus
import app.awn.quran.player.DownloadProgress
import app.awn.quran.player.DownloadState
import app.awn.quran.player.PlaybackOptions
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * A player that makes no sound. It moves [state] as a real one would at the start of playback and
 * records the calls. A test sets [currentState] to simulate progress.
 */
class FakeAudioPlayer : AudioPlayer {

    val currentState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playCalls = mutableListOf<Triple<String, AyahRef, AyahRef?>>()
    val wordCalls = mutableListOf<Triple<String, AyahRef, Int>>()
    var nextCalls = 0
        private set

    var previousCalls = 0
        private set

    var stopCalls = 0
        private set

    private val mutableOptions = MutableStateFlow(PlaybackOptions())

    /** Repeats of each ayah beyond the first play, as `setRepeat` sets them. */
    val repeatTimes: Int
        get() = mutableOptions.value.repeatEachAyah - 1

    override val state: Flow<PlaybackState> = currentState

    override val options: StateFlow<PlaybackOptions> = mutableOptions

    override suspend fun play(reciterId: String, from: AyahRef, to: AyahRef?) {
        playCalls += Triple(reciterId, from, to)
        currentState.value = PlaybackState.Playing(reciterId, from, null, 0L, null)
    }

    override suspend fun playWord(reciterId: String, ayah: AyahRef, position: Int) {
        wordCalls += Triple(reciterId, ayah, position)
        currentState.value = PlaybackState.Playing(reciterId, ayah, position, 0L, null)
    }

    override fun pause() {
        val playing = currentState.value as? PlaybackState.Playing ?: return
        currentState.value =
            PlaybackState.Paused(playing.reciterId, playing.ayah, playing.positionMs)
    }

    override fun resume() {
        val paused = currentState.value as? PlaybackState.Paused ?: return
        currentState.value =
            PlaybackState.Playing(paused.reciterId, paused.ayah, null, paused.positionMs, null)
    }

    override fun stop() {
        stopCalls++
        currentState.value = PlaybackState.Idle
    }

    override fun next() {
        nextCalls++
    }

    override fun previous() {
        previousCalls++
    }

    override fun setOptions(options: PlaybackOptions) {
        mutableOptions.value = options.coerced()
    }
}

/**
 * In-memory downloads. [downloadSurah] marks a whole surah present with [bytesPerSurah] bytes and
 * [download] marks single ayahs present with [bytesPerAyah] bytes each. A test sets [gate] to hold
 * [download] until it completes it, or [failure] to make [download] throw.
 */
@Suppress("TooManyFunctions") // Mirrors the AudioDownloads interface.
class FakeAudioDownloads(
    private val bytesPerSurah: Long = 1_000L,
    private val bytesPerAyah: Long = 100L,
) : AudioDownloads {

    private data class Holdings(
        val surahs: Set<Int> = emptySet(),
        val ayahs: Set<AyahRef> = emptySet(),
    ) {
        fun has(ayah: AyahRef) = ayah.surah in surahs || ayah in ayahs
    }

    private val holdings = MutableStateFlow<Map<String, Holdings>>(emptyMap())

    /** Each `download` call, in order. */
    val downloadCalls = mutableListOf<Pair<String, List<AyahRef>>>()

    var gate: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null

    /** What [estimateBytes] returns for any request. */
    var estimate: Long? = 5_000L

    override fun state(reciterId: String, surah: Int): Flow<DownloadState> = holdings.map {
        val held = it[reciterId]
        if (held != null && surah in held.surahs) {
            DownloadState(reciterId, surah, bytesPerSurah, bytesPerSurah, true)
        } else {
            DownloadState(reciterId, surah, 0L, null, false)
        }
    }

    override suspend fun downloadPage(reciterId: String, ayahs: List<AyahRef>) {
        ayahs.map { it.surah }.distinct().forEach { downloadSurah(reciterId, it) }
    }

    override suspend fun downloadSurah(reciterId: String, surah: Int) {
        update(reciterId) { it.copy(surahs = it.surahs + surah) }
    }

    override suspend fun download(
        reciterId: String,
        ayahs: List<AyahRef>,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        downloadCalls += reciterId to ayahs
        val total = ayahs.size * bytesPerAyah
        onProgress(DownloadProgress(0L, total))
        gate?.await()
        failure?.let { throw it }
        update(reciterId) { it.copy(ayahs = it.ayahs + ayahs) }
        onProgress(DownloadProgress(total, total))
    }

    override suspend fun bytesUsed(reciterId: String): Long = bytesHeld(holdings.value[reciterId])

    override fun usedBytes(reciterId: String): Flow<Long> = holdings.map {
        bytesHeld(it[reciterId])
    }

    override suspend fun status(reciterId: String, ayahs: List<AyahRef>): AudioStatus {
        val held = holdings.value[reciterId]
        val present = ayahs.distinct().count { held?.has(it) == true }
        return AudioStatus(present * bytesPerAyah, present, ayahs.distinct().size)
    }

    override suspend fun estimateBytes(reciterId: String, ayahs: List<AyahRef>): Long? = estimate

    override suspend fun removeAyahs(reciterId: String, ayahs: List<AyahRef>) {
        update(reciterId) {
            it.copy(
                surahs = it.surahs - ayahs.map { a -> a.surah }.toSet(),
                ayahs = it.ayahs - ayahs.toSet(),
            )
        }
    }

    override suspend fun remove(reciterId: String) {
        holdings.value = holdings.value - reciterId
    }

    private fun bytesHeld(held: Holdings?): Long =
        if (held == null) {
            0L
        } else {
            held.surahs.size * bytesPerSurah +
                held.ayahs.count { it.surah !in held.surahs } * bytesPerAyah
        }

    private fun update(reciterId: String, change: (Holdings) -> Holdings) {
        holdings.value =
            holdings.value + (reciterId to change(holdings.value[reciterId] ?: Holdings()))
    }
}
