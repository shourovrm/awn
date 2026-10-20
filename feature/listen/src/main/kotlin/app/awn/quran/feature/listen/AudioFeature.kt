package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.AudioPlayer
import kotlinx.coroutines.CoroutineScope

/**
 * Everything the audio composables need, created once by :app and passed to [AudioBar],
 * [PlayRangePanel] and [AudioSettingsPage]. It holds the view model factory, so :app registers no
 * view models for audio.
 *
 * - [player] should be a [app.awn.quran.player.DownloadingAudioPlayer] around the Exo player, so
 *   playing a range downloads missing audio first.
 * - [scope] outlives the screens (the application scope): downloads and play requests run in it.
 * - [script] is the print whose page numbers the audio bar uses to find the ayahs of a page.
 */
@Suppress("LongParameterList") // A holder of the audio module's dependencies.
class AudioFeature(
    val player: AudioPlayer,
    val downloads: AudioDownloads,
    val reciters: ReciterRepository,
    val quranText: QuranTextRepository,
    val settings: ListenSettings,
    val scope: CoroutineScope,
    val script: ScriptId = ScriptId.V2,
) {

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
                            AudioSettingsViewModel(reciters, downloads, settings)
                        DownloadsManagerViewModel::class.java ->
                            DownloadsManagerViewModel(downloads, reciters, quranText, scope)
                        else -> throw IllegalArgumentException("No audio view model: $modelClass")
                    }
                return viewModel as T
            }
        }
}
