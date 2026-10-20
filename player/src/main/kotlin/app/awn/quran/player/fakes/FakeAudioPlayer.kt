package app.awn.quran.player.fakes

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.DownloadState
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A player that makes no sound. It moves [state] as a real one would at the start of playback and
 * records the calls. A test sets [currentState] to simulate progress.
 */
class FakeAudioPlayer : AudioPlayer {

    val currentState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playCalls = mutableListOf<Triple<String, AyahRef, AyahRef?>>()
    val wordCalls = mutableListOf<Triple<String, AyahRef, Int>>()
    var repeatTimes = 0
        private set

    override val state: Flow<PlaybackState> = currentState

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
        currentState.value = PlaybackState.Idle
    }

    override fun setRepeat(times: Int) {
        repeatTimes = times
    }
}

/** In-memory downloads: [downloadSurah] marks the surah complete with [bytesPerSurah] bytes. */
class FakeAudioDownloads(private val bytesPerSurah: Long = 1_000L) : AudioDownloads {

    private val states = MutableStateFlow<Map<Pair<String, Int>, DownloadState>>(emptyMap())

    override fun state(reciterId: String, surah: Int): Flow<DownloadState> = states.map {
        it[reciterId to surah] ?: DownloadState(reciterId, surah, 0L, null, false)
    }

    override suspend fun downloadPage(reciterId: String, ayahs: List<AyahRef>) {
        ayahs.map { it.surah }.distinct().forEach { downloadSurah(reciterId, it) }
    }

    override suspend fun downloadSurah(reciterId: String, surah: Int) {
        val complete = DownloadState(reciterId, surah, bytesPerSurah, bytesPerSurah, true)
        states.value = states.value + ((reciterId to surah) to complete)
    }

    override suspend fun bytesUsed(reciterId: String): Long =
        states.value.filterKeys { it.first == reciterId }.values.sumOf { it.downloadedBytes }

    override suspend fun remove(reciterId: String) {
        states.value = states.value.filterKeys { it.first != reciterId }
    }
}
