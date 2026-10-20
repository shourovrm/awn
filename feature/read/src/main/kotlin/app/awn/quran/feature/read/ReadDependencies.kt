package app.awn.quran.feature.read

import app.awn.quran.core.data.BookmarkRepository
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.TranslationRepository
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
    /**
     * The reciter list, used to find out whether the chosen reciter has word timings before a word
     * is played. Null means word taps only show the meaning.
     */
    val reciters: ReciterRepository? = null,
    /** The id of the reciter chosen in the audio settings, emitted again after each change. */
    val selectedReciterId: Flow<String> = flowOf(DEFAULT_RECITER_ID),
) {
    companion object {
        /** The reciter :feature:listen starts with. */
        const val DEFAULT_RECITER_ID = "alafasy"
    }
}
