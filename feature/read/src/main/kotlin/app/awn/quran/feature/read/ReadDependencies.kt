package app.awn.quran.feature.read

import app.awn.quran.core.data.BookmarkRepository
import app.awn.quran.core.data.MyWords
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.feature.read.quran.PrintOfferStore
import app.awn.quran.feature.read.settings.ReadSettingsStore
import app.awn.quran.player.AudioPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Everything the Read screens need from the rest of the app, handed over once by :app. */
data class ReadDependencies(
    val quranText: QuranTextRepository,
    val translations: TranslationRepository,
    val bookmarks: BookmarkRepository,
    val packs: PackRepository,
    val progressEvents: ProgressEvents,
    val audioPlayer: AudioPlayer,
    val settingsStore: ReadSettingsStore,
    /** Starts, cancels and watches the background downloads of the catalogue. */
    val downloads: PackDownloads,
    /**
     * The reciter list, used to find out whether the chosen reciter has word timings before a word
     * is played. Null means word taps only show the meaning.
     */
    val reciters: ReciterRepository? = null,
    /** The id of the reciter chosen in the audio settings, emitted again after each change. */
    val selectedReciterId: Flow<String> = flowOf(DEFAULT_RECITER_ID),
    /**
     * The reciter chosen for single words in Settings > Audio, or null before a choice; the word
     * card falls back to the listening reciter, then to any reciter with word timings.
     */
    val wordReciterId: Flow<String?> = flowOf(null),
    /** The one-time offer of a printed Mushaf on the first Quran screen. */
    val printOffer: PrintOfferStore = PrintOfferStore.Never,
    /**
     * True on mobile data or a hotspot. A reciter's word audio (about 3 MB) downloads by itself
     * only when this is false, the same rule as the audio downloads; otherwise the word card asks.
     */
    val isNetworkMetered: () -> Boolean = { false },
    /** The word list of the Learn course; null hides "Add to my words" on the word card. */
    val myWords: MyWords? = null,
) {
    companion object {
        /** The reciter tests and previews start with; the app passes the listen settings choice. */
        const val DEFAULT_RECITER_ID = "alafasy"
    }
}
