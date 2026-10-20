package app.awn.quran.services

import android.app.Application
import app.awn.quran.AppPreferences
import app.awn.quran.core.data.CoreData
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.feature.learn.LearnServices
import app.awn.quran.feature.learn.learnProgressEvents
import app.awn.quran.feature.listen.AudioFeature
import app.awn.quran.feature.listen.ListenSettings
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.settings.DataStoreReadSettingsStore
import app.awn.quran.feature.recite.MicrophoneTrackerInput
import app.awn.quran.feature.recite.ReciteController
import app.awn.quran.feature.recite.ReciteSettings
import app.awn.quran.feature.recite.SpeechFiles
import app.awn.quran.feature.recite.SpeechPreparation
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.DownloadingAudioPlayer
import app.awn.quran.player.ExoAudioPlayer
import app.awn.quran.player.OkHttpAudioDownloads
import app.awn.quran.player.UnmeteredOnlyPolicy
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

    val audioDownloads: AudioDownloads =
        OkHttpAudioDownloads(
            audioRoot = File(application.filesDir, AUDIO_DIRECTORY),
            reciters = coreData.reciters,
            quranText = coreData.quranText,
            client = okHttpClient,
            networkPolicy = UnmeteredOnlyPolicy(application) { listenSettings.wifiOnly.first() },
        )

    private val exoPlayer =
        ExoAudioPlayer(application, coreData.reciters, coreData.quranText, okHttpClient)

    /** Plays through [exoPlayer], first downloading missing audio by the "Download amount". */
    val audioPlayer: AudioPlayer =
        DownloadingAudioPlayer(
            exoPlayer,
            audioDownloads,
            coreData.quranText,
            downloadAmount = { listenSettings.downloadAmount.first() },
            scope = applicationScope,
        )

    /** The audio bar, play panel, reciter sheet and audio settings share this. */
    val audio =
        AudioFeature(
            audioPlayer,
            audioDownloads,
            coreData.reciters,
            coreData.quranText,
            listenSettings,
            applicationScope,
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
            downloads = coreData.packDownloads,
            reciters = coreData.reciters,
            selectedReciterId = listenSettings.selectedReciterId,
        )

    private val modelFiles =
        ModelFiles(application.assets, File(application.filesDir, MODEL_DIRECTORY))

    private val speechPreparation = SpeechPreparation.fromFiles {
        val paths = modelFiles.ensureInstalled()
        SpeechFiles(paths.model, paths.tokens, paths.referencePhonemes)
    }

    /** Reciting on the Quran screen; [ReciteController.prepareSpeech] runs at app start. */
    val recite =
        ReciteController(
            quranText = coreData.quranText,
            progressEvents = progressEvents,
            settings = reciteSettings,
            audioPlayer = audioPlayer,
            speech = speechPreparation,
            inputFactory = { MicrophoneTrackerInput(application) },
            scope = applicationScope,
        )

    private companion object {
        const val AUDIO_DIRECTORY = "audio"
        const val MODEL_DIRECTORY = "models"
    }
}
