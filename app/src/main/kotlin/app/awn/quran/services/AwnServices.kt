package app.awn.quran.services

import android.app.Application
import app.awn.quran.AppPreferences
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.CoreData
import app.awn.quran.core.data.MyWords
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.downloads.CatalogueModel
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.data.models.SpeechModelStore
import app.awn.quran.feature.hifz.HifzServices
import app.awn.quran.feature.hifz.PracticeRequest
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.learn.LearnServices
import app.awn.quran.feature.learn.learnAddWord
import app.awn.quran.feature.learn.learnProgressEvents
import app.awn.quran.feature.learn.learnRemoveWord
import app.awn.quran.feature.learn.learnWordIsAdded
import app.awn.quran.feature.learn.learnWordMarks
import app.awn.quran.feature.listen.AudioFeature
import app.awn.quran.feature.listen.ListenSettings
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.quran.PrintOfferStore
import app.awn.quran.feature.read.quran.wordReciterFor
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
import app.awn.quran.search.StoppableInput
import app.awn.quran.tracker.TrackerInput
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
            wordReciterId = listenSettings.wordReciterId,
            printOffer =
                object : PrintOfferStore {
                    override val isPending = preferences.printOfferPending

                    override suspend fun markHandled() = preferences.markPrintOfferHandled()
                },
            isNetworkMetered = networkStatus::isMetered,
            myWords =
                object : MyWords {
                    override fun isAdded(word: WordKey) = learnWordIsAdded(application, word)

                    override suspend fun add(word: WordKey) = learnAddWord(application, word)

                    override suspend fun remove(word: WordKey) = learnRemoveWord(application, word)

                    override fun marks(words: Collection<WordKey>) =
                        learnWordMarks(application, words)
                },
        )

    /** The speech model is downloaded after install; recite offers the download first. */
    val speechModel =
        SpeechModelStore(File(application.filesDir, MODEL_DIRECTORY), applicationScope)

    /** Loads the model for reciting; the recite entry prepares it again after a download. */
    val speechPreparation = RemoteSpeech.preparation(application, speechModel)

    /** The microphone for voice search; tapping stop ends its recording. */
    fun openVoiceSearchMicrophone(): StoppableInput {
        val microphone = MicrophoneTrackerInput(application)
        return object : StoppableInput, TrackerInput by microphone {
            override fun stop() = microphone.close()
        }
    }

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
            wordReciterId = { listeningReciterId ->
                val withTimings =
                    coreData.reciters
                        .reciters()
                        .filter { it.wordByWord && it.timingPack != null }
                        .map { it.id }
                wordReciterFor(
                    listenSettings.wordReciterId.first(),
                    listeningReciterId,
                    withTimings,
                ) ?: listeningReciterId
            },
            myWords = readDependencies.myWords,
        )

    /**
     * Plays the Quran word of a Learn page or practice item with the word reciter from Settings.
     * Built on first use: its download list reads WorkManager, whose start-up needs this holder to
     * exist already (AwnApplication.workManagerConfiguration), so building it here crashed.
     */
    val learnWordSound by lazy {
        LearnWordSound(
            reciters = coreData.reciters,
            audioPlayer = audioPlayer,
            listeningReciterId = listenSettings.selectedReciterId,
            chosenWordReciterId = listenSettings.wordReciterId,
            catalogue = CatalogueModel(coreData.packs, coreData.packDownloads, applicationScope),
        )
    }

    /** The Hifz tab, its plan and its practice; the pages follow the print the reader shows. */
    val hifz =
        HifzServices(
            application,
            coreData.quranText,
            readDependencies.settingsStore.settings.map { it.script }.distinctUntilChanged(),
            applicationScope,
        )

    /**
     * Opens the Quran screen at the first ayah of [request] in recite mode. A new lesson shows the
     * ayah, and the user hides the words with the Hifz bar's eye button; revision parts start with
     * the words hidden. The choice lasts for this practice only and leaves the saved recite setting
     * alone. The Hifz tab calls it after the practice is stored in `hifz.controller`.
     */
    fun startHifzPractice(request: PracticeRequest) {
        openQuranAt(request.firstAyah)
        recite.practiceFrom(request.firstAyah, hideWords = request.kind != PartKind.NewLesson)
    }

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
