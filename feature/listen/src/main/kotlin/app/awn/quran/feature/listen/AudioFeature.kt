package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.player.AudioDownloadRunner
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.DirectDownloadRunner
import app.awn.quran.player.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Everything the audio composables need, created once by :app and passed to [AudioBar],
 * [PlayRangePanel] and [AudioSettingsPage]. It holds the view model factory, so :app registers no
 * view models for audio.
 *
 * - [player] should be a [app.awn.quran.player.DownloadingAudioPlayer] around the Exo player, so
 *   playing a range downloads missing audio first.
 * - [scope] outlives the screens (the application scope): downloads and play requests run in it.
 * - [initialScript] is the print whose page numbers the audio bar uses to find the ayahs of a page.
 *   :app calls [useScript] whenever the reader changes print, so the bar maps a page to the ayahs
 *   the reader shows on it.
 *
 * Repeat, speed and the memorisation options are restored into [player] when this is created and
 * saved to [settings] after every change, so they hold across launches and surahs.
 */
@Suppress("LongParameterList") // A holder of the audio module's dependencies.
class AudioFeature(
    val player: AudioPlayer,
    val downloads: AudioDownloads,
    val reciters: ReciterRepository,
    val quranText: QuranTextRepository,
    val settings: ListenSettings,
    val scope: CoroutineScope,
    initialScript: ScriptId = ScriptId.V2,
    private val downloadRunner: AudioDownloadRunner = DirectDownloadRunner(downloads),
    private val network: NetworkStatus = NetworkStatus.Unrestricted,
) {

    private val currentScript = MutableStateFlow(initialScript)

    /** The print that maps a page to its ayahs. */
    val script: StateFlow<ScriptId> = currentScript

    fun useScript(script: ScriptId) {
        currentScript.value = script
    }

    init {
        // The player must be touched on the main thread, which the application scope is not on.
        scope.launch(Dispatchers.Main.immediate) {
            player.setOptions(settings.playbackOptions.first())
            player.options.collect { settings.savePlaybackOptions(it) }
        }
    }

    /** What the Quran screen's `onPlayFrom` slot calls. */
    val controller = AudioController(player, settings, scope)

    internal val viewModelFactory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val viewModel: ViewModel =
                    when (modelClass) {
                        AudioBarViewModel::class.java ->
                            AudioBarViewModel(
                                player,
                                reciters,
                                quranText,
                                settings,
                                controller,
                                script,
                            )
                        PlayRangeViewModel::class.java ->
                            PlayRangeViewModel(player, quranText, controller, script)
                        ReciterSheetViewModel::class.java ->
                            ReciterSheetViewModel(reciters, downloads, quranText, settings, script)
                        AudioSettingsViewModel::class.java ->
                            AudioSettingsViewModel(reciters, downloads, settings, player)
                        DownloadsManagerViewModel::class.java ->
                            DownloadsManagerViewModel(
                                downloads,
                                reciters,
                                quranText,
                                scope,
                                downloadRunner,
                                network,
                            )
                        else -> throw IllegalArgumentException("No audio view model: $modelClass")
                    }
                return viewModel as T
            }
        }
}
