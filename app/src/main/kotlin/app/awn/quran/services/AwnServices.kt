package app.awn.quran.services

import android.app.Application
import app.awn.quran.AppPreferences
import app.awn.quran.core.data.CoreData
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.feature.learn.LearnServices
import app.awn.quran.feature.learn.learnProgressEvents
import app.awn.quran.feature.listen.ListenSettings
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.settings.DataStoreReadSettingsStore
import app.awn.quran.feature.recite.MicrophoneTrackerInput
import app.awn.quran.feature.recite.RecitationSession
import app.awn.quran.feature.recite.ReciteSettings
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.ExoAudioPlayer
import app.awn.quran.player.OkHttpAudioDownloads
import app.awn.quran.player.UnmeteredOnlyPolicy
import app.awn.quran.tracker.JsonReferencePhonemes
import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.SherpaPhonemeSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * The objects that live as long as the process, created by hand in [AwnApplication] (no
 * dependency-injection framework). Screens receive what they need from here through view models and
 * the navigation host.
 */
class AwnServices(private val application: Application) {

    /** For work that must go on after the screen that started it is gone, such as downloads. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = AppPreferences(application)
    val coreData = CoreData(application)
    val listenSettings = ListenSettings.create(application)
    val reciteSettings = ReciteSettings(application)
    val learn: LearnServices = LearnServices.get(application)
    val progressEvents: ProgressEvents = learnProgressEvents(application)

    private val okHttpClient = OkHttpClient()

    val audioPlayer =
        ExoAudioPlayer(application, coreData.reciters, coreData.quranText, okHttpClient)

    val audioDownloads: AudioDownloads =
        OkHttpAudioDownloads(
            audioRoot = File(application.filesDir, AUDIO_DIRECTORY),
            reciters = coreData.reciters,
            quranText = coreData.quranText,
            client = okHttpClient,
            networkPolicy = UnmeteredOnlyPolicy(application) { listenSettings.wifiOnly.first() },
        )

    val readDependencies =
        ReadDependencies(
            quranText = coreData.quranText,
            translations = coreData.translations,
            bookmarks = coreData.bookmarks,
            packs = coreData.packs,
            progressEvents = progressEvents,
            audioPlayer = audioPlayer,
            settingsStore = DataStoreReadSettingsStore(application),
        )

    private val modelFiles =
        ModelFiles(application.assets, File(application.filesDir, MODEL_DIRECTORY))

    // Parsed on first use, which happens off the main thread when the recite screen loads a range.
    private val referencePhonemes: ReferencePhonemes = LazyReferencePhonemes {
        JsonReferencePhonemes.fromFile(modelFiles.ensureInstalled().referencePhonemes)
    }

    /** Builds a session whose work runs in [scope]; the recite view model owns one. */
    fun createRecitationSession(scope: CoroutineScope): RecitationSession =
        RecitationSession(
            quranText = coreData.quranText,
            referencePhonemes = referencePhonemes,
            trackerFactory = AlignmentTrackerFactory,
            progressEvents = progressEvents,
            phonemeSourceFactory = ::createPhonemeSource,
            inputFactory = { MicrophoneTrackerInput(application) },
            scope = scope,
        )

    private fun createPhonemeSource(): SherpaPhonemeSource {
        val paths = modelFiles.ensureInstalled()
        return try {
            SherpaPhonemeSource(paths.model.path, paths.tokens.path)
        } catch (problem: UnsatisfiedLinkError) {
            // Turned into the exception the session reports as "can't use the microphone".
            throw IllegalStateException("The speech library could not be loaded.", problem)
        }
    }

    private companion object {
        const val AUDIO_DIRECTORY = "audio"
        const val MODEL_DIRECTORY = "models"
    }
}
