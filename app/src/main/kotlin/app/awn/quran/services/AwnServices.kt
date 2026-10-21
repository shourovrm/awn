package app.awn.quran.services

import android.app.Application
import app.awn.quran.AppPreferences
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.CoreData
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.feature.learn.LearnServices
import app.awn.quran.feature.learn.fsrs.FsrsRating
import app.awn.quran.feature.learn.learnProgressEvents
import app.awn.quran.feature.listen.AudioFeature
import app.awn.quran.feature.listen.ListenSettings
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.quran.PrintOfferStore
import app.awn.quran.feature.read.settings.DataStoreReadSettingsStore
import app.awn.quran.feature.recite.AyahGrade
import app.awn.quran.feature.recite.MicrophoneTrackerInput
import app.awn.quran.feature.recite.ReciteController
import app.awn.quran.feature.recite.ReciteSettings
import app.awn.quran.feature.recite.RevisionQueue
import app.awn.quran.player.AndroidNetworkStatus
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.DirectoryFreeSpace
import app.awn.quran.player.DownloadingAudioPlayer
import app.awn.quran.player.ExoAudioPlayer
import app.awn.quran.player.OkHttpAudioDownloads
import app.awn.quran.player.UnmeteredOnlyPolicy
import app.awn.quran.player.WorkManagerDownloadRunner
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

    private val audioRoot = File(application.filesDir, AUDIO_DIRECTORY)

    private val wifiOnlyPolicy =
        UnmeteredOnlyPolicy(application) { listenSettings.wifiOnly.first() }

    val audioDownloads: AudioDownloads =
        OkHttpAudioDownloads(
            audioRoot = audioRoot,
            reciters = coreData.reciters,
            quranText = coreData.quranText,
            client = okHttpClient,
            networkPolicy = wifiOnlyPolicy,
        )

    /** Free space where audio is stored; checked before every audio download. */
    val audioFreeSpace = DirectoryFreeSpace(application, audioRoot)

    val networkStatus = AndroidNetworkStatus(application)

    /** Audio downloads run in WorkManager, so they finish after the app closes. */
    private val audioDownloadRunner = WorkManagerDownloadRunner(application)

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
            runner = audioDownloadRunner,
            network = networkStatus,
            networkPolicy = wifiOnlyPolicy,
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
            downloadRunner = audioDownloadRunner,
            network = networkStatus,
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
            printOffer =
                object : PrintOfferStore {
                    override val isPending = preferences.printOfferPending

                    override suspend fun markHandled() = preferences.markPrintOfferHandled()
                },
        )

    private val modelFiles =
        ModelFiles(application.assets, File(application.filesDir, MODEL_DIRECTORY))

    private val speechPreparation = RemoteSpeech.preparation(application, modelFiles)

    /** Reciting on the Quran screen; the speech model loads when recite starts. */
    val recite =
        ReciteController(
            quranText = coreData.quranText,
            progressEvents = progressEvents,
            settings = reciteSettings,
            audioPlayer = audioPlayer,
            speech = speechPreparation,
            inputFactory = { MicrophoneTrackerInput(application) },
            scope = applicationScope,
            gradeSink = { ayah, grade -> learn.progress.gradeAyah(ayah, grade.toFsrsRating()) },
            revisionQueue = RevisionQueue { learn.progress.dueAyahs() },
            onOpenAyah = { ayah -> openQuranAt(ayah) },
            hasWordTimings = { reciterId, ayah ->
                coreData.reciters.ayahAudio(reciterId, ayah)?.words.orEmpty().isNotEmpty()
            },
        )

    /**
     * Opens the Quran screen at an ayah. The navigation host sets it once it exists; revision uses
     * it to move to the next due ayah.
     */
    var openQuranAt: (AyahRef) -> Unit = {}

    private fun AyahGrade.toFsrsRating(): FsrsRating =
        when (this) {
            AyahGrade.Easy -> FsrsRating.Easy
            AyahGrade.Good -> FsrsRating.Good
            AyahGrade.Hard -> FsrsRating.Hard
            AyahGrade.Forgot -> FsrsRating.Again
        }

    private companion object {
        const val AUDIO_DIRECTORY = "audio"
        const val MODEL_DIRECTORY = "models"
    }
}
