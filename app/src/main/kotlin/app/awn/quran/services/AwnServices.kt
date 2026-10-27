package app.awn.quran.services

import android.app.Application
import app.awn.quran.AppPreferences
import app.awn.quran.ReciteBarOrDownload
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.CoreData
import app.awn.quran.core.data.MyWords
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.downloads.CatalogueModel
import app.awn.quran.core.data.downloads.DownloadNetworkRule
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.data.models.SpeechModelStore
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.duas.PronunciationState
import app.awn.quran.feature.duas.content.DuaCatalogueRepository
import app.awn.quran.feature.duas.content.TopicCatalogueRepository
import app.awn.quran.feature.duas.data.DataStoreDuaProgressStore
import app.awn.quran.feature.duas.recite.ControllerDuaRecitation
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
import app.awn.quran.feature.listen.ReciterSheet
import app.awn.quran.feature.listen.replaceUnknownReciter
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val listenSettings = ListenSettings.create(application)
    val networkStatus = AndroidNetworkStatus(application)

    /** The Wi-Fi only switch as a value the download code reads without suspending. */
    private val wifiOnlyNow: StateFlow<Boolean> =
        listenSettings.wifiOnly.stateIn(applicationScope, SharingStarted.Eagerly, false)

    /** One rule for every download: packs, the recitation checker and the reciter audio. */
    val downloadNetworkRule =
        object : DownloadNetworkRule {
            override fun isWifiOnly(): Boolean = wifiOnlyNow.value

            override fun isMetered(): Boolean = networkStatus.isMetered()
        }

    val coreData = CoreData(application, downloadNetworkRule)

    init {
        // The catalogue no longer lists every reciter an earlier version offered.
        applicationScope.launch { listenSettings.replaceUnknownReciter(coreData.reciters) }
    }

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
        SpeechModelStore(
            File(application.filesDir, MODEL_DIRECTORY),
            applicationScope,
            networkRule = downloadNetworkRule,
        )

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

    private val duaCatalogue = DuaCatalogueRepository(application.assets)
    private val topicCatalogue = TopicCatalogueRepository(application.assets)

    /**
     * What the Duas screens use. Built on first use: it reaches [learnWordSound], whose download
     * list reads WorkManager, and WorkManager is configured only after this holder exists.
     */
    val duasDependencies by lazy {
        DuasDependencies(
            loadCatalogue = { duaCatalogue.catalogue() },
            progress = DataStoreDuaProgressStore(application),
            quranText = coreData.quranText,
            translations = coreData.translations,
            enabledTranslations = readDependencies.settingsStore.settings.map { it.translations },
            pageScript = readDependencies.settingsStore.settings.map { it.script },
            audioPlayer = audioPlayer,
            reciters = coreData.reciters,
            selectedReciterId = listenSettings.selectedReciterId,
            recitation = ControllerDuaRecitation(recite),
            playWord = { word -> learnWordSound.play(word) },
            myWords = readDependencies.myWords,
            reciteGate = { onCancel, content ->
                ReciteBarOrDownload(this, onExit = onCancel, bar = content)
            },
            reciterPicker = { onDismiss ->
                ReciterSheet(audio, page = null, onDismiss = onDismiss)
            },
            pronunciation = pronunciationState(),
            requestPronunciation = { coreData.packDownloads.request(PRONUNCIATION_PACK_ID) },
            loadTopics = { topicCatalogue.catalogue() },
            readSettings = readDependencies.settingsStore.settings,
            packs = coreData.packs,
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

    /** Whether the word pronunciation pack is on the phone or on its way, for Word by word. */
    private fun pronunciationState(): Flow<PronunciationState> =
        combine(coreData.packs.installed(), coreData.packDownloads.transfers) { installed, transfers
            ->
            val transfer = transfers[PRONUNCIATION_PACK_ID]
            when {
                installed.any { it.id == PRONUNCIATION_PACK_ID } -> PronunciationState.Installed
                transfer is PackTransfer.Waiting || transfer is PackTransfer.Running ->
                    PronunciationState.Downloading
                else -> PronunciationState.Missing
            }
        }

    private companion object {
        const val AUDIO_DIRECTORY = "audio"
        /** The word-by-word pronunciation in Latin letters, as `TranslationRepository` reads it. */
        const val PRONUNCIATION_PACK_ID = "en_transliteration_words"
        const val MODEL_DIRECTORY = "models"
    }
}
